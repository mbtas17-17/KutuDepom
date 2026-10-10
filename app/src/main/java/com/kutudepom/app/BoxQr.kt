package com.kutudepom.app

object BoxQr {
    fun encode(id:Long):String { require(id>0); return "KUTUDEPOM:$id" }
    fun decode(raw:String):Long? = if(raw.startsWith("KUTUDEPOM:")) raw.removePrefix("KUTUDEPOM:").toLongOrNull()?.takeIf { it>0 } else null
}
