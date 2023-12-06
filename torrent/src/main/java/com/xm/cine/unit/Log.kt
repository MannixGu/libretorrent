package com.xm.cine.unit

import com.xm.cine.unit.StringBuilderUtil.parseString
import java.io.FileOutputStream
import java.io.OutputStream

interface Logger {
    fun w(msg: String)
    fun i(msg: String)
    fun e(msg: String)
    fun d(msg: String)
}

class AndroidLog(val TAG: String) : Logger {
    override fun w(msg: String) {
        android.util.Log.w(TAG, msg)
    }

    override fun i(msg: String) {
        android.util.Log.i(TAG, msg)
    }

    override fun e(msg: String) {
        android.util.Log.e(TAG, msg)
    }

    override fun d(msg: String) {
        android.util.Log.d(TAG, msg)
    }
}

object Log {
    private const val TAG = "LOG"

    private val logger: Logger

    init {
        logger = AndroidLog(TAG)
    }

    fun log(): Logger {
        return logger
    }

    @JvmStatic
    fun w(vararg objs: Any?) {
        log().w(parseString(*objs))
    }

    @JvmStatic
    fun i(vararg objs: Any?) {
        log().i(parseString(*objs))
    }

    @JvmStatic
    fun e(vararg objs: Any?) {
        log().e(parseString(*objs))
    }

    @JvmStatic
    fun d(vararg objs: Any?) {
        log().d(parseString(*objs))
    }
}