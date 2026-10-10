package com.kutudepom.app

import android.app.*
import android.os.Bundle
import android.content.*
import android.database.sqlite.*
import android.graphics.Color
import android.view.*
import android.widget.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class Store(ctx: Context): SQLiteOpenHelper(ctx, "kutudepom.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE boxes(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, room TEXT NOT NULL, cupboard TEXT NOT NULL, shelf TEXT NOT NULL)")
        db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, qty INTEGER NOT NULL DEFAULT 1, box_id INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun boxes(): List<Pair<Long,String>> {
        val out=mutableListOf<Pair<Long,String>>()
        readableDatabase.rawQuery("SELECT id,name,room,cupboard,shelf FROM boxes ORDER BY room,cupboard,shelf,name",null).use { c ->
            while(c.moveToNext()) out.add(c.getLong(0) to "${c.getString(1)} • ${c.getString(2)} / ${c.getString(3)} / ${c.getString(4)}")
        }
        return out
    }
}
class MainActivity: Activity() {
    private lateinit var db: Store
    private lateinit var root: LinearLayout
    private var selectedBox: Long?=null
    private val scanner=registerForActivityResult(ScanContract()) { result ->
        val id=result.contents?.removePrefix("KUTUDEPOM:")?.toLongOrNull()
        if(id!=null) showBox(id) else if(result.contents!=null) toast("Bu QR kodu KutuDepom kutusu değil")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db=Store(this)
        home()
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun screen(title:String) {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,20,24,16); setBackgroundColor(Color.rgb(248,249,252)) }
        val scroll=ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        label(title,24)
    }
    private fun label(s:String, size:Int=17) {
        root.addView(TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(Color.rgb(30,38,55)); setPadding(0,12,0,12) })
    }
    private fun button(s:String, click:()->Unit) {
        root.addView(Button(this).apply { text=s; setOnClickListener { click() } })
    }
    private fun input(hint:String, value:String=""):EditText {
        val e=EditText(this).apply { this.hint=hint; setText(value); setSingleLine(true) }
        root.addView(e)
        return e
    }
    private fun home(query:String="") {
        selectedBox=null
        screen("📦 KutuDepom")
        button("＋ Yeni kutu") { newBox() }
        button("▣ QR kod tara") { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Kutunun QR kodunu okut").setBeepEnabled(false)) }
        val search=input("Ürün ara...",query)
        button("Ara") { searchItems(search.text.toString()) }
        label("Kutular",20)
        val boxes=db.boxes()
        if(boxes.isEmpty()) label("Henüz kutu yok. İlk kutunu oluştur.")
        boxes.forEach { (id,name)->button(name) { showBox(id) } }
    }
    private fun newBox() {
        screen("Yeni kutu")
        val name=input("Kutu adı (örn. Kutu 1)")
        val room=input("Oda (örn. Proje odası)")
        val cupboard=input("Dolap")
        val shelf=input("Raf")
        button("Kaydet") {
            if(name.text.isBlank() || room.text.isBlank()) { toast("Kutu adı ve oda gerekli"); return@button }
            val values=android.content.ContentValues().apply {
                put("name",name.text.toString());put("room",room.text.toString())
                put("cupboard",cupboard.text.toString());put("shelf",shelf.text.toString())
            }
            val id=db.writableDatabase.insert("boxes",null,values)
            showBox(id)
        }
        button("Geri") { home() }
    }
    private fun showBox(id:Long) {
        selectedBox=id
        val c=db.readableDatabase.rawQuery("SELECT name,room,cupboard,shelf FROM boxes WHERE id=?",arrayOf(id.toString()))
        if(!c.moveToFirst()) { c.close(); home();return }
        val title=c.getString(0);val location="${c.getString(1)} / ${c.getString(2)} / ${c.getString(3)}";c.close()
        screen("📦 $title")
        label(location)
        label("QR içeriği: KUTUDEPOM:$id",14)
        button("＋ Ürün ekle") { itemForm(id,null) }
        db.readableDatabase.rawQuery("SELECT id,name,qty,note FROM items WHERE box_id=? ORDER BY name",arrayOf(id.toString())).use { cur ->
            if(cur.count==0) label("Bu kutu boş.")
            while(cur.moveToNext()) {
                val itemId=cur.getLong(0);val name=cur.getString(1);val qty=cur.getInt(2)
                button("$name  × $qty") { itemForm(id,itemId) }
            }
        }
        button("Ana sayfa") { home() }
    }
    private fun itemForm(boxId:Long, itemId:Long?) {
        var oldName="";var oldQty=1;var oldNote=""
        if(itemId!=null) db.readableDatabase.rawQuery("SELECT name,qty,note FROM items WHERE id=?",arrayOf(itemId.toString())).use { c ->
            if(c.moveToFirst()){oldName=c.getString(0);oldQty=c.getInt(1);oldNote=c.getString(2)}
        }
        screen(if(itemId==null)"Ürün ekle" else "Ürünü düzenle")
        val name=input("Ürün adı",oldName)
        val qty=input("Adet",oldQty.toString()).apply { inputType=2 }
        val note=input("Not / açıklama",oldNote)
        button("− 1") { qty.setText(((qty.text.toString().toIntOrNull()?:0)-1).coerceAtLeast(0).toString()) }
        button("＋ 1") { qty.setText(((qty.text.toString().toIntOrNull()?:0)+1).toString()) }
        button("Kaydet") {
            if(name.text.isBlank()){toast("Ürün adı gerekli");return@button}
            val values=android.content.ContentValues().apply {
                put("name",name.text.toString());put("qty",(qty.text.toString().toIntOrNull()?:0).coerceAtLeast(0))
                put("note",note.text.toString());put("box_id",boxId)
            }
            if(itemId==null) db.writableDatabase.insert("items",null,values)
            else db.writableDatabase.update("items",values,"id=?",arrayOf(itemId.toString()))
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
                    .setPositiveButton("Sil") { _,_->db.writableDatabase.delete("items","id=?",arrayOf(itemId.toString()));showBox(boxId)}
                    .setNegativeButton("Vazgeç",null).show()
            }
        }
        button("Geri") { showBox(boxId) }
    }
    private fun searchItems(q:String) {
        screen("Arama sonuçları")
        button("Ana sayfa") { home(q) }
        if(q.isBlank()){label("Aranacak ürün adını yaz.");return}
        val sql="SELECT i.name,i.qty,b.id,b.name,b.room,b.cupboard,b.shelf FROM items i JOIN boxes b ON b.id=i.box_id WHERE i.name LIKE ? ORDER BY i.name"
        db.readableDatabase.rawQuery(sql,arrayOf("%$q%")).use { c ->
            if(c.count==0) label("Ürün bulunamadı.")
            while(c.moveToNext()) {
                val id=c.getLong(2)
                button("${c.getString(0)} × ${c.getInt(1)}\n${c.getString(4)} / ${c.getString(5)} / ${c.getString(6)} / ${c.getString(3)}") { showBox(id) }
            }
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { home() }
}
