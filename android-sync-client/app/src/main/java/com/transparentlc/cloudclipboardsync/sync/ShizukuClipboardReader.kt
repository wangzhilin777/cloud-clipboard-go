package com.transparentlc.cloudclipboardsync.sync

import android.content.ClipData
import android.content.Context
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.RemoteException
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.ShizukuRemoteProcess
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.lang.reflect.Modifier

data class ShizukuClipboardReadResult(
    val success: Boolean,
    val text: String,
    val detail: String,
)

object ShizukuClipboardReader {
    private const val TAG = "ShizukuClipboardReader"
    private const val FAILURE_BACKOFF_MS = 30_000L
    private const val CLIPBOARD_SERVICE_NAME = "clipboard"
    private const val STUB_CLASS_NAME = "android.content.IClipboard\$Stub"
    private const val INTERFACE_CLASS_NAME = "android.content.IClipboard"
    private const val TRANSACTION_GET_PRIMARY_CLIP = 4
    private val METHOD_NAME_PRIORITY = linkedMapOf(
        "getUserPrimaryClip" to 0,
        "getPrimaryClip" to 1,
        "getPrimaryClipAsPackage" to 2,
        "getStashPrimaryClip" to 3,
    )
    @Volatile
    private var lastFailureAtMs = 0L
    @Volatile
    private var lastFailureDetail = ""
    @Volatile
    private var probeInFlight = false

    fun readText(context: Context, source: String = "poll"): ShizukuClipboardReadResult {
        val now = System.currentTimeMillis()
        val verboseProbe = source != "poll"
        val allowDeepProbe = source == "manual"
        val failureAt = lastFailureAtMs
        if (failureAt > 0L && now - failureAt < FAILURE_BACKOFF_MS) {
            val cachedDetail = "Shizuku 辅助读取暂时退避中，避免重复探针刷屏"
            lastFailureDetail = cachedDetail
            return failed(cachedDetail, log = false, recordFailureTime = false)
        }
        if (probeInFlight) {
            return failed("Shizuku 辅助读取正在处理上一轮请求，已跳过本次探针", log = false, recordFailureTime = false)
        }
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return failed("Shizuku 服务未运行")
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            return failed("Shizuku 未授权")
        }

        probeInFlight = true
        try {
            val binder = SystemServiceHelper.getSystemService(CLIPBOARD_SERVICE_NAME)
                ?: return failed("Shizuku 无法获取系统剪贴板服务")
            if (verboseProbe) {
                logBinderShape(binder)
            }
            val service = createClipboardServiceProxy(binder)
                ?: return failed("Shizuku 无法创建剪贴板服务代理")
            if (verboseProbe) {
                logServiceShape(service)
            }
            val methodAttempts = findGetPrimaryClipMethods(service)
            if (verboseProbe) {
                Log.w(
                    TAG,
                    "getPrimaryClip candidates=${methodAttempts.asSequence().map(::buildMethodSignature).distinct().take(40).joinToString(" | ").ifBlank { "无" }}",
                )
            }
            if (methodAttempts.isEmpty()) {
                if (!allowDeepProbe) {
                    return failed("Shizuku 直接读取未命中，已进入静默退避")
                }
                val fallback = readTextViaBinderTransaction(binder, context)
                if (fallback.success && fallback.text.isNotBlank()) {
                    return fallback
                }
                val remoteProcessFallback = readTextViaRemoteProcess(context)
                if (remoteProcessFallback.success) {
                    return remoteProcessFallback
                }
                return failed(
                    "Shizuku 未找到可用的 getPrimaryClip 接口：${listAvailableGetPrimaryClipSignatures(service)}；事务兜底：${fallback.detail}；远程进程兜底：${remoteProcessFallback.detail}",
                )
            }

            val methodFailures = mutableListOf<String>()
            for (method in methodAttempts) {
                val args = buildMethodArgs(method, context)
                val result = try {
                    method.isAccessible = true
                    method.invoke(service, *args)
                } catch (error: Throwable) {
                    val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                    methodFailures += "${buildMethodSignature(method)} args=${describeArgs(args)} error=$detail"
                    null
                } as? ClipData

                if (result == null || result.itemCount <= 0) {
                    methodFailures += "${buildMethodSignature(method)} args=${describeArgs(args)} empty"
                    continue
                }

                val text = result.getItemAt(0).coerceToText(context)?.toString().orEmpty().trim()
                if (text.isBlank()) {
                    methodFailures += "${buildMethodSignature(method)} args=${describeArgs(args)} blank-text"
                    continue
                }
                return success(
                    text,
                    "Shizuku 已读取到系统剪贴板文本：${buildMethodSignature(method)} args=${describeArgs(args)}",
                )
            }

            if (!allowDeepProbe) {
                return failed("Shizuku 直接读取未命中，已进入静默退避")
            }

            val fallback = readTextViaBinderTransaction(binder, context)
            if (fallback.success && fallback.text.isNotBlank()) {
                return fallback
            }
            val remoteProcessFallback = readTextViaRemoteProcess(context)
            if (remoteProcessFallback.success && remoteProcessFallback.text.isNotBlank()) {
                return remoteProcessFallback
            }
            return failed(
                buildString {
                    append("Shizuku 未读到可发送的剪贴板文本；方法尝试：")
                    append(if (methodFailures.isEmpty()) "无" else methodFailures.joinToString(" ; "))
                    append("；事务兜底：")
                    append(fallback.detail)
                    append("；远程进程兜底：")
                    append(remoteProcessFallback.detail)
                },
            )
        } finally {
            probeInFlight = false
        }
    }

    private fun failed(detail: String, log: Boolean = true, recordFailureTime: Boolean = true): ShizukuClipboardReadResult {
        if (log) {
            Log.w(TAG, detail)
        }
        if (recordFailureTime) {
            lastFailureAtMs = System.currentTimeMillis()
        }
        lastFailureDetail = detail
        return ShizukuClipboardReadResult(false, "", detail)
    }

    private fun success(text: String, detail: String): ShizukuClipboardReadResult {
        Log.d(TAG, detail + if (text.isNotBlank()) " textLength=${text.length}" else "")
        lastFailureAtMs = 0L
        lastFailureDetail = ""
        return ShizukuClipboardReadResult(true, text, detail)
    }

    private fun createClipboardServiceProxy(binder: IBinder): Any? {
        val wrapper = ShizukuBinderWrapper(binder)
        val stubClass = runCatching { Class.forName(STUB_CLASS_NAME) }.getOrElse { error ->
            Log.w(TAG, "加载 IClipboard.Stub 失败：${error.message ?: error.javaClass.simpleName}")
            return null
        }
        val asInterface = runCatching {
            stubClass.getDeclaredMethod("asInterface", IBinder::class.java)
        }.getOrElse { error ->
            Log.w(TAG, "查找 IClipboard.Stub.asInterface 失败：${error.message ?: error.javaClass.simpleName}")
            return null
        }
        return runCatching {
            asInterface.isAccessible = true
            asInterface.invoke(null, wrapper)
        }.onFailure { error ->
            Log.w(TAG, "调用 IClipboard.Stub.asInterface 失败：${error.message ?: error.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun logBinderShape(binder: IBinder) {
        val descriptor = runCatching { binder.interfaceDescriptor }.getOrElse { error ->
            "读取失败:${error.javaClass.simpleName}:${error.message.orEmpty()}"
        }
        val ping = runCatching { binder.pingBinder() }.getOrDefault(false)
        val alive = runCatching { binder.isBinderAlive }.getOrDefault(false)
        val methods = binder.javaClass.methods
            .asSequence()
            .filter {
                it.name.contains("transact", ignoreCase = true) ||
                    it.name.contains("descriptor", ignoreCase = true) ||
                    it.name.contains("interface", ignoreCase = true)
            }
            .map(::buildMethodSignature)
            .distinct()
            .take(16)
            .toList()
        Log.w(
            TAG,
            "binderClass=${binder.javaClass.name} descriptor=$descriptor ping=$ping alive=$alive binderMethods=${if (methods.isEmpty()) "无" else methods.joinToString(" | ")}",
        )
    }

    private fun logServiceShape(service: Any) {
        val className = service.javaClass.name
        val interfaces = service.javaClass.interfaces.joinToString { it.name }.ifBlank { "无" }
        val superClass = service.javaClass.superclass?.name ?: "无"
        val binderClass = runCatching {
            service.javaClass.methods.firstOrNull { it.name == "asBinder" }?.let { method ->
                method.isAccessible = true
                (method.invoke(service) as? IBinder)?.javaClass?.name
            }
        }.getOrNull() ?: "无"
        val interfaceClass = runCatching { Class.forName(INTERFACE_CLASS_NAME) }.getOrNull()
        val methods = buildList {
            addAll(service.javaClass.methods.toList())
            addAll(service.javaClass.declaredMethods.toList())
            service.javaClass.interfaces.forEach { iface ->
                addAll(iface.methods.toList())
                addAll(iface.declaredMethods.toList())
            }
            interfaceClass?.let { iface ->
                addAll(iface.methods.toList())
                addAll(iface.declaredMethods.toList())
            }
        }
            .map(::buildMethodSignature)
            .distinct()
            .filter {
                it.contains("Clip", ignoreCase = true) ||
                    it.contains("Primary", ignoreCase = true) ||
                    it.contains("clipboard", ignoreCase = true)
            }
            .take(48)
        Log.w(
            TAG,
            "serviceClass=$className superClass=$superClass interfaces=$interfaces interfaceClass=${interfaceClass?.name ?: "无"} binder=$binderClass methodHints=${if (methods.isEmpty()) "无" else methods.joinToString(" | ")}",
        )
    }

    private fun findGetPrimaryClipMethods(service: Any): List<Method> {
        val interfaceClass = runCatching { Class.forName(INTERFACE_CLASS_NAME) }.getOrNull()
        val candidates = buildList {
            addAll(service.javaClass.methods.toList())
            addAll(service.javaClass.declaredMethods.toList())
            if (interfaceClass != null) {
                addAll(interfaceClass.methods.toList())
                addAll(interfaceClass.declaredMethods.toList())
            }
            service.javaClass.interfaces.forEach { iface ->
                addAll(iface.methods.toList())
                addAll(iface.declaredMethods.toList())
            }
        }
        return candidates
            .asSequence()
            .filter {
                it.name == "getPrimaryClip" ||
                    it.name == "getPrimaryClipAsPackage" ||
                    it.name == "getUserPrimaryClip" ||
                    it.name == "getStashPrimaryClip" ||
                    it.name.contains("PrimaryClip", ignoreCase = true)
            }
            .filter { ClipData::class.java.isAssignableFrom(it.returnType) }
            .sortedWith(
                compareBy<Method> { methodNamePriority(it.name) }
                    .thenBy { methodPenaltyScore(it) },
            )
            .toList()
    }

    private fun methodNamePriority(name: String): Int = METHOD_NAME_PRIORITY[name] ?: 10

    private fun methodPenaltyScore(method: Method): Int {
        return method.parameterTypes.fold(0) { total, type ->
            total + when {
                type == String::class.java -> 0
                type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType -> 1
                type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType -> 2
                type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType -> 2
                type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType -> 3
                type == Double::class.javaPrimitiveType || type == Double::class.javaObjectType -> 3
                type == Short::class.javaPrimitiveType || type == Short::class.javaObjectType -> 3
                type == Byte::class.javaPrimitiveType || type == Byte::class.javaObjectType -> 3
                type == Char::class.javaPrimitiveType || type == Char::class.javaObjectType -> 3
                else -> 8
            }
        }
    }

    private fun buildMethodArgs(method: Method, context: Context): Array<Any?> {
        val opPackageName = runCatching { context.opPackageName }.getOrDefault(context.packageName)
        val attributionTag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.attributionTag else null
        val userId = resolveUserId(context)
        val deviceId = resolveDeviceId(context)
        var stringIndex = 0
        var intIndex = 0
        return method.parameterTypes.map { type ->
            when {
                type == String::class.java -> {
                    val value = when (stringIndex++) {
                        0 -> opPackageName
                        1 -> attributionTag
                        else -> null
                    }
                    value
                }
                type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType -> {
                    val value = when (intIndex++) {
                        0 -> userId
                        1 -> deviceId
                        else -> 0
                    }
                    value
                }
                type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType -> 0L
                type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType -> false
                type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType -> 0f
                type == Double::class.javaPrimitiveType || type == Double::class.javaObjectType -> 0.0
                type == Short::class.javaPrimitiveType || type == Short::class.javaObjectType -> 0.toShort()
                type == Byte::class.javaPrimitiveType || type == Byte::class.javaObjectType -> 0.toByte()
                type == Char::class.javaPrimitiveType || type == Char::class.javaObjectType -> 0.toChar()
                else -> null
            }
        }.toTypedArray()
    }

    private fun readTextViaBinderTransaction(binder: IBinder, context: Context): ShizukuClipboardReadResult {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            val wrapper = ShizukuBinderWrapper(binder)
            val opPackageName = runCatching { context.opPackageName }.getOrDefault(context.packageName)
            val attributionTag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.attributionTag else null
            val userId = resolveUserId(context)
            val deviceId = resolveDeviceId(context)
            data.writeInterfaceToken(INTERFACE_CLASS_NAME)
            data.writeString(opPackageName)
            data.writeString(attributionTag)
            data.writeInt(userId)
            data.writeInt(deviceId)
            val transacted = wrapper.transact(TRANSACTION_GET_PRIMARY_CLIP, data, reply, 0)
            if (!transacted) {
                return failed("Binder 事务兜底失败：transact 返回 false")
            }
            reply.readException()
            val clip = reply.readTypedObject(ClipData.CREATOR)
            if (clip == null || clip.itemCount <= 0) {
                return success("", "Binder 事务兜底已执行，但当前没有可读取的剪贴板内容")
            }
            val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty().trim()
            if (text.isBlank()) {
                return success("", "Binder 事务兜底读取到的剪贴板不是可发送的纯文本")
            }
            return success(
                text,
                "Binder 事务兜底已读取到系统剪贴板文本：code=$TRANSACTION_GET_PRIMARY_CLIP args=${describeArgs(arrayOf(opPackageName, attributionTag, userId, deviceId))}",
            )
        } catch (error: Throwable) {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            failed("Binder 事务兜底失败：code=$TRANSACTION_GET_PRIMARY_CLIP error=$detail")
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun readTextViaRemoteProcess(context: Context): ShizukuClipboardReadResult {
        val attempts = listOf(
            RemoteProbeAttempt("shell-uid+pkg", shellUser = "2000", packageArg = "com.android.shell"),
            RemoteProbeAttempt("shell-uid+empty", shellUser = "2000", packageArg = "__EMPTY__"),
            RemoteProbeAttempt("shell-uid+null", shellUser = "2000", packageArg = "__NULL__"),
            RemoteProbeAttempt("root+shell-pkg", shellUser = null, packageArg = "com.android.shell"),
            RemoteProbeAttempt("root+null", shellUser = null, packageArg = "__NULL__"),
        )
        val details = mutableListOf<String>()
        for (attempt in attempts) {
            val process = createRemoteProbeProcess(context, attempt)
            if (process == null) {
                details += "${attempt.label}=create-failed"
                continue
            }
            val result = tryReadRemoteProcessResult(process, attempt)
            details += "${attempt.label}=${result.detail}"
            if (result.success && result.text.isNotBlank()) {
                return result
            }
        }
        return failed("远程进程兜底失败：${details.joinToString(" ; ")}")
    }

    private fun tryReadRemoteProcessResult(
        process: ShizukuRemoteProcess,
        attempt: RemoteProbeAttempt,
    ): ShizukuClipboardReadResult {
        return try {
            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText).trim()
            val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
            when {
                stdout.startsWith("TEXT::") -> {
                    val text = stdout.removePrefix("TEXT::").trim()
                    if (text.isBlank()) {
                        success("", "远程进程 ${attempt.label} 读取到空白文本")
                    } else {
                        success(text, "远程进程 ${attempt.label} 已读取到系统剪贴板文本：exit=$exitCode")
                    }
                }
                stdout.startsWith("EMPTY::") -> success("", "远程进程 ${attempt.label} 已执行，但当前没有可读取的剪贴板内容：$stdout")
                stdout.startsWith("ERROR::") -> failed("远程进程 ${attempt.label} 失败：$stdout${stderr.takeIf { it.isNotBlank() }?.let { " stderr=$it" }.orEmpty()}")
                stderr.isNotBlank() -> failed("远程进程 ${attempt.label} 失败：exit=$exitCode stderr=$stderr stdout=$stdout")
                stdout.isNotBlank() -> failed("远程进程 ${attempt.label} 返回未知结果：exit=$exitCode stdout=$stdout")
                else -> failed("远程进程 ${attempt.label} 失败：exit=$exitCode 未返回任何输出")
            }
        } catch (error: Throwable) {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            failed("远程进程 ${attempt.label} 失败：$detail")
        } finally {
            runCatching { process.destroy() }
        }
    }

    private fun createRemoteProbeProcess(
        context: Context,
        attempt: RemoteProbeAttempt,
    ): ShizukuRemoteProcess? {
        val userId = resolveUserId(context)
        val deviceId = resolveDeviceId(context)
        val probeCommand = "CLASSPATH='${context.packageCodePath}' app_process /system/bin ${ShizukuClipboardProbe::class.java.name} '${attempt.packageArg}' '$userId' '$deviceId'"
        val shellCommand = if (attempt.shellUser != null) {
            "su ${attempt.shellUser} -c \"$probeCommand\""
        } else {
            probeCommand
        }
        val command = arrayOf(
            "/system/bin/sh",
            "-c",
            shellCommand,
        )
        val newProcess = runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
        }.getOrElse { error ->
            Log.w(TAG, "查找 Shizuku.newProcess 失败：${error.message ?: error.javaClass.simpleName}")
            return null
        }
        return runCatching {
            newProcess.isAccessible = true
            @Suppress("SpreadOperator")
            newProcess.invoke(null, command, null, null) as? ShizukuRemoteProcess
        }.onFailure { error ->
            Log.w(TAG, "调用 Shizuku.newProcess 失败：${error.message ?: error.javaClass.simpleName}")
        }.getOrNull()
    }

    private data class RemoteProbeAttempt(
        val label: String,
        val shellUser: String?,
        val packageArg: String,
    )

    private fun resolveUserId(context: Context): Int {
        return runCatching {
            Context::class.java.getMethod("getUserId").invoke(context) as? Int
        }.getOrNull() ?: runCatching {
            runCatching { Process.myUid() / 100000 }.getOrDefault(0)
        }.getOrDefault(0)
    }

    private fun resolveDeviceId(context: Context): Int {
        return runCatching {
            Context::class.java.getMethod("getDeviceId").invoke(context) as? Int
        }.getOrNull() ?: 0
    }

    private fun listAvailableGetPrimaryClipSignatures(service: Any): String {
        val interfaceClass = runCatching { Class.forName(INTERFACE_CLASS_NAME) }.getOrNull()
        val signatures = buildList {
            addAll(service.javaClass.methods.toList())
            addAll(service.javaClass.declaredMethods.toList())
            if (interfaceClass != null) {
                addAll(interfaceClass.methods.toList())
                addAll(interfaceClass.declaredMethods.toList())
            }
            service.javaClass.interfaces.forEach { iface ->
                addAll(iface.methods.toList())
                addAll(iface.declaredMethods.toList())
            }
            service.javaClass.superclass?.let { superClass ->
                addAll(superClass.methods.toList())
                addAll(superClass.declaredMethods.toList())
            }
        }.asSequence()
            .filter {
                it.name.contains("PrimaryClip", ignoreCase = true) ||
                    it.name.contains("Clipboard", ignoreCase = true)
            }
            .map(::buildMethodSignature)
            .distinct()
            .toList()
        return if (signatures.isEmpty()) "无" else signatures.joinToString(" | ")
    }

    private fun buildMethodSignature(method: Method): String {
        val params = method.parameterTypes.joinToString(", ") { it.simpleName }
        val modifiers = buildString {
            if (Modifier.isStatic(method.modifiers)) append(" static")
            if (Modifier.isAbstract(method.modifiers)) append(" abstract")
        }.trim()
        val throwsRemote = method.exceptionTypes.any { RemoteException::class.java.isAssignableFrom(it) }
        val suffix = buildString {
            if (modifiers.isNotBlank()) append(" [$modifiers]")
            if (throwsRemote) append(" throws RemoteException")
        }
        return "${method.returnType.simpleName} ${method.name}($params)$suffix"
    }

    private fun describeArgs(args: Array<Any?>): String {
        return args.joinToString(prefix = "[", postfix = "]") { value ->
            when (value) {
                null -> "null"
                is String -> "\"$value\""
                else -> value.toString()
            }
        }
    }
}
