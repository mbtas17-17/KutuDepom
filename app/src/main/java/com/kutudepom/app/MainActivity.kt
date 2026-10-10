package com.kutudepom.app

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import java.io.File
import androidx.core.content.FileProvider
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity: AppCompatActivity() {
    private lateinit var db: Store
    private lateinit var root: LinearLayout
    private var selectedBox: Long?=null
    private var page="home"
    private var editingItem: Long?=null
    private var currentPhoto: String?=null
    private var originalPhoto: String?=null
    private var nameInput: EditText?=null
    private var noteInput: EditText?=null
    private var searchInput: EditText?=null
    private var photoLabel: TextView?=null
    private lateinit var manualStorage:ManualStorage
    private lateinit var manualList:LinearLayout
    private var pendingManuals=mutableListOf<Manual>()
    private var removedManuals=mutableSetOf<Long>()
    private var editingSession=java.util.UUID.randomUUID().toString()
    private var importingManuals=false
    private var manualButton:MaterialButton?=null
    private var saveButton:MaterialButton?=null
    private var boxInputs:List<EditText> = emptyList()
    private val documentPicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty() && page=="item") {
            val session=editingSession
            importingManuals=true; manualButton?.isEnabled=false; saveButton?.isEnabled=false
            lifecycleScope.launch {
                val imported=mutableListOf<Manual>(); val errors=mutableListOf<String>(); var attached=false
                try {
                    withContext(Dispatchers.IO) {
                        uris.forEach { uri ->
                            try { imported.add(manualStorage.importUri(uri)) }
                            catch(ex:Exception) { errors.add(ex.message ?: "Belge kopyalanamadı.") }
                        }
                    }
                    if(page=="item" && editingSession==session) {
                        pendingManuals.addAll(imported); attached=true; renderManuals()
                        if(errors.isNotEmpty()) toast(errors.joinToString("\n"))
                    }
                } finally {
                    if(!attached) imported.forEach { manualStorage.remove(it) }
                    importingManuals=false; manualButton?.isEnabled=true; saveButton?.isEnabled=true
                }
            }
        }
    }
    private val photoPicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null && page=="item") {
            var file: File?=null
            try {
                val folder=File(filesDir,"photos").apply { mkdirs() }
                val target=File(folder,"${java.util.UUID.randomUUID()}.img"); file=target
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input)
                    target.outputStream().use { output ->
                        val buffer=ByteArray(8192); var total=0
                        while(true) {
                            val n=input.read(buffer); if(n<0) break
                            total+=n; require(total<=15*1024*1024) { "Fotoğraf en fazla 15 MB olabilir" }
                            output.write(buffer,0,n)
                        }
                    }
                }
                val opts=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                android.graphics.BitmapFactory.decodeFile(target.absolutePath,opts)
                require(opts.outWidth>0 && opts.outHeight>0) { "Fotoğraf okunamadı" }
                cleanDraftPhoto(); currentPhoto=target.name; photoLabel?.text="Fotoğraf seçildi ✓"
            } catch(ex:Exception) { file?.delete(); toast(ex.message ?: "Fotoğraf alınamadı") }
        }
    }
    private fun cleanDraftPhoto() {
        currentPhoto?.let { if(it!=originalPhoto && !db.photoInUse(it)) File(filesDir,"photos/$it").delete() }
    }
    override fun onSaveInstanceState(out:Bundle) {
        out.putString("page",page); out.putLong("box",selectedBox ?: 0); out.putLong("item",editingItem ?: 0)
        out.putString("query",searchInput?.text?.toString())
        if(page=="item") {
            out.putString("draftName",nameInput?.text?.toString())
            out.putString("draftNote",noteInput?.text?.toString()); out.putString("photo",currentPhoto)
            out.putParcelableArrayList("manuals",ArrayList(pendingManuals.map { m -> Bundle().apply {
                putString("name",m.name); putString("stored",m.storedName); putString("mime",m.mime); putLong("size",m.size)
            } }))
            out.putLongArray("removedManuals",removedManuals.toLongArray())
        }
        if(page=="newBox" || page=="editBox") out.putStringArrayList("boxDraft",ArrayList(boxInputs.map { it.text.toString() }))
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() { db.close(); super.onDestroy() }
    private val scanner=registerForActivityResult(ScanContract()) { result ->
        val id=result.contents?.let { BoxQr.decode(it) }
        if(id!=null) showBox(id) else if(result.contents!=null) toast("Bu QR kodu KutuDepom kutusu değil")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
        db=Store(this)
        manualStorage=ManualStorage(this)
        onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if(page=="home") finish() else if(page=="item") showBox(selectedBox ?: 0) else home() }
        })
        val state=savedInstanceState
        when(state?.getString("page")) {
            "item" -> itemForm(state.getLong("box"),state.getLong("item").takeIf { it>0 },state)
            "box" -> showBox(state.getLong("box"))
            "newBox" -> newBox(state=state)
            "editBox" -> editBox(state.getLong("box"),state)
            "search" -> searchItems(state.getString("query") ?: "")
            else -> home(state?.getString("query") ?: "")
        }
        if(savedInstanceState==null) manualStorage.cleanOrphans(db.manualFiles())
        File(filesDir,"photos").listFiles()?.filter { !db.photoInUse(it.name) && it.name!=currentPhoto }?.forEach { it.delete() }
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun color(attr:Int)=MaterialColors.getColor(this,attr,"KutuDepom")
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun space(parent:LinearLayout=root) { parent.addView(Space(this),LinearLayout.LayoutParams(1,dp(8))) }
    private fun install(content:View) {
        setContentView(content)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content) { v,insets ->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            v.post { v.findFocus()?.let { focus -> focus.requestRectangleOnScreen(android.graphics.Rect(0,0,focus.width,focus.height),false) } }
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(content)
    }
    private fun screen(title:String) {
        root=column().apply { setPadding(dp(12),dp(12),dp(12),dp(16)) }
        val scroll=ScrollView(this).apply { id=R.id.form_scroll; addView(root); isFillViewport=true }
        // Insets belong to the outer frame so ScrollView sees the actual available height.
        val frame=FrameLayout(this).apply { addView(scroll,FrameLayout.LayoutParams(-1,-1)) }
        install(frame)
        scroll.viewTreeObserver.addOnGlobalLayoutListener {
            val focused=root.findFocus()
            if(focused is EditText) {
                val bounds=android.graphics.Rect(); focused.getDrawingRect(bounds)
                root.offsetDescendantRectToMyCoords(focused,bounds)
                scroll.requestChildRectangleOnScreen(root,bounds,true)
            }
        }
        label(title,20)
    }
    private fun listScreen(title:String):LinearLayout {
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(window.decorView.windowToken,0)
        val shell=column().apply { setBackgroundColor(color(com.google.android.material.R.attr.colorSurface)) }
        root=column().apply { id=R.id.fixed_header; setPadding(dp(12),dp(8),dp(12),0) }
        shell.addView(root,LinearLayout.LayoutParams(-1,-2))
        install(shell)
        root.addView(text(title,20).apply { maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END })
        return shell
    }
    private data class Row(val id:Long,val name:String,val description:String,val open:()->Unit)
    private fun list(shell:LinearLayout,rows:List<Row>,empty:String) {
        if(rows.isEmpty()) root.addView(text(empty))
        val recycler=RecyclerView(this).apply {
            id=R.id.inventory_list; layoutManager=LinearLayoutManager(this@MainActivity)
            setPadding(dp(12),dp(4),dp(12),dp(8)); clipToPadding=false
            adapter=object:RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                init { setHasStableIds(true) }
                override fun getItemId(position:Int)=rows[position].id
                override fun getItemCount()=rows.size
                override fun onCreateViewHolder(parent:ViewGroup,type:Int):RecyclerView.ViewHolder {
                    val card=MaterialCardView(this@MainActivity).apply {
                        radius=dp(12).toFloat(); cardElevation=0f; isClickable=true; isFocusable=true
                        setContentPadding(dp(12),dp(10),dp(12),dp(10)); minimumHeight=dp(52)
                        layoutParams=RecyclerView.LayoutParams(-1,-2).apply { bottomMargin=dp(8) }
                    }
                    card.addView(text("",16).apply { maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END })
                    return object:RecyclerView.ViewHolder(card) {}
                }
                override fun onBindViewHolder(holder:RecyclerView.ViewHolder,position:Int) {
                    val row=rows[position]; val card=holder.itemView as MaterialCardView
                    (card.getChildAt(0) as TextView).text=row.name
                    card.contentDescription=row.description; card.setOnClickListener { row.open() }
                }
            }
        }
        shell.addView(recycler,LinearLayout.LayoutParams(-1,0,1f))
    }
    private fun actionRow():LinearLayout=LinearLayout(this).apply {
        orientation=LinearLayout.HORIZONTAL; isBaselineAligned=false; gravity=Gravity.CENTER_VERTICAL
        root.addView(this,LinearLayout.LayoutParams(-1,-2))
    }
    private fun rowButton(s:String,row:LinearLayout,click:()->Unit)=button(s,row,secondary=true,click=click).apply {
        layoutParams=LinearLayout.LayoutParams(0,dp(56),1f).apply { marginEnd=dp(4) }
        setPadding(dp(4),0,dp(4),0); maxLines=2
    }
    private fun text(s:String,size:Int=14)=MaterialTextView(this).apply {
        text=s
        setTextAppearance(when {
            size>=24 -> com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall
            size>=20 -> com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
            size>=16 -> com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
            else -> com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
        })
        setTextColor(color(com.google.android.material.R.attr.colorOnSurface))
        setPadding(0,dp(4),0,dp(4))
    }
    private fun label(s:String,size:Int=14) { root.addView(text(s,size)) }
    private fun button(s:String,parent:LinearLayout=root,secondary:Boolean=false,danger:Boolean=false,click:()->Unit):MaterialButton {
        val button=MaterialButton(this).apply {
            text=s; isAllCaps=false; minHeight=dp(48); cornerRadius=dp(12); setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            if(secondary) {
                backgroundTintList=ColorStateList.valueOf(color(com.google.android.material.R.attr.colorSurface))
                strokeWidth=dp(1); strokeColor=ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOutline))
                setTextColor(color(if(danger) com.google.android.material.R.attr.colorError else androidx.appcompat.R.attr.colorPrimary))
            } else if(danger) {
                backgroundTintList=ColorStateList.valueOf(color(com.google.android.material.R.attr.colorError))
                setTextColor(color(com.google.android.material.R.attr.colorOnError))
            }
            setOnClickListener { click() }
        }
        parent.addView(button,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(4) })
        return button
    }
    private fun input(hint:String,value:String="",parent:LinearLayout=root,multiline:Boolean=false):EditText {
        val container=TextInputLayout(this).apply { boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE; this.hint=hint; setBoxCornerRadii(dp(12).toFloat(),dp(12).toFloat(),dp(12).toFloat(),dp(12).toFloat()) }
        val edit=TextInputEditText(container.context).apply {
            this.hint=hint; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge); setText(value); setSingleLine(!multiline)
            if(multiline) { minLines=3; maxLines=6; gravity=Gravity.TOP }
        }
        container.addView(edit,LinearLayout.LayoutParams(-1,-2))
        parent.addView(container,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        return edit
    }
    private fun section(title:String,subtitle:String?=null):LinearLayout {
        space()
        val card=MaterialCardView(this).apply {
            radius=dp(12).toFloat(); cardElevation=0f
            setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceVariant))
        }
        val content=column().apply { setPadding(dp(16),dp(12),dp(16),dp(12)) }
        content.addView(text(title,16)); subtitle?.let { content.addView(text(it)) }
        card.addView(content); root.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        return content
    }
    private fun searchControls(query:String) {
        val search=input("Ürün, not veya konum ara...",query); searchInput=search
        val action=button("Ara") { searchItems(search.text.toString()) }
        fun update() { action.isEnabled=search.text.isNotBlank() }
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { update() }
            override fun afterTextChanged(s:Editable?) {}
        })
        update()
    }
    private fun cleanDraft() {
        cleanDraftPhoto(); pendingManuals.forEach { manualStorage.remove(it) }
        pendingManuals.clear(); removedManuals.clear(); editingSession=java.util.UUID.randomUUID().toString()
    }
    private fun home(query:String="") {
        cleanDraft(); currentPhoto=null; page="home"; selectedBox=null
        val shell=listScreen("KutuDepom")
        val actions=actionRow()
        rowButton("＋ Yeni kutu",actions) { newBox() }
        rowButton("▣ QR kod tara",actions) { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Kutunun QR kodunu okut").setBeepEnabled(false)) }
        searchControls(query)
        label("Kutular",16)
        list(shell,db.boxes().map { (id,name) -> Row(id,name,"$name, kutu içeriğini aç") { showBox(id) } },"Henüz kutu yok. İlk kutunu oluştur.")
    }
    private fun newBox(roomName:String="", cupboardName:String="", shelfName:String="",state:Bundle?=null) {
        page="newBox"
        screen("Yeni kutu")
        val draft=state?.getStringArrayList("boxDraft")
        label("Oda, dolap ve raf isteğe bağlıdır.")
        val name=input("Kutu adı (örn. Kutu 1)",draft?.getOrNull(0) ?: "")
        val room=locationInput("Oda (örn. Proje odası)","room",draft?.getOrNull(1) ?: roomName) { emptyMap() }
        val cupboard=locationInput("Dolap","cupboard",draft?.getOrNull(2) ?: cupboardName) { mapOf("room" to room.text.toString()) }
        val shelf=locationInput("Raf","shelf",draft?.getOrNull(3) ?: shelfName) { mapOf("room" to room.text.toString(),"cupboard" to cupboard.text.toString()) }
        boxInputs=listOf(name,room,cupboard,shelf)
        button("Kaydet") {
            if(name.text.isBlank()) { name.error="Kutu adı gerekli"; return@button }
            val values=android.content.ContentValues().apply {
                put("name",name.text.toString().trim());put("room",room.text.toString().trim())
                put("cupboard",cupboard.text.toString().trim());put("shelf",shelf.text.toString().trim())
            }
            val id=db.writableDatabase.insertOrThrow("boxes",null,values)
            showBox(id)
        }
        button("Geri") { home() }
    }
    private fun showBox(id:Long) {
        cleanDraft(); currentPhoto=null; page="box"; selectedBox=id
        val box=db.readableDatabase.rawQuery("SELECT name,room,cupboard,shelf FROM boxes WHERE id=?",arrayOf(id.toString())).use { c ->
            if(!c.moveToFirst()) null else List(4) { c.getString(it) }
        }
        if(box==null) { toast("Bu kutu cihazda bulunamadı"); home(); return }
        val title=box[0]; val location=box.drop(1).filter { it.isNotBlank() }.joinToString(" → ")
        val shell=listScreen(title)
        if(location.isNotBlank()) root.addView(text(location).apply { maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END })
        val actions=actionRow()
        rowButton("Ürün Ekle",actions) { itemForm(id,null) }
        rowButton("QR Etiketi",actions) { qrLabel(id,title,location) }
        rowButton("Kutu İşlemleri",actions) {
            MaterialAlertDialogBuilder(this).setTitle("Kutu İşlemleri")
                .setItems(arrayOf("Kutu konumunu düzenle","Boş kutuyu sil")) { _,which -> if(which==0) editBox(id) else deleteBox(id) }.show()
        }
        button("Ana sayfa",secondary=true) { home() }
        label("Ürünler",16)
        val rows=db.readableDatabase.rawQuery("SELECT id,name FROM items WHERE box_id=? ORDER BY name",arrayOf(id.toString())).use { c ->
            buildList { while(c.moveToNext()) {
                val itemId=c.getLong(0); val name=c.getString(1)
                add(Row(itemId,name,"$name, ürün detayını aç") { itemForm(id,itemId) })
            } }
        }
        list(shell,rows,"Bu kutu boş. Ürün Ekle ile başlayın.")
    }
    private fun itemForm(boxId:Long, itemId:Long?, state:Bundle?=null) {
        cleanDraft(); page="item"; selectedBox=boxId; editingItem=itemId
        currentPhoto=null; originalPhoto=null
        state?.getParcelableArrayList<Bundle>("manuals")?.forEach { m -> pendingManuals.add(Manual(name=m.getString("name") ?: "Belge",storedName=m.getString("stored") ?: "",mime=m.getString("mime") ?: "application/octet-stream",size=m.getLong("size"))) }
        removedManuals.addAll(state?.getLongArray("removedManuals")?.toSet() ?: emptySet())
        var oldName="";var oldQty=1;var oldNote=""
        if(itemId!=null) db.readableDatabase.rawQuery("SELECT name,qty,note,photo FROM items WHERE id=?",arrayOf(itemId.toString())).use { c ->
            if(c.moveToFirst()){oldName=c.getString(0);oldQty=c.getInt(1);oldNote=c.getString(2);originalPhoto=c.getString(3);currentPhoto=originalPhoto}
        }
        screen(if(itemId==null)"Ürün ekle" else "Ürünü düzenle")
        val name=input("Ürün adı",state?.getString("draftName") ?: oldName); nameInput=name
        val note=input("Not / açıklama",state?.getString("draftNote") ?: oldNote,multiline=true); noteInput=note
        if(state!=null) currentPhoto=state.getString("photo")
        photoLabel=text(if(currentPhoto==null) "Fotoğraf yok" else "Fotoğraf seçildi ✓"); root.addView(photoLabel)
        currentPhoto?.let { showPhoto(it) }
        button("Galeriden fotoğraf seç") { photoPicker.launch(arrayOf("image/*")) }
        button("Fotoğrafı kaldır") { cleanDraftPhoto(); currentPhoto=null; photoLabel?.text="Fotoğraf yok" }
        manualSection()
        saveButton=button("Kaydet") {
            if(importingManuals) { toast("Belge kopyalama tamamlanana kadar bekleyin."); return@button }
            if(name.text.isBlank()){name.error="Ürün adı gerekli";return@button}
            val count=oldQty
            val values=android.content.ContentValues().apply {
                put("photo",currentPhoto)
                put("name",name.text.toString().trim());put("qty",count)
                put("note",note.text.toString());put("box_id",boxId)
            }
            val removedFiles=itemId?.let { db.manuals(it).filter { m -> m.id in removedManuals } } ?: emptyList()
            try { db.saveItem(itemId,values,pendingManuals,removedManuals) }
            catch(ex:Exception) { android.util.Log.e("KutuDepom","Ürün kaydedilemedi",ex); toast("Ürün kaydedilemedi. Lütfen tekrar deneyin."); return@button }
            pendingManuals.clear(); removedManuals.clear(); removedFiles.forEach { manualStorage.remove(it) }
            originalPhoto?.takeIf { it!=currentPhoto && !db.photoInUse(it) }?.let { File(filesDir,"photos/$it").delete() }
            showBox(boxId)
        }
        if(itemId!=null) {
            button("Başka kutuya taşı") {
                val choices=db.boxes().filter { it.first!=boxId }
                if(choices.isEmpty()) { toast("Başka kutu oluşturmalısın");return@button }
                MaterialAlertDialogBuilder(this).setTitle("Hedef kutu seç")
                    .setItems(choices.map { it.second }.toTypedArray()) { _,which->
                        val values=android.content.ContentValues().apply { put("box_id",choices[which].first) }
                        db.writableDatabase.update("items",values,"id=?",arrayOf(itemId.toString()))
                        showBox(choices[which].first)
                    }.show()
            }
            button("Ürünü sil",secondary=true,danger=true) {
                MaterialAlertDialogBuilder(this).setMessage("Ürün silinsin mi?")
                    .setPositiveButton("Sil") { _,_->deleteProduct(itemId); showBox(boxId)}
                    .setNegativeButton("Vazgeç",null).show()
            }
        }
        button("Geri") { showBox(boxId) }
    }
    private fun searchItems(q:String) {
        page="search"
        val shell=listScreen("Arama sonuçları")
        searchControls(q)
        button("Ana sayfa",secondary=true) { home(searchInput?.text?.toString() ?: q) }
        if(q.isBlank()){list(shell,emptyList(),"Aranacak ürün adını yaz.");return}
        val key=q.trim().lowercase(java.util.Locale.forLanguageTag("tr"))
        val rows=mutableListOf<Row>()
        db.readableDatabase.rawQuery("SELECT i.name,i.id,b.id,b.name,b.room,b.cupboard,b.shelf,i.note,i.photo FROM items i JOIN boxes b ON b.id=i.box_id ORDER BY i.name",null).use { c ->
            while(c.moveToNext()) {
                val text=listOf(c.getString(0),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7)).joinToString(" ")
                if(!text.lowercase(java.util.Locale.forLanguageTag("tr")).contains(key)) continue
                val id=c.getLong(2); val itemId=c.getLong(1)
                val name=c.getString(0)
                rows.add(Row(itemId,name,"$name, ürün detayını aç") { itemForm(id,itemId) })
            }
        }
        list(shell,rows,"Ürün bulunamadı.")
    }

    private fun locationInput(hint:String, column:String, value:String, parents:()->Map<String,String>):AutoCompleteTextView {
        val input=com.google.android.material.textfield.MaterialAutoCompleteTextView(this).apply { this.hint=hint; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge); setText(value); threshold=1; setSingleLine(true) }
        fun refresh() {
            val filters=parents()
            val where=if(filters.isEmpty()) "" else " WHERE "+filters.keys.joinToString(" AND ") { "$it=?" }
            val names=db.readableDatabase.rawQuery("SELECT DISTINCT $column FROM boxes$where ORDER BY $column",filters.values.toTypedArray()).use { c ->
                buildList { while(c.moveToNext()) add(c.getString(0)) }
            }
            input.setAdapter(ArrayAdapter(this,android.R.layout.simple_dropdown_item_1line,names))
        }
        input.setOnFocusChangeListener { _,hasFocus -> if(hasFocus) refresh() }
        input.setOnClickListener { refresh(); input.showDropDown() }
        val container=TextInputLayout(this).apply { boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE; this.hint=hint; setBoxCornerRadii(dp(12).toFloat(),dp(12).toFloat(),dp(12).toFloat(),dp(12).toFloat()) }
        container.addView(input,LinearLayout.LayoutParams(-1,-2))
        root.addView(container,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) }); return input
    }
    private fun editBox(id:Long,state:Bundle?=null) {
        val values=db.readableDatabase.rawQuery("SELECT name,room,cupboard,shelf FROM boxes WHERE id=?",arrayOf(id.toString())).use { c ->
            if(!c.moveToFirst()) return
            List(4) { c.getString(it) }
        }
        page="editBox"; selectedBox=id; screen("Kutu konumunu düzenle")
        label("Yalnızca kutu adı zorunludur.")
        val draft=state?.getStringArrayList("boxDraft")
        val fields=listOf("Kutu","Oda","Dolap","Raf").mapIndexed { index,hint -> input(hint,draft?.getOrNull(index) ?: values[index]) }
        boxInputs=fields
        button("Kaydet") {
            if(fields.first().text.isBlank()) { fields.first().error="Kutu adı gerekli"; return@button }
            val v=ContentValues().apply { listOf("name","room","cupboard","shelf").forEachIndexed { index,key -> put(key,fields[index].text.toString().trim()) } }
            db.writableDatabase.update("boxes",v,"id=?",arrayOf(id.toString())); showBox(id)
        }
        button("Vazgeç") { showBox(id) }
    }
    private fun deleteBox(id:Long) {
        MaterialAlertDialogBuilder(this).setTitle("Boş kutu silinsin mi?").setMessage("Ürün içeren kutular silinemez.")
            .setNegativeButton("Vazgeç",null).setPositiveButton("Sil") { _,_ ->
                try { db.writableDatabase.delete("boxes","id=?",arrayOf(id.toString())); home() }
                catch(ex:Exception) { toast("Önce kutudaki ürünleri taşıyın veya silin") }
            }.show()
    }
    private fun photoView(name:String,height:Int=160):ImageView {
        val file=File(filesDir,"photos/$name")
        val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath,bounds)
        var sample=1
        while(bounds.outWidth/sample>800 || bounds.outHeight/sample>800) sample*=2
        return ImageView(this).apply {
            contentDescription="Ürün fotoğrafı"; scaleType=ImageView.ScaleType.CENTER_INSIDE
            layoutParams=LinearLayout.LayoutParams(-1,dp(height))
            setImageBitmap(android.graphics.BitmapFactory.decodeFile(file.absolutePath,android.graphics.BitmapFactory.Options().apply { inSampleSize=sample }))
        }
    }
    private fun showPhoto(name:String) { root.addView(photoView(name)) }
    private fun manualSection() {
        val section=section("Kullanım Kılavuzu","PDF, Office, OpenDocument ve metin belgeleri ekleyebilirsiniz. Belge değişiklikleri Kaydet ile uygulanır.")
        manualList=column(); section.addView(manualList)
        manualButton=button("Belge ekle",section,secondary=true) { documentPicker.launch(ManualStorage.pickerTypes) }
        manualButton?.isEnabled=!importingManuals
        renderManuals()
    }
    private fun renderManuals() {
        manualList.removeAllViews()
        val saved=editingItem?.let { db.manuals(it) } ?: emptyList()
        val all=saved.filter { it.id !in removedManuals }+pendingManuals
        if(all.isEmpty()) manualList.addView(text("Henüz belge eklenmedi."))
        all.forEach { manual ->
            val card=MaterialCardView(this).apply { radius=dp(12).toFloat(); cardElevation=0f }
            val body=column().apply { setPadding(dp(12),dp(8),dp(12),dp(8)) }
            body.addView(text(manual.name))
            body.addView(text(android.text.format.Formatter.formatShortFileSize(this,manual.size)))
            button("Aç",body,secondary=true) {
                try { startActivity(manualStorage.openIntent(manual)) }
                catch(ex:ActivityNotFoundException) { toast("Bu belge türünü açabilen bir uygulama yükleyin.") }
                catch(ex:Exception) { toast(ex.message ?: "Belge açılamadı.") }
            }
            button("Kaldır",body,secondary=true,danger=true) {
                MaterialAlertDialogBuilder(this).setTitle("Belge kaldırılsın mı?").setMessage(manual.name)
                    .setNegativeButton("Vazgeç",null).setPositiveButton("Kaldır") { _,_ ->
                        if(manual.id==0L) { pendingManuals.remove(manual); manualStorage.remove(manual) }
                        else removedManuals.add(manual.id)
                        renderManuals()
                    }.show()
            }
            card.addView(body); manualList.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        }
    }
    private fun deleteProduct(id:Long) {
        val documents=db.manuals(id)
        val photo=db.readableDatabase.rawQuery("SELECT photo FROM items WHERE id=?",arrayOf(id.toString())).use { c ->
            if(c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
        db.writableDatabase.delete("items","id=?",arrayOf(id.toString()))
        documents.forEach { manualStorage.remove(it) }
        photo?.let { if(!db.photoInUse(it)) File(filesDir,"photos/$it").delete() }
    }
    private fun qrLabel(id:Long,title:String,location:String) {
        val matrix=com.google.zxing.MultiFormatWriter().encode(BoxQr.encode(id),com.google.zxing.BarcodeFormat.QR_CODE,640,640)
        val bitmap=android.graphics.Bitmap.createBitmap(640,640,android.graphics.Bitmap.Config.ARGB_8888)
        val pixels=IntArray(640*640) { i -> if(matrix[i%640,i/640]) Color.BLACK else Color.WHITE }
        bitmap.setPixels(pixels,0,640,0,0,640,640)
        val image=ImageView(this).apply { setImageBitmap(bitmap); contentDescription="Kutu QR etiketi" }
        MaterialAlertDialogBuilder(this).setTitle(title).setView(image).setNegativeButton("Kapat",null).setPositiveButton("Paylaş") { _,_ ->
            val dir=File(cacheDir,"labels").apply { mkdirs() }; val file=File(dir,"kutu-$id.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            val uri=FileProvider.getUriForFile(this,"$packageName.files",file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="image/png"; putExtra(Intent.EXTRA_STREAM,uri); putExtra(Intent.EXTRA_TEXT,"$location / $title"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },"QR etiketini paylaş"))
        }.show()
    }
}

