package com.kutudepom.app

import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
    @Test fun realNamesOptionalLocationStableColorsAndLiveSearch() {
        context.deleteDatabase("kutudepom.db")
        try {
            Store(context).use { s ->
                s.writableDatabase.execSQL("INSERT INTO boxes(name,room,cupboard,shelf) VALUES('1','','','')")
                s.writableDatabase.execSQL("INSERT INTO boxes(name,room,cupboard,shelf) VALUES('Uzun kutu adı: elektrikli aletler ve bağlantı parçaları','Çalışma odası','Mavi dolap','Üst raf')")
                s.writableDatabase.execSQL("INSERT INTO items(name,box_id,note) VALUES('İğne',2,'Kırmızı iplik')")
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var firstColor=0
                var search:EditText?=null
                scenario.onActivity { a ->
                    val list=a.findViewById<RecyclerView>(R.id.inventory_list)
                    val card=list.findViewHolderForAdapterPosition(0)!!.itemView
                    val texts=views(card).filterIsInstance<TextView>()
                    assertEquals("1",texts[0].text.toString())
                    assertEquals(View.GONE,texts[1].visibility)
                    firstColor=views(card).filterIsInstance<ImageView>()[0].imageTintList!!.defaultColor
                    val located=list.findViewHolderForAdapterPosition(1)!!.itemView
                    val labels=views(located).filterIsInstance<TextView>()
                    assertEquals("Uzun kutu adı: elektrikli aletler ve bağlantı parçaları",labels[0].text.toString())
                    assertEquals("Çalışma odası → Mavi dolap → Üst raf",labels[1].text.toString())
                    assertEquals(2,labels[0].maxLines)
                    assertTrue(labels[1].top>=labels[0].bottom)
                    capture(a,"home-light")
                    search=views(a.window.decorView).filterIsInstance<EditText>().single()
                    search!!.setText("kırmızı")
                    assertSame(search,views(a.window.decorView).filterIsInstance<EditText>().single())
                    assertEquals(1,list.adapter!!.itemCount)
                    search!!.setText("Çalışma odası"); assertEquals(1,list.adapter!!.itemCount)
                    search!!.setText("iğne"); assertEquals(1,list.adapter!!.itemCount)
                    search!!.setText("eşleşmez"); assertEquals(0,list.adapter!!.itemCount)
                    search!!.setText(""); assertEquals(2,list.adapter!!.itemCount)
                }
                // Change order, not the permanent ID, and reload without modifying any name.
                Store(context).use { it.writableDatabase.execSQL("UPDATE boxes SET room='Ş oda' WHERE id=1") }
                scenario.recreate()
                scenario.onActivity { a ->
                    val list=a.findViewById<RecyclerView>(R.id.inventory_list)
                    val card=list.findViewHolderForAdapterPosition(1)!!.itemView
                    assertEquals("1",views(card).filterIsInstance<TextView>()[0].text.toString())
                    assertEquals(firstColor,views(card).filterIsInstance<ImageView>()[0].imageTintList!!.defaultColor)
                    a.delegate.localNightMode=androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { a -> capture(a,"home-dark"); a.findViewById<RecyclerView>(R.id.inventory_list).findViewHolderForAdapterPosition(0)!!.itemView.performClick() }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { a -> capture(a,"box-dark"); a.delegate.localNightMode=androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { a -> capture(a,"box-light") }
            }
        } finally { context.deleteDatabase("kutudepom.db") }
    }
    private fun capture(a:MainActivity,name:String) {
        assertTrue(a.window.decorView.width>0)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("mkdir -p /sdcard/Download/kutudepom-previews")).use { it.readBytes() }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p /sdcard/Download/kutudepom-previews/$name.png")).use { it.readBytes() }
    }
    @Test fun pastelIconContrastInBothThemes() {
        val pairs=listOf(R.color.pastel_green to R.color.ink_green,R.color.pastel_blue to R.color.ink_blue,R.color.pastel_purple to R.color.ink_purple,R.color.pastel_orange to R.color.ink_orange)
        for(mode in listOf(Configuration.UI_MODE_NIGHT_NO,Configuration.UI_MODE_NIGHT_YES)) {
            val config=Configuration(context.resources.configuration).apply { uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode }
            val themed=context.createConfigurationContext(config)
            pairs.forEach { (background,foreground) -> assertTrue("Pastel text/icon contrast >=4.5",ColorUtils.calculateContrast(themed.getColor(foreground),themed.getColor(background))>=4.5) }
        }
    }
}
