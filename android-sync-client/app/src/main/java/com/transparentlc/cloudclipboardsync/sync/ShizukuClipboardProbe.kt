package com.transparentlc.cloudclipboardsync.sync

import android.content.ClipData
import android.content.Intent
import android.os.IBinder
import android.os.Parcel

object ShizukuClipboardProbe {
    private const val CLIPBOARD_SERVICE_NAME = "clipboard"
    private const val INTERFACE_CLASS_NAME = "android.content.IClipboard"
    private const val TRANSACTION_GET_PRIMARY_CLIP = 4
    private const val ARG_NULL = "__NULL__"
    private const val ARG_EMPTY = "__EMPTY__"

    @JvmStatic
    fun main(args: Array<String>) {
        val packageName = decodePackageName(args.getOrNull(0))
        val userId = args.getOrNull(1)?.toIntOrNull() ?: 0
        val deviceId = args.getOrNull(2)?.toIntOrNull() ?: 0
        val callerUid = android.os.Process.myUid()
        val binder = getClipboardBinder() ?: run {
            println("ERROR::clipboard-binder-missing uid=$callerUid pkg=${packageName ?: "null"} user=$userId device=$deviceId")
            return
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(INTERFACE_CLASS_NAME)
            data.writeString(packageName)
            data.writeString(null)
            data.writeInt(userId)
            data.writeInt(deviceId)
            val transacted = binder.transact(TRANSACTION_GET_PRIMARY_CLIP, data, reply, 0)
            if (!transacted) {
                println("ERROR::transact-false uid=$callerUid pkg=${packageName ?: "null"} user=$userId device=$deviceId")
                return
            }
            reply.readException()
            val clip = reply.readTypedObject(ClipData.CREATOR)
            if (clip == null || clip.itemCount <= 0) {
                println("EMPTY::no-clip uid=$callerUid pkg=${packageName ?: "null"} user=$userId device=$deviceId")
                return
            }
            val item = clip.getItemAt(0)
            val text = item.text?.toString()
                ?: item.htmlText
                ?: item.uri?.toString()
                ?: item.intent?.toUri(Intent.URI_INTENT_SCHEME)
                ?: ""
            if (text.isBlank()) {
                println("EMPTY::blank-text uid=$callerUid pkg=${packageName ?: "null"} user=$userId device=$deviceId")
                return
            }
            println("TEXT::uid=$callerUid pkg=${packageName ?: "null"} user=$userId device=$deviceId::$text")
        } catch (error: Throwable) {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            println("ERROR::$detail uid=$callerUid pkg=${packageName ?: "null"} user=$userId device=$deviceId")
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun getClipboardBinder(): IBinder? {
        val serviceManagerClass = Class.forName("android.os.ServiceManager")
        val getService = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
        return getService.invoke(null, CLIPBOARD_SERVICE_NAME) as? IBinder
    }

    private fun decodePackageName(raw: String?): String? {
        return when (raw) {
            null, ARG_NULL -> null
            ARG_EMPTY -> ""
            else -> raw
        }
    }
}
