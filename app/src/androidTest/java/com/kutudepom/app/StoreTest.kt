package com.kutudepom.app

import android.database.sqlite.SQLiteDatabase
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
    private fun rejects(action:()->Unit) {
        try { action(); fail("İşlem reddedilmeliydi") } catch(ex:Exception) { }
    }
}
