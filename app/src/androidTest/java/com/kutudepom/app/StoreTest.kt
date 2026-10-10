package com.kutudepom.app

import android.database.sqlite.SQLiteDatabase
import android.content.ContentValues
import android.database.DatabaseUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreTest {
    @Test fun preservesVersionOneDataAndEnforcesStockBounds() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("migration-test.db")
        val file=context.getDatabasePath("migration-test.db"); file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
            db.execSQL("CREATE TABLE boxes(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,room TEXT NOT NULL,cupboard TEXT NOT NULL,shelf TEXT NOT NULL)")
            db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,qty INTEGER NOT NULL DEFAULT 1,box_id INTEGER NOT NULL,note TEXT NOT NULL DEFAULT '')")
            db.execSQL("INSERT INTO boxes VALUES(17,'Kutu','Oda','Dolap','Raf')")
            db.execSQL("INSERT INTO items VALUES(23,'Vida',0,17,'Eski kayıt')")
            db.version=1
        }
        val store=Store(context,"migration-test.db")
        try {
            assertEquals(17L,store.boxes().single().first)
            store.readableDatabase.rawQuery("SELECT id,name,note,photo FROM items",null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals(23L,c.getLong(0)); assertEquals("Vida",c.getString(1)); assertEquals("Eski kayıt",c.getString(2)); assertTrue(c.isNull(3))
            }
            assertFalse(store.changeStock(23,-1)); assertTrue(store.changeStock(23,1))
            store.writableDatabase.execSQL("UPDATE items SET qty=2147483647 WHERE id=23")
            assertFalse(store.changeStock(23,1))
            rejects { store.writableDatabase.execSQL("DELETE FROM boxes WHERE id=17") }
            rejects { store.writableDatabase.execSQL("UPDATE items SET box_id=99 WHERE id=23") }
            rejects { store.writableDatabase.execSQL("UPDATE items SET qty=-1 WHERE id=23") }
        } finally { store.close(); context.deleteDatabase("migration-test.db") }
    }
    @Test fun versionTwoKeepsPhotoAndNotesAndAddsTransactionalDocuments() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="version-two-test.db"
        context.deleteDatabase(name)
        val file=context.getDatabasePath(name); file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
            db.execSQL("CREATE TABLE boxes(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,room TEXT NOT NULL,cupboard TEXT NOT NULL,shelf TEXT NOT NULL)")
            db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,qty INTEGER NOT NULL DEFAULT 1,box_id INTEGER NOT NULL,note TEXT NOT NULL DEFAULT '',photo TEXT)")
            db.execSQL("INSERT INTO boxes VALUES(17,'Kutu','','','')")
            db.execSQL("INSERT INTO items VALUES(23,'Matkap',7,17,'Eski not','old-photo.img')")
            db.version=2
        }
        val store=Store(context,name)
        try {
            assertEquals(3,store.readableDatabase.version)
            assertEquals("Kutu",store.boxes().single().second)
            store.readableDatabase.rawQuery("SELECT qty,note,photo FROM items WHERE id=23",null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals(7,c.getInt(0)); assertEquals("Eski not",c.getString(1)); assertEquals("old-photo.img",c.getString(2))
            }
            val values=ContentValues().apply { put("name","Matkap"); put("qty",7); put("box_id",17); put("note","Eski not"); put("photo","old-photo.img") }
            val manual=Manual(name="Kılavuz.pdf",storedName="test.pdf",mime="application/pdf",size=64)
            store.saveItem(23,values,listOf(manual),emptySet())
            assertEquals("Kılavuz.pdf",store.manuals(23).single().name)
            rejects { store.saveItem(null,values,listOf(manual),emptySet()) }
            assertEquals(1L,DatabaseUtils.longForQuery(store.readableDatabase,"SELECT COUNT(*) FROM items",null))
            assertEquals(1,store.manuals(23).size)
            val id=store.manuals(23).single().id
            store.saveItem(23,values,emptyList(),setOf(id)); assertTrue(store.manuals(23).isEmpty())
            store.saveItem(23,values,listOf(manual),emptySet())
            store.close()
            Store(context,name).use { reopened ->
                assertEquals("Kılavuz.pdf",reopened.manuals(23).single().name)
                reopened.writableDatabase.delete("items","id=?",arrayOf("23"))
                assertTrue(reopened.manuals(23).isEmpty())
            }
        } finally { store.close(); context.deleteDatabase(name) }
    }
    private fun rejects(action:()->Unit) {
        try { action(); fail("İşlem reddedilmeliydi") } catch(ex:Exception) { }
    }
}
