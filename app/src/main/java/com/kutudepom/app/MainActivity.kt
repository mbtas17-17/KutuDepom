package com.kutudepom.app

import android.app.*
import android.os.Bundle
import android.content.*
import android.database.sqlite.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import java.io.File
import androidx.core.content.FileProvider
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity: androidx.activity.ComponentActivity() {
    private lateinit var db: Store
    private lateinit var root: LinearLayout
    private var selectedBox: Long?=null
    private var page="home"
    private var editingItem: Long?=null
    private var currentPhoto: String?=null
    private var originalPhoto: String?=null
    private var nameInput: EditText?=null
    private var qtyInput: EditText?=null
    private var noteInput: EditText?=null
    private var searchInput: EditText?=null
    private var photoLabel: TextView?=null
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
            out.putString("draftName",nameInput?.text?.toString()); out.putString("draftQty",qtyInput?.text?.toString())
            out.putString("draftNote",noteInput?.text?.toString()); out.putString("photo",currentPhoto)
        }
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() { db.close(); super.onDestroy() }
    private val scanner=registerForActivityResult(ScanContract()) { result ->
        val id=result.contents?.let { BoxQr.decode(it) }
        if(id!=null) showBox(id) else if(result.contents!=null) toast("Bu QR kodu KutuDepom kutusu değil")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db=Store(this)
        onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if(page=="home") finish() else home() }
        })
        val state=savedInstanceState
        when(state?.getString("page")) {
            "item" -> itemForm(state.getLong("box"),state.getLong("item").takeIf { it>0 },state)
            "box" -> showBox(state.getLong("box"))
            else -> home(state?.getString("query") ?: "")
        }
        File(filesDir,"photos").listFiles()?.filter { !db.photoInUse(it.name) && it.name!=currentPhoto }?.forEach { it.delete() }
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun screen(title:String) {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(16),dp(18),dp(24)); setBackgroundColor(Color.rgb(246,248,252)) }
        val scroll=ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { v,insets ->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(scroll)
        label(title,24)
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun rounded(color:Int, radius:Int=18)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun label(s:String, size:Int=17) {
        root.addView(TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(Color.rgb(30,38,55)); setPadding(dp(2),dp(12),0,dp(12)); if(size>=20) setTypeface(null,Typeface.BOLD) })
    }
    private fun button(s:String, click:()->Unit) {
        root.addView(Button(this).apply { text=s; isAllCaps=false; textSize=15f; setTextColor(Color.rgb(31,51,80)); background=rounded(Color.WHITE,18); elevation=dp(2).toFloat(); setPadding(dp(14),dp(10),dp(14),dp(10)); setOnClickListener { click() } }, LinearLayout.LayoutParams(-1,dp(58)).apply { bottomMargin=dp(10) })
    }
    private fun input(hint:String, value:String=""):EditText {
        val e=EditText(this).apply { this.hint=hint; setText(value); setSingleLine(true) }
        e.background=rounded(Color.WHITE,14); e.setPadding(dp(16),dp(12),dp(16),dp(12)); root.addView(e,LinearLayout.LayoutParams(-1,dp(56)).apply { bottomMargin=dp(12) })
        return e
    }
    private fun home(query:String="") {
        cleanDraftPhoto(); currentPhoto=null; page="home"; selectedBox=null
        screen("📦 KutuDepom")
        button("＋ Yeni kutu") { newBox() }
        button("Oda → dolap → raf → kutu") { browseLocations() }
        button("▣ QR kod tara") { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Kutunun QR kodunu okut").setBeepEnabled(false)) }
        val search=input("Ürün, not veya konum ara...",query); searchInput=search
        button("Ara") { searchItems(search.text.toString()) }
        label("Kutular",20)
        val boxes=db.boxes()
        if(boxes.isEmpty()) label("Henüz kutu yok. İlk kutunu oluştur.")
        boxes.forEach { (id,name)->button(name) { showBox(id) } }
    }
    private fun newBox(roomName:String="", cupboardName:String="", shelfName:String="") {
        page="newBox"
        screen("Yeni kutu")
        val name=input("Kutu adı (örn. Kutu 1)")
        val room=locationInput("Oda (örn. Proje odası)","room",roomName) { emptyMap() }
        val cupboard=locationInput("Dolap","cupboard",cupboardName) { mapOf("room" to room.text.toString()) }
        val shelf=locationInput("Raf","shelf",shelfName) { mapOf("room" to room.text.toString(),"cupboard" to cupboard.text.toString()) }
        button("Kaydet") {
            if(listOf(name,room,cupboard,shelf).any { it.text.isBlank() }) { toast("Oda, dolap, raf ve kutu adı gerekli"); return@button }
            val values=android.content.ContentValues().apply {
                put("name",name.text.toString().trim());put("room",room.text.toString().trim())
                put("cupboard",cupboard.text.toString().trim());put("shelf",shelf.text.toString().trim())
            }
            val id=db.writableDatabase.insert("boxes",null,values)
            showBox(id)
        }
        button("Geri") { home() }
    }
    private fun showBox(id:Long) {
        cleanDraftPhoto(); currentPhoto=null; page="box"; selectedBox=id
        val c=db.readableDatabase.rawQuery("SELECT name,room,cupboard,shelf FROM boxes WHERE id=?",arrayOf(id.toString()))
        if(!c.moveToFirst()) { c.close(); toast("Bu kutu cihazda bulunamadı"); home();return }
        val title=c.getString(0);val location="${c.getString(1)} / ${c.getString(2)} / ${c.getString(3)}";c.close()
        screen("📦 $title")
        label(location)
        button("QR etiketi göster / paylaş") { qrLabel(id,title,location) }
        button("Kutu konumunu düzenle") { editBox(id) }
        button("Boş kutuyu sil") { deleteBox(id) }
        button("＋ Ürün ekle") { itemForm(id,null) }
        db.readableDatabase.rawQuery("SELECT id,name,qty,note,photo FROM items WHERE box_id=? ORDER BY name",arrayOf(id.toString())).use { cur ->
            if(cur.count==0) label("Bu kutu boş.")
            while(cur.moveToNext()) {
                val itemId=cur.getLong(0);val name=cur.getString(1)
                cur.getString(4)?.let { photo -> showPhoto(photo) }
                button("📦  $name     ›") { itemForm(id,itemId) }
            }
        }
        button("Ana sayfa") { home() }
    }
    private fun itemForm(boxId:Long, itemId:Long?, state:Bundle?=null) {
        page="item"; selectedBox=boxId; editingItem=itemId
        currentPhoto=null; originalPhoto=null
        var oldName="";var oldQty=1;var oldNote=""
        if(itemId!=null) db.readableDatabase.rawQuery("SELECT name,qty,note,photo FROM items WHERE id=?",arrayOf(itemId.toString())).use { c ->
            if(c.moveToFirst()){oldName=c.getString(0);oldQty=c.getInt(1);oldNote=c.getString(2);originalPhoto=c.getString(3);currentPhoto=originalPhoto}
        }
        screen(if(itemId==null)"Ürün ekle" else "Ürünü düzenle")
        val name=input("Ürün adı",state?.getString("draftName") ?: oldName); nameInput=name
        qtyInput=null
        val note=input("Not / açıklama",state?.getString("draftNote") ?: oldNote); noteInput=note
        if(state!=null) currentPhoto=state.getString("photo")
        photoLabel=TextView(this).apply { text=if(currentPhoto==null) "Fotoğraf yok" else "Fotoğraf seçildi ✓" }; root.addView(photoLabel)
        currentPhoto?.let { showPhoto(it) }
        button("Galeriden fotoğraf seç") { photoPicker.launch(arrayOf("image/*")) }
        button("Fotoğrafı kaldır") { cleanDraftPhoto(); currentPhoto=null; photoLabel?.text="Fotoğraf yok" }
        button("Kaydet") {
            if(name.text.isBlank()){toast("Ürün adı gerekli");return@button}
            val count=oldQty
            val values=android.content.ContentValues().apply {
                put("photo",currentPhoto)
                put("name",name.text.toString().trim());put("qty",count)
                put("note",note.text.toString());put("box_id",boxId)
            }
            if(itemId==null) db.writableDatabase.insert("items",null,values)
            else db.writableDatabase.update("items",values,"id=?",arrayOf(itemId.toString()))
            originalPhoto?.takeIf { it!=currentPhoto && !db.photoInUse(it) }?.let { File(filesDir,"photos/$it").delete() }
            showBox(boxId)
        }
        if(itemId!=null) {
            button("Başka kutuya taşı") {
                val choices=db.boxes().filter { it.first!=boxId }
                if(choices.isEmpty()) { toast("Başka kutu oluşturmalısın");return@button }
                AlertDialog.Builder(this).setTitle("Hedef kutu seç")
                    .setItems(choices.map { it.second }.toTypedArray()) { _,which->
                        val values=android.content.ContentValues().apply { put("box_id",choices[which].first) }
                        db.writableDatabase.update("items",values,"id=?",arrayOf(itemId.toString()))
                        showBox(choices[which].first)
                    }.show()
            }
            button("Ürünü sil") {
                AlertDialog.Builder(this).setMessage("Ürün silinsin mi?")
                    .setPositiveButton("Sil") { _,_->db.writableDatabase.delete("items","id=?",arrayOf(itemId.toString()));originalPhoto?.let { if(!db.photoInUse(it)) File(filesDir,"photos/$it").delete() };showBox(boxId)}
                    .setNegativeButton("Vazgeç",null).show()
            }
        }
        button("Geri") { showBox(boxId) }
    }
    private fun searchItems(q:String) {
        page="home"
        screen("Arama sonuçları")
        button("Ana sayfa") { home(q) }
        if(q.isBlank()){label("Aranacak ürün adını yaz.");return}
        val key=q.trim().lowercase(java.util.Locale.forLanguageTag("tr"))
        var found=0
        db.readableDatabase.rawQuery("SELECT i.name,i.qty,b.id,b.name,b.room,b.cupboard,b.shelf,i.note FROM items i JOIN boxes b ON b.id=i.box_id ORDER BY i.name",null).use { c ->
            while(c.moveToNext()) {
                val text=listOf(c.getString(0),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7)).joinToString(" ")
                if(!text.lowercase(java.util.Locale.forLanguageTag("tr")).contains(key)) continue
                found++
                val id=c.getLong(2)
                button("${c.getString(0)}\n${c.getString(4)} / ${c.getString(5)} / ${c.getString(6)} / ${c.getString(3)}") { showBox(id) }
            }
        }
        if(found==0) label("Ürün bulunamadı.")
    }

    private fun locationInput(hint:String, column:String, value:String, parents:()->Map<String,String>):AutoCompleteTextView {
        val input=AutoCompleteTextView(this).apply { this.hint=hint; setText(value); threshold=1; setSingleLine(true) }
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
        root.addView(input); return input
    }
    private fun browseLocations(room:String?=null, cupboard:String?=null, shelf:String?=null) {
        page="home"
        screen(listOfNotNull(room,cupboard,shelf).joinToString(" → ").ifEmpty { "Odalar" })
        button("Ana sayfa") { home() }
        button("Bu konumda yeni kutu") { newBox(room ?: "",cupboard ?: "",shelf ?: "") }
        val filters=linkedMapOf<String,String>()
        room?.let { filters["room"]=it }; cupboard?.let { filters["cupboard"]=it }; shelf?.let { filters["shelf"]=it }
        val where=if(filters.isEmpty()) "" else " WHERE "+filters.keys.joinToString(" AND ") { "$it=?" }
        val column=when { room==null -> "room"; cupboard==null -> "cupboard"; shelf==null -> "shelf"; else -> "name" }
        db.readableDatabase.rawQuery("SELECT DISTINCT $column"+(if(shelf!=null) ",id" else "")+" FROM boxes$where ORDER BY $column",filters.values.toTypedArray()).use { c ->
            while(c.moveToNext()) {
                val name=c.getString(0)
                val id=if(shelf!=null) c.getLong(1) else 0
                button(name.ifBlank { "Belirtilmemiş" }) {
                    when { room==null -> browseLocations(name); cupboard==null -> browseLocations(room,name); shelf==null -> browseLocations(room,cupboard,name); else -> showBox(id) }
                }
            }
            if(c.count==0) label("Henüz konum yok. Yeni kutu ekleyerek başlayın.")
        }
    }
    private fun editBox(id:Long) {
        val values=db.readableDatabase.rawQuery("SELECT name,room,cupboard,shelf FROM boxes WHERE id=?",arrayOf(id.toString())).use { c ->
            if(!c.moveToFirst()) return
            List(4) { c.getString(it) }
        }
        page="box"; screen("Kutu konumunu düzenle")
        val fields=listOf("Kutu","Oda","Dolap","Raf").mapIndexed { index,hint -> input(hint,values[index]) }
        button("Kaydet") {
            if(fields.any { it.text.isBlank() }) { toast("Tüm konum alanları gerekli"); return@button }
            val v=ContentValues().apply { listOf("name","room","cupboard","shelf").forEachIndexed { index,key -> put(key,fields[index].text.toString().trim()) } }
            db.writableDatabase.update("boxes",v,"id=?",arrayOf(id.toString())); showBox(id)
        }
        button("Vazgeç") { showBox(id) }
    }
    private fun deleteBox(id:Long) {
        AlertDialog.Builder(this).setTitle("Boş kutu silinsin mi?").setMessage("Ürün içeren kutular silinemez.")
            .setNegativeButton("Vazgeç",null).setPositiveButton("Sil") { _,_ ->
                try { db.writableDatabase.delete("boxes","id=?",arrayOf(id.toString())); home() }
                catch(ex:Exception) { toast("Önce kutudaki ürünleri taşıyın veya silin") }
            }.show()
    }
    private fun showPhoto(name:String) {
        val file=File(filesDir,"photos/$name")
        val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath,bounds)
        var sample=1
        while(bounds.outWidth/sample>800 || bounds.outHeight/sample>800) sample*=2
        root.addView(ImageView(this).apply {
            contentDescription="Ürün fotoğrafı"; scaleType=ImageView.ScaleType.CENTER_INSIDE
            layoutParams=LinearLayout.LayoutParams(-1,(160*resources.displayMetrics.density).toInt())
            setImageBitmap(android.graphics.BitmapFactory.decodeFile(file.absolutePath,android.graphics.BitmapFactory.Options().apply { inSampleSize=sample }))
        })
    }
    private fun qrLabel(id:Long,title:String,location:String) {
        val matrix=com.google.zxing.MultiFormatWriter().encode(BoxQr.encode(id),com.google.zxing.BarcodeFormat.QR_CODE,640,640)
        val bitmap=android.graphics.Bitmap.createBitmap(640,640,android.graphics.Bitmap.Config.ARGB_8888)
        val pixels=IntArray(640*640) { i -> if(matrix[i%640,i/640]) Color.BLACK else Color.WHITE }
        bitmap.setPixels(pixels,0,640,0,0,640,640)
        val image=ImageView(this).apply { setImageBitmap(bitmap); contentDescription="Kutu QR etiketi" }
        AlertDialog.Builder(this).setTitle(title).setView(image).setNegativeButton("Kapat",null).setPositiveButton("Paylaş") { _,_ ->
            val dir=File(cacheDir,"labels").apply { mkdirs() }; val file=File(dir,"kutu-$id.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            val uri=FileProvider.getUriForFile(this,"$packageName.files",file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="image/png"; putExtra(Intent.EXTRA_STREAM,uri); putExtra(Intent.EXTRA_TEXT,"$location / $title"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },"QR etiketini paylaş"))
        }.show()
    }
}

