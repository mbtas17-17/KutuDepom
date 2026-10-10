package com.kutudepom.app

import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.database.DatabaseUtils
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class InventoryFlowTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun views(view:View):List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList())
    private fun buttons(a:MainActivity)=views(a.window.decorView).filterIsInstance<Button>()
    private fun press(a:MainActivity,text:String) { assertTrue(buttons(a).first { it.text.toString()==text }.performClick()) }
    private fun field(a:MainActivity,hint:String)=views(a.window.decorView).filterIsInstance<EditText>().first { it.hint.toString()==hint }
    private fun set(a:MainActivity,hint:String,value:String) { field(a,hint).setText(value) }
    private fun layout(a:MainActivity) {
        val content=a.findViewById<View>(android.R.id.content)
        content.measure(View.MeasureSpec.makeMeasureSpec(content.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(content.height,View.MeasureSpec.EXACTLY))
        content.layout(content.left,content.top,content.right,content.bottom)
    }
    private fun openBox(a:MainActivity,name:String) {
        layout(a)
        assertTrue(views(a.window.decorView).filterIsInstance<MaterialCardView>().first { it.contentDescription=="$name, kutu içeriğini aç" }.performClick())
    }
    // Other activity controls are activated with performClick too. Use the same deterministic
    // activation for dialog buttons; emulator IME/window transitions can discard injected taps.
    private fun dialogButton(id:Int) {
        onView(withId(id)).inRoot(isDialog()).perform(object:androidx.test.espresso.ViewAction {
            override fun getDescription()="Activate confirmation button"
            override fun getConstraints():org.hamcrest.Matcher<View> = androidx.test.espresso.matcher.ViewMatchers.isDisplayed()
            override fun perform(controller:androidx.test.espresso.UiController,view:View) {
                assertTrue(view.performClick()); controller.loopMainThreadUntilIdle()
            }
        })
    }
    private fun chooseOperation(scenario:ActivityScenario<MainActivity>,name:String) {
        scenario.onActivity { press(it,"Kutu İşlemleri") }
        onView(withText(name)).inRoot(isDialog()).perform(click())
    }
    private fun openProduct(a:MainActivity,name:String) {
        layout(a)
        val card=views(a.window.decorView).filterIsInstance<MaterialCardView>().first { it.contentDescription=="$name, ürün detayını aç" }
        assertTrue(card.performClick())
    }
    private fun createBox(a:MainActivity) {
        press(a,"Yeni Kutu"); set(a,"Kutu adı (örn. Kutu 1)","Test kutusu"); press(a,"Kaydet")
    }
    private fun awaitDocument(scenario:ActivityScenario<MainActivity>,name:String) {
        val deadline=System.currentTimeMillis()+10000
        var found=false
        while(!found && System.currentTimeMillis()<deadline) {
            scenario.onActivity { a -> found=views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString()==name } }
            if(!found) Thread.sleep(50)
        }
        assertTrue("Belge listede görünmeli",found)
    }
    @Test fun optionalLocationsRepeatedSearchDraftPersistenceAndNoStockFields() {
        context.deleteDatabase("kutudepom.db")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                assertFalse(buttons(a).any { it.text.toString()=="Ara" })
                set(a,"Ürün, not veya konum ara...","   ")
                assertFalse(buttons(a).any { it.text.toString()=="Ara" })
                assertFalse(buttons(a).any { it.text.toString()=="Oda → dolap → raf → kutu" })
                press(a,"Yeni Kutu"); press(a,"Kaydet")
                Store(context).use { assertTrue(it.boxes().isEmpty()) }
                set(a,"Kutu adı (örn. Kutu 1)","Test kutusu"); press(a,"Kaydet")
            }
            chooseOperation(scenario,"Kutu konumunu düzenle")
            scenario.onActivity { a ->
                set(a,"Kutu",""); press(a,"Kaydet"); assertNotNull(field(a,"Kutu").error)
                set(a,"Kutu","Test kutusu"); set(a,"Oda",""); set(a,"Dolap",""); set(a,"Raf",""); press(a,"Kaydet")
                press(a,"Ürün Ekle"); set(a,"Ürün adı","İplik"); set(a,"Not / açıklama","Kırmızı")
                assertFalse(views(a.window.decorView).filterIsInstance<EditText>().any { it.hint.toString()=="Adet" })
                assertFalse(buttons(a).any { it.text.toString().contains("1 ·") })
            }
            scenario.recreate()
            scenario.onActivity { a ->
                assertEquals("İplik",field(a,"Ürün adı").text.toString())
                press(a,"Kaydet")
                layout(a)
                val card=views(a.window.decorView).filterIsInstance<MaterialCardView>().first { it.contentDescription=="İplik, ürün detayını aç" }
                assertTrue(views(card).filterIsInstance<Button>().isEmpty())
                assertEquals(listOf("İplik"),views(card).filterIsInstance<TextView>().map { it.text.toString() }.filter { it.isNotBlank() })
                press(a,"Ana sayfa"); set(a,"Ürün, not veya konum ara...","iplik")
                assertFalse(buttons(a).any { it.text.toString()=="Ara" })
                assertEquals("iplik",field(a,"Ürün, not veya konum ara...").text.toString())
                openProduct(a,"İplik"); press(a,"Geri"); press(a,"Ana sayfa")
                set(a,"Ürün, not veya konum ara...","Kırmızı")
                set(a,"Ürün, not veya konum ara...","bulunmayan")
                assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="Ürün bulunamadı." })
                set(a,"Ürün, not veya konum ara...",""); assertFalse(buttons(a).any { it.text.toString()=="Ara" })
            }
            Store(context).use { store ->
                assertEquals("Test kutusu",store.boxes().single().second)
                store.readableDatabase.rawQuery("SELECT name,qty,note FROM items",null).use { c ->
                    assertTrue(c.moveToFirst()); assertEquals("İplik",c.getString(0)); assertEquals(1,c.getInt(1)); assertEquals("Kırmızı",c.getString(2))
                }
            }
        }
        context.deleteDatabase("kutudepom.db")
    }
    @Test fun copiedDocumentsSurviveRecreationOpenRemoveMoveAndDeleteWithProduct() {
        context.deleteDatabase("kutudepom.db")
        Intents.init()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { a -> createBox(a); press(a,"Ürün Ekle"); set(a,"Ürün adı","Matkap") }
                val source=File(context.filesDir,"manuals/kilavuz.pdf").apply { parentFile?.mkdirs(); writeText("%PDF-1.4\nTest manual\n%%EOF") }
                val sourceUri=FileProvider.getUriForFile(context,"${context.packageName}.files",source)
                intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,Intent().setData(sourceUri)))
                scenario.onActivity { a -> press(a,"Belge ekle") }
                awaitDocument(scenario,"kilavuz.pdf")
                scenario.recreate()
                scenario.onActivity { a -> press(a,"Kaydet"); openProduct(a,"Matkap") }
                val storage=ManualStorage(context)
                var first:Manual?=null
                Store(context).use { store -> first=store.manuals(1).single() }
                assertEquals("kilavuz.pdf",first!!.name); assertEquals("application/pdf",first!!.mime)
                assertNotEquals(source.name,first!!.storedName)
                source.delete()
                val open=storage.openIntent(first!!)
                assertEquals(Intent.ACTION_VIEW,open.action); assertEquals("application/pdf",open.type)
                assertTrue(open.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertEquals("%PDF-1.4\nTest manual\n%%EOF",context.contentResolver.openInputStream(open.data!!)!!.bufferedReader().use { it.readText() })
                scenario.onActivity { a -> press(a,"Kaldır") }
                dialogButton(android.R.id.button1)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { a ->
                    assertEquals("Matkap",field(a,"Ürün adı").text.toString())
                    assertFalse("Removal must be staged in form",views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="kilavuz.pdf" })
                    press(a,"Kaydet")
                    assertFalse("Save must leave the form",views(a.window.decorView).filterIsInstance<EditText>().any { it.hint.toString()=="Ürün adı" })
                }
                Store(context).use { assertTrue(it.manuals(1).isEmpty()) }
                assertFalse(storage.file(first!!).exists())
                source.writeText("%PDF-1.4\nSecond manual\n%%EOF")
                scenario.onActivity { a -> openProduct(a,"Matkap"); press(a,"Belge ekle") }
                awaitDocument(scenario,"kilavuz.pdf")
                scenario.onActivity { a -> press(a,"Kaydet"); openProduct(a,"Matkap") }
                var second:Manual?=null
                Store(context).use { store ->
                    second=store.manuals(1).single()
                    store.writableDatabase.insertOrThrow("boxes",null,ContentValues().apply {
                        put("name","Hedef"); put("room",""); put("cupboard",""); put("shelf","")
                    })
                }
                scenario.onActivity { a -> press(a,"Başka kutuya taşı") }
                onView(withText("Hedef")).inRoot(isDialog()).perform(click())
                Store(context).use { store ->
                    assertEquals(second!!.storedName,store.manuals(1).single().storedName)
                    assertEquals(2L,DatabaseUtils.longForQuery(store.readableDatabase,"SELECT box_id FROM items WHERE id=1",null))
                }
                scenario.onActivity { a -> openProduct(a,"Matkap"); press(a,"Ürünü sil") }
                dialogButton(android.R.id.button1)
                Store(context).use { store ->
                    assertTrue(store.manuals(1).isEmpty())
                    assertEquals(0L,DatabaseUtils.longForQuery(store.readableDatabase,"SELECT COUNT(*) FROM items",null))
                }
                assertFalse(storage.file(second!!).exists())
                source.delete()
                chooseOperation(scenario,"Boş kutuyu sil")
                // Opening the confirmation must not delete the box.
                Store(context).use { assertEquals(2,it.boxes().size) }
                dialogButton(android.R.id.button2)
            }
        } finally { Intents.release(); context.deleteDatabase("kutudepom.db") }
    }
    @Test fun photoQrAndDarkMaterialThemeRemainAvailable() {
        context.deleteDatabase("kutudepom.db")
        Intents.init()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { a -> createBox(a); press(a,"Ürün Ekle"); set(a,"Ürün adı","Kamera"); set(a,"Not / açıklama","Fotoğraf notu") }
                val source=File(context.filesDir,"manuals/source.png").apply { parentFile?.mkdirs() }
                val bitmap=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888)
                source.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",source)
                intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,Intent().setData(uri)))
                scenario.onActivity { a -> press(a,"Galeriden fotoğraf seç") }
                awaitDocument(scenario,"Fotoğraf seçildi ✓")
                scenario.recreate()
                scenario.onActivity { a -> press(a,"Kaydet"); press(a,"QR Etiketi") }
                dialogButton(android.R.id.button2)
                Store(context).use { store ->
                    store.readableDatabase.rawQuery("SELECT photo,note FROM items",null).use { c ->
                        assertTrue(c.moveToFirst()); assertEquals("Fotoğraf notu",c.getString(1))
                        assertTrue(File(context.filesDir,"photos/${c.getString(0)}").isFile)
                    }
                }
                scenario.onActivity { androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) }
                scenario.recreate()
                scenario.onActivity { a ->
                    assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,a.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    assertTrue(buttons(a).all { it is com.google.android.material.button.MaterialButton })
                    openProduct(a,"Kamera")
                    assertTrue(field(a,"Ürün adı") is com.google.android.material.textfield.TextInputEditText)
                    press(a,"Geri")
                }
                source.delete()
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
            Intents.release(); context.deleteDatabase("kutudepom.db")
        }
    }

    @Test fun openDocumentButtonDispatchesReadOnlyViewerIntent() {
        context.deleteDatabase("kutudepom.db")
        Intents.init()
        val file=File(context.filesDir,"manuals/dispatch-test.pdf").apply { parentFile?.mkdirs(); writeText("%PDF-1.4\n%%EOF") }
        try {
            Store(context).use { store ->
                store.writableDatabase.execSQL("INSERT INTO boxes(name,room,cupboard,shelf) VALUES('Kutu','','','')")
                store.saveItem(null,ContentValues().apply { put("name","Matkap"); put("qty",1); put("box_id",1); put("note","") },listOf(Manual(name="Kılavuz.pdf",storedName=file.name,mime="application/pdf",size=file.length())),emptySet())
            }
            intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,Intent()))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { a -> openBox(a,"Kutu"); openProduct(a,"Matkap"); press(a,"Aç") }
                intended(hasAction(Intent.ACTION_VIEW))
            }
        } finally { Intents.release(); context.deleteDatabase("kutudepom.db"); file.delete() }
    }

}
