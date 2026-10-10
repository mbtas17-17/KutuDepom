package com.kutudepom.app

import android.content.Context
import android.content.ContentValues
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Additive migrations keep boxes, products, photographs and legacy QR IDs. */
class Store(ctx: Context, name: String = "kutudepom.db") : SQLiteOpenHelper(ctx,name,null,3) {
    override fun onConfigure(db:SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE boxes(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, room TEXT NOT NULL, cupboard TEXT NOT NULL, shelf TEXT NOT NULL)")
        db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, qty INTEGER NOT NULL DEFAULT 1, box_id INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '', photo TEXT)")
        integrity(db)
        documentSchema(db)
    }
    override fun onUpgrade(db:SQLiteDatabase, oldVersion:Int, newVersion:Int) {
        if(oldVersion<2) {
            db.execSQL("ALTER TABLE items ADD COLUMN photo TEXT")
            db.execSQL("UPDATE items SET qty=MAX(0,MIN(qty,2147483647))")
            // Preserve any orphaned rows in a recovery box instead of discarding them.
            val count=DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM items WHERE box_id NOT IN (SELECT id FROM boxes)",null)
            if(count>0) {
                db.execSQL("INSERT INTO boxes(name,room,cupboard,shelf) VALUES('Kurtarılan ürünler','Kurtarılan','Belirtilmemiş','Belirtilmemiş')")
                db.execSQL("UPDATE items SET box_id=last_insert_rowid() WHERE box_id NOT IN (SELECT id FROM boxes)")
            }
            integrity(db)
        }
        if(oldVersion<3) documentSchema(db)
    }
    private fun documentSchema(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE product_documents(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
            display_name TEXT NOT NULL,
            stored_name TEXT NOT NULL UNIQUE,
            mime TEXT NOT NULL,
            size INTEGER NOT NULL CHECK(size>=0))""")
        db.execSQL("CREATE INDEX document_item ON product_documents(item_id)")
    }
    private fun integrity(db:SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS item_box ON items(box_id)")
        for(event in listOf("INSERT","UPDATE")) {
            db.execSQL("""CREATE TRIGGER item_${event.lowercase()} BEFORE $event ON items
                WHEN NEW.qty<0 OR NEW.qty>2147483647 OR length(trim(NEW.name))=0
                OR NOT EXISTS(SELECT 1 FROM boxes WHERE id=NEW.box_id)
                BEGIN SELECT RAISE(ABORT,'Invalid item'); END""")
        }
        db.execSQL("""CREATE TRIGGER box_delete BEFORE DELETE ON boxes
            WHEN EXISTS(SELECT 1 FROM items WHERE box_id=OLD.id)
            BEGIN SELECT RAISE(ABORT,'Box contains items'); END""")
    }
    fun boxes():List<Pair<Long,String>> = readableDatabase.rawQuery("SELECT id,name,room,cupboard,shelf FROM boxes ORDER BY room,cupboard,shelf,name",null).use { c ->
        buildList { while(c.moveToNext()) add(c.getLong(0) to listOf(c.getString(2),c.getString(3),c.getString(4),c.getString(1)).filter { it.isNotBlank() }.joinToString(" → ")) }
    }
    fun changeStock(id:Long, delta:Int):Boolean {
        require(delta==1 || delta == -1)
        return writableDatabase.compileStatement("UPDATE items SET qty=qty+? WHERE id=? AND qty+? BETWEEN 0 AND 2147483647").use {
            it.bindLong(1,delta.toLong()); it.bindLong(2,id); it.bindLong(3,delta.toLong()); it.executeUpdateDelete()==1
        }
    }
    fun photoInUse(name:String):Boolean = DatabaseUtils.longForQuery(readableDatabase,"SELECT COUNT(*) FROM items WHERE photo=?",arrayOf(name))>0
    fun manuals(itemId:Long):List<Manual> = readableDatabase.rawQuery(
        "SELECT id,display_name,stored_name,mime,size FROM product_documents WHERE item_id=? ORDER BY id",arrayOf(itemId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(Manual(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4))) } }
    fun manualFiles():Set<String> = readableDatabase.rawQuery("SELECT stored_name FROM product_documents",null).use { c ->
        buildSet { while(c.moveToNext()) add(c.getString(0)) }
    }
    fun saveItem(id:Long?,values:ContentValues,added:List<Manual>,removed:Set<Long>):Long {
        val db=writableDatabase
        db.beginTransaction()
        try {
            val itemId=if(id==null) db.insertOrThrow("items",null,values) else {
                check(db.update("items",values,"id=?",arrayOf(id.toString()))==1); id
            }
            removed.forEach { db.delete("product_documents","id=? AND item_id=?",arrayOf(it.toString(),itemId.toString())) }
            added.forEach { m -> db.insertOrThrow("product_documents",null,ContentValues().apply {
                put("item_id",itemId); put("display_name",m.name); put("stored_name",m.storedName); put("mime",m.mime); put("size",m.size)
            }) }
            db.setTransactionSuccessful(); return itemId
        } finally { db.endTransaction() }
    }
}
