package com.kutudepom.app

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListUsabilityTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
    private fun press(a:MainActivity,name:String) { views(a.window.decorView).filterIsInstance<MaterialButton>().first { it.text.toString()==name }.performClick() }
    private fun field(a:MainActivity,name:String)=views(a.window.decorView).filterIsInstance<EditText>().first { it.hint.toString()==name }
    private fun rect(v:View):Rect {
        val visible=Rect()
        assertTrue("${v.javaClass.simpleName} must be visible",v.getGlobalVisibleRect(visible))
        val matrix=android.graphics.Matrix(); v.transformMatrixToGlobal(matrix)
        val full=android.graphics.RectF(0f,0f,v.width.toFloat(),v.height.toFloat()); matrix.mapRect(full)
        assertTrue("Control must remain fully visible: expected=$full actual=$visible scale=${v.scaleY}",
            visible.top<=full.top+2 && visible.bottom>=full.bottom-2 && visible.left<=full.left+2 && visible.right>=full.right-2)
        return visible
    }
    private fun seed() {
        context.deleteDatabase("kutudepom.db")
        Store(context).use { store ->
            val db=store.writableDatabase
            db.beginTransaction()
            try {
                repeat(100) { n -> db.execSQL("INSERT INTO boxes(name,room,cupboard,shelf) VALUES(?, '', '', '')",arrayOf("Kutu %03d".format(n))) }
                repeat(100) { n -> db.execSQL("INSERT INTO items(name,qty,box_id,note) VALUES(?,1,1,?)",arrayOf("Ürün %03d".format(n),"Çığ, öğütücü, şişe, İğne.\n".repeat(100))) }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }
    @Test fun hundredBoxesAndProductsScrollWithoutMovingControlsAndNotesStayInDetail() {
        seed()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var headerBounds=Rect()
                scenario.onActivity { a ->
                    val list=a.findViewById<RecyclerView>(R.id.inventory_list)
                    assertEquals(100,list.adapter!!.itemCount)
                    assertTrue(list.height>0)
                    assertFalse(views(a.window.decorView).any { it is android.widget.ScrollView })
                    headerBounds=rect(a.findViewById(R.id.fixed_header))
                    (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(99,0)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { a ->
                    assertEquals(headerBounds,rect(a.findViewById(R.id.fixed_header)))
                    listOf("Yeni Kutu","QR Kod Tara").forEach { name -> rect(views(a.window.decorView).filterIsInstance<MaterialButton>().first { it.text.toString()==name }) }
                    val list=a.findViewById<RecyclerView>(R.id.inventory_list)
                    assertTrue((list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()>=99)
                    list.scrollToPosition(0)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { a -> a.findViewById<RecyclerView>(R.id.inventory_list).findViewHolderForAdapterPosition(0)!!.itemView.performClick() }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { a ->
                    val list=a.findViewById<RecyclerView>(R.id.inventory_list)
                    assertEquals(100,list.adapter!!.itemCount)
                    assertFalse(views(a.window.decorView).any { it is android.widget.ScrollView })
                    headerBounds=rect(a.findViewById(R.id.fixed_header))
                    (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(99,0)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { a ->
                    assertEquals(headerBounds,rect(a.findViewById(R.id.fixed_header)))
                    listOf("Ürün Ekle","QR Etiketi","Kutu İşlemleri").forEach { name -> rect(views(a.window.decorView).filterIsInstance<MaterialButton>().first { it.text.toString()==name }) }
                    val list=a.findViewById<RecyclerView>(R.id.inventory_list)
                    assertTrue((list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()>=99)
                    val card=list.findViewHolderForAdapterPosition(99)!!.itemView as MaterialCardView
                    assertEquals(listOf("Ürün 099"),views(card).filterIsInstance<TextView>().map { it.text.toString() }.filter { it.isNotBlank() })
                    card.performClick()
                    assertEquals("Çığ, öğütücü, şişe, İğne.\n".repeat(100),field(a,"Not / açıklama").text.toString())
                }
                scenario.recreate()
                scenario.onActivity { a ->
                    assertEquals("Çığ, öğütücü, şişe, İğne.\n".repeat(100),field(a,"Not / açıklama").text.toString())
                    press(a,"Kaydet")
                }
                Store(context).use { it.readableDatabase.rawQuery("SELECT note FROM items WHERE name='Ürün 099'",null).use { c -> assertTrue(c.moveToFirst()); assertEquals("Çığ, öğütücü, şişe, İğne.\n".repeat(100),c.getString(0)) } }
            }
        } finally { context.deleteDatabase("kutudepom.db") }
    }
    @Test fun keyboardKeepsBoxAndProductFieldsReachableAndDraftsIntact() {
        context.deleteDatabase("kutudepom.db")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                showKeyboard(scenario,"Ürün, not veya konum ara...")
                scenario.onActivity { a ->
                    val search=field(a,"Ürün, not veya konum ara..."); rect(search)
                    search.setText("eşleşmeyen arama")
                    assertSame(search,field(a,"Ürün, not veya konum ara...")); rect(search)
                    search.setText("")
                    press(a,"Yeni Kutu"); field(a,"Kutu adı (örn. Kutu 1)").setText("Klavye kutusu")
                }
                showKeyboard(scenario,"Raf")
                scenario.onActivity { a -> rect(field(a,"Raf")); press(a,"Kaydet"); press(a,"Ürün Ekle"); field(a,"Ürün adı").setText("Çalışma ürünü") }
                showKeyboard(scenario,"Not / açıklama")
                scenario.onActivity { a ->
                    rect(field(a,"Not / açıklama")); field(a,"Not / açıklama").setText("İçerik: ğüşiöç İĞÜŞÖÇ")
                    assertEquals(16f*a.resources.displayMetrics.scaledDensity,field(a,"Ürün adı").textSize,1f)
                    assertEquals(field(a,"Ürün adı").textSize,field(a,"Not / açıklama").textSize,0f)
                }
                scenario.recreate()
                scenario.onActivity { a -> assertEquals("İçerik: ğüşiöç İĞÜŞÖÇ",field(a,"Not / açıklama").text.toString()); press(a,"Kaydet") }
            }
        } finally { context.deleteDatabase("kutudepom.db") }
    }
    private fun showKeyboard(scenario:ActivityScenario<MainActivity>,hint:String) {
        scenario.onActivity { a ->
            val edit=field(a,hint); edit.requestFocus(); edit.setSelection(edit.text.length)
            (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(edit,InputMethodManager.SHOW_IMPLICIT)
        }
        val deadline=System.currentTimeMillis()+8000
        var visible=false
        while(!visible && System.currentTimeMillis()<deadline) {
            scenario.onActivity { a -> visible=ViewCompat.getRootWindowInsets(a.findViewById<View>(android.R.id.content))?.isVisible(WindowInsetsCompat.Type.ime())==true }
            if(!visible) Thread.sleep(100)
        }
        assertTrue("Actual soft keyboard should be visible",visible)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // Insets and focus scrolling are posted after IME visibility changes; await the final field bounds.
        val settle=System.currentTimeMillis()+5000
        var reached=false
        while(!reached && System.currentTimeMillis()<settle) {
            scenario.onActivity { a ->
                val edit=field(a,hint); val visibleRect=Rect()
                reached=edit.getGlobalVisibleRect(visibleRect) && visibleRect.height()>=edit.height-2
            }
            if(!reached) Thread.sleep(100)
        }
        if(!reached) scenario.onActivity { a ->
            val edit=field(a,hint); val bounds=Rect(); edit.getGlobalVisibleRect(bounds)
            val scroll=a.findViewById<View>(R.id.form_scroll) ?: a.findViewById<View>(R.id.fixed_header)
            println("Keyboard geometry: field=$hint height=${edit.height} visible=$bounds scrollHeight=${scroll.height} scrollY=${scroll.scrollY} insets=${ViewCompat.getRootWindowInsets(scroll)}")
        }
        assertTrue("Focused field must fit above keyboard: $hint",reached)
    }
}
