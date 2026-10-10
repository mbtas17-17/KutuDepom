package com.kutudepom.app

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupUiTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
    private fun press(a:MainActivity,name:String)=views(a.window.decorView).filterIsInstance<Button>().first { it.text.toString()==name }.performClick()
    private fun dialogButton(id:Int) { onView(withId(id)).inRoot(isDialog()).perform(object:ViewAction {
        override fun getDescription()="Activate backup dialog button"
        override fun getConstraints():Matcher<View> = isDisplayed()
        override fun perform(controller:UiController,view:View) { assertTrue(view.performClick()); controller.loopMainThreadUntilIdle() }
    }) }
    private fun await(scenario:ActivityScenario<MainActivity>,condition:(BackupViewModel.State)->Boolean) {
        val deadline=System.currentTimeMillis()+15000; var ready=false
        while(!ready && System.currentTimeMillis()<deadline) {
            scenario.onActivity { ready=condition(ViewModelProvider(it)[BackupViewModel::class.java].state.value) }
            if(!ready) Thread.sleep(50)
        }
        assertTrue("Backup state did not finish",ready)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    @Test fun systemPickersExportAndConfirmationCancellationRecreationAndRestore() {
        context.deleteDatabase("kutudepom.db"); Intents.init()
        try {
            Store(context).use { it.writableDatabase.execSQL("INSERT INTO boxes(name,room,cupboard,shelf) VALUES('Yedekteki kutu','','','')") }
            val file=File(context.cacheDir,"labels/ui-backup.zip").apply { parentFile?.mkdirs() }
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,Intent().setData(uri)))
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,Intent().setData(uri)))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { assertTrue(press(it,"Yedekle")) }
                await(scenario) { !it.busy && file.length()>0 }
                assertTrue(Intents.getIntents().any { it.action==Intent.ACTION_CREATE_DOCUMENT && it.type=="application/zip" })
                val prepared=BackupRepository(context).prepare(uri); BackupRepository(context).discard(prepared)
                // Dismiss export success before starting the import picker.
                dialogButton(android.R.id.button1)
                Store(context).use { it.writableDatabase.execSQL("UPDATE boxes SET name='Mevcut kutu' WHERE id=1") }
                scenario.onActivity { assertTrue(press(it,"İçe Aktar")) }
                await(scenario) { it.pending!=null }
                assertTrue(Intents.getIntents().any { it.action==Intent.ACTION_OPEN_DOCUMENT })
                Store(context).use { assertEquals("Mevcut kutu",it.boxes().single().second) }
                scenario.recreate()
                await(scenario) { it.pending!=null }
                dialogButton(android.R.id.button2)
                await(scenario) { it.pending==null && !it.busy }
                Store(context).use { assertEquals("Mevcut kutu",it.boxes().single().second) }
                scenario.onActivity { assertTrue(press(it,"İçe Aktar")) }
                await(scenario) { it.pending!=null }
                dialogButton(android.R.id.button1)
                await(scenario) { !it.busy && it.pending==null && Store(context).use { store -> store.boxes().single().second=="Yedekteki kutu" } }
                dialogButton(android.R.id.button1)
                scenario.onActivity { a ->
                    assertTrue(views(a.window.decorView).any { it.contentDescription=="Yedekteki kutu, kutu içeriğini aç" })
                    assertFalse(views(a.window.decorView).filterIsInstance<Button>().any { it.text.toString().contains("Adet") })
                }
            }
        } finally { Intents.release(); context.deleteDatabase("kutudepom.db") }
    }
}
