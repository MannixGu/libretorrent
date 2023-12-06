package com.xm.cine.unit

object StringBuilderUtil {
    @JvmStatic
    fun StringBuilder.appendCompateJson(vararg objArr: Any?) {
        for (obj in objArr) {
            if (obj == null) {
                append('"').append('"')
            } else if (obj is String) {
                val str = obj.toString()
                if (str.startsWith("{")) {
                    append(str)
                } else {
                    append('"').append(str).append('"')
                }
            } else {
                append(obj)
            }
            append(",")
        }

        setLength(length - 1)
    }

    @JvmStatic
    inline fun <T> StringBuilder.appendInterator(iterator: Iterator<T>, head: String = "") {
        append("$head[")
        iterator.forEach {
            append(it)
            append(",")
        }
        deleteCharAt(length - 1)
        append("]")
    }

    @JvmStatic
    infix fun <T> StringBuilder.add(mes: T) {
        if (mes == null) {
            append("null")
            return
        }

        when (mes) {
            is ByteArray -> {
                appendInterator(mes.iterator())
            }

            is ShortArray -> {
                appendInterator(mes.iterator())
            }

            is CharArray -> {
                appendInterator(mes.iterator())
            }

            is IntArray -> {
                appendInterator(mes.iterator())
            }

            is LongArray -> {
                appendInterator(mes.iterator())
            }

            is FloatArray -> {
                appendInterator(mes.iterator())
            }

            is DoubleArray -> {
                appendInterator(mes.iterator())
            }

            is Array<*> -> {
                appendInterator(mes.iterator())
            }

            is Map<*, *> -> {
                append("{")
                mes.forEach {
                    var (k, v) = it
                    append("${k}:${v}")
                    append(",")
                }
                deleteCharAt(length - 1)
                append("}")
            }

            is Throwable -> {
                append(mes.stackTraceToString())
            }

            else -> {
                append(mes)
            }
        }
    }

    @JvmStatic
    fun parseString(vararg objs: Any?): String {
        val sb = StringBuilder()
        for (mes in objs) {
            sb add mes
            sb.append(" ")
        }
        sb.deleteCharAt(sb.length - 1)
        return sb.toString()
    }

}