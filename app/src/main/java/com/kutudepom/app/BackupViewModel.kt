package com.kutudepom.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BackupViewModel(application:Application):AndroidViewModel(application) {
    data class State(val busy:Boolean=false,val progress:String="",val pending:BackupRepository.Prepared?=null,val message:String?=null,val restored:Boolean=false)
    private val repository=BackupRepository(application)
    private val mutable=MutableStateFlow(State())
    val state=mutable.asStateFlow()
    private fun operation(action:suspend ()->State) {
        if(mutable.value.busy || mutable.value.pending!=null) return
        mutable.value=State(busy=true,progress="İşlem hazırlanıyor…")
        viewModelScope.launch {
            mutable.value=try { withContext(Dispatchers.IO) { action() } }
            catch(ex:Exception) { State(message=repository.message(ex)) }
        }
    }
    private fun progress(message:String) { mutable.value=State(busy=true,progress=message) }
    fun export(uri:Uri)=operation { repository.export(uri,::progress); State(message="Yedek dosyası eksiksiz kaydedildi.") }
    fun inspect(uri:Uri)=operation { State(pending=repository.prepare(uri,::progress)) }
    fun restore() {
        val prepared=mutable.value.pending ?: return
        mutable.value=State()
        operation { repository.restore(prepared,::progress); State(message="Yedek geri yüklendi. Kutular, ürünler ve dosyalar hazır.",restored=true) }
    }
    fun cancelImport() {
        val prepared=mutable.value.pending ?: return
        mutable.value=State()
        viewModelScope.launch(Dispatchers.IO) { repository.discard(prepared) }
    }
    fun acknowledge() { if(!mutable.value.busy && mutable.value.pending==null) mutable.value=State() }
    override fun onCleared() {
        mutable.value.pending?.let { prepared -> Thread { repository.discard(prepared) }.start() }
        super.onCleared()
    }
}
