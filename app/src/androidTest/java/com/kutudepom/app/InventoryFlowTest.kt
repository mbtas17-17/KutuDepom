package com.kutudepom.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryFlowTest {
    private fun views(view:View):List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList())
    private fun click(activity:MainActivity,text:String) {
        val button=views(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString()==text }
        assertTrue(button.performClick())
    }
    private fun set(activity:MainActivity,hint:String,value:String) {
        views(activity.window.decorView).filterIsInstance<EditText>().first { it.hint.toString()==hint }.setText(value)
    }
    @Test fun createsBoxAndProductKeepsDraftOnRecreationAndChangesStock() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("kutudepom.db")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                click(a,"＋ Yeni kutu")
                set(a,"Kutu adı (örn. Kutu 1)","Test kutusu")
                set(a,"Oda (örn. Proje odası)","Atölye"); set(a,"Dolap","Dolap 1"); set(a,"Raf","Raf 1")
                click(a,"Kaydet"); click(a,"＋ Ürün ekle")
                set(a,"Ürün adı","İplik"); set(a,"Adet","2"); set(a,"Not / açıklama","Kırmızı")
            }
            scenario.recreate()
            scenario.onActivity { a -> click(a,"Kaydet"); click(a,"− 1 · İplik") }
            Store(context).use { store ->
                assertTrue(store.boxes().single().second.contains("Atölye → Dolap 1 → Raf 1 → Test kutusu"))
                store.readableDatabase.rawQuery("SELECT name,qty,note FROM items",null).use { c ->
                    assertTrue(c.moveToFirst()); assertEquals("İplik",c.getString(0)); assertEquals(1,c.getInt(1)); assertEquals("Kırmızı",c.getString(2))
                }
            }
            scenario.onActivity { a ->
                click(a,"QR etiketi göster / paylaş")
                assertTrue(views(a.window.decorView).isNotEmpty())
            }
        }
        context.deleteDatabase("kutudepom.db")
    }
}
