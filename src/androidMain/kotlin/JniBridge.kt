@file:Suppress("unused", "MemberVisibilityCanBePrivate")

import kotlinx.cinterop.*
import platform.android.*

data class JniClass(val jclass: jclass)
data class JniObject(val jobject: jobject)
data class JniMethod(val jmethod: jmethodID)

fun asJniClass(jclass: jclass?) = if (jclass != null) JniClass(jclass) else null
fun asJniObject(jobject: jobject?) = if (jobject != null) JniObject(jobject) else null
fun asJniMethod(jmethodID: jmethodID?) = if (jmethodID != null) JniMethod(jmethodID) else null

@ExperimentalUnsignedTypes
class JniBridge(val env: CPointer<JNIEnvVar>) {
    private val innerEnv = env.pointed.pointed!!
    private val fNewStringUTF = innerEnv.NewStringUTF!!
    private val fGetStringUTFChars = innerEnv.GetStringUTFChars!!
    private val fReleaseStringUTFChars = innerEnv.ReleaseStringUTFChars!!
    private val fFindClass = innerEnv.FindClass!!
    private val fGetMethodID = innerEnv.GetMethodID!!
    private val fGetStaticMethodID = innerEnv.GetStaticMethodID!!
    private val fCallVoidMethodA = innerEnv.CallVoidMethodA!!
    private val fCallObjectMethodA = innerEnv.CallObjectMethodA!!
    private val fCallStaticVoidMethodA = innerEnv.CallStaticVoidMethodA!!
    private val fCallStaticObjectMethodA = innerEnv.CallStaticObjectMethodA!!
    private val fExceptionCheck = innerEnv.ExceptionCheck!!
    private val fExceptionDescribe = innerEnv.ExceptionDescribe!!
    private val fExceptionClear = innerEnv.ExceptionClear!!
    private val fGetArrayLength = innerEnv.GetArrayLength!!
    private val fGetObjectArrayElement = innerEnv.GetObjectArrayElement!!
    private val fNewObjectArray = innerEnv.NewObjectArray!!
    private val fSetObjectArrayElement = innerEnv.SetObjectArrayElement!!
    private val fDeleteLocalRef = innerEnv.DeleteLocalRef!!
    private val fGetObjectClass = innerEnv.GetObjectClass!!

    val fPushLocalFrame = innerEnv.PushLocalFrame!!
    val fPopLocalFrame = innerEnv.PopLocalFrame!!

    private fun check() {
        if (fExceptionCheck(env) != 0.toUByte()) {
            fExceptionDescribe(env)
            fExceptionClear(env)
            throw Error("JVM exception thrown")
        }
    }

    private fun toJString(string: String) = memScoped {
        val result = asJniObject(fNewStringUTF(env, string.cstr.ptr))
        check()
        result
    }

    private fun toKString(string: jstring) = memScoped {
        val isCopy = alloc<jbooleanVar>()
        val chars = fGetStringUTFChars(env, string, isCopy.ptr)
        var ret: String? = null
        if (chars != null) {
            ret = chars.toKString()
            fReleaseStringUTFChars(env, string, chars)
        }
        ret
    }

    private fun stringArrayToJStringArray(array: Array<String>): jobjectArray? = memScoped {
        val jStringCls = findClass("java/lang/String")
        val jStringInit = "".asJString()
        val len = array.size
        val jarr = fNewObjectArray(env, len, jStringCls?.jclass, jStringInit)
        for (i in array.indices) {
            fSetObjectArrayElement(env, jarr, i, array[i].asJString())
        }
        fDeleteLocalRef(env, jStringInit)
        fDeleteLocalRef(env, jStringCls?.jclass)
        jarr
    }

    private fun jstringArrayToStringArray(array: jobjectArray): Array<String> {
        val arr = mutableListOf<String>()
        val len = fGetArrayLength(env, array)
        for (i in 0 until len) {
            val jstr = fGetObjectArrayElement(env, array, i)
            arr.add(jstr?.asKString() ?: "")
        }
        return arr.toTypedArray()
    }

    private fun toJValues(arguments: Array<out Any?>, scope: MemScope): CPointer<jvalue>? {
        val result = scope.allocArray<jvalue>(arguments.size)
        arguments.mapIndexed { index, it ->
            when (it) {
                null -> result[index].l = null
                is JniObject -> result[index].l = it.jobject
                is String -> result[index].l = toJString(it)?.jobject
                is Int -> result[index].i = it
                is Long -> result[index].j = it
                is Byte -> result[index].b = it
                is Short -> result[index].s = it
                is Double -> result[index].d = it
                is Float -> result[index].f = it
                is Char -> result[index].c = it.toInt().toUShort()
                is Boolean -> result[index].z = (if (it) JNI_TRUE else JNI_FALSE).toUByte()
                else -> throw Error("Unsupported conversion for ${it::class.simpleName}")
            }
        }
        return result
    }

    fun findClass(name: String) = memScoped {
        val result = asJniClass(fFindClass(env, name.cstr.ptr))
        check()
        result
    }

    fun getObjectClass(obj: jobject) = memScoped {
        val result = asJniClass(fGetObjectClass(env, obj))
        check()
        result
    }

    fun getMethodID(clazz: JniClass?, name: String, signature: String) = memScoped {
        val result = asJniMethod(fGetMethodID(env, clazz?.jclass, name.cstr.ptr, signature.cstr.ptr))
        check()
        result
    }
    fun getMethodID(clazz: jclass?, name: String, signature: String) = memScoped {
        val result = asJniMethod(fGetMethodID(env, clazz, name.cstr.ptr, signature.cstr.ptr))
        check()
        result
    }

    fun getStaticMethodID(clazz: JniClass?, name: String, signature: String) = memScoped {
        val result = asJniMethod(fGetStaticMethodID(env, clazz?.jclass, name.cstr.ptr, signature.cstr.ptr))
        check()
        result
    }
    fun getStaticMethodID(clazz: jclass?, name: String, signature: String) = memScoped {
        val result = asJniMethod(fGetStaticMethodID(env, clazz, name.cstr.ptr, signature.cstr.ptr))
        check()
        result
    }

    fun callStaticVoidMethod(receiver: JniClass?, method: JniMethod, vararg arguments: Any?) = memScoped {
        fCallStaticVoidMethodA(env, receiver?.jclass, method.jmethod, toJValues(arguments, this@memScoped))
        check()
    }
    fun callStaticVoidMethod(receiver: JniClass?, method: JniMethod, arguments: Array<Any>?) = memScoped {
        fCallStaticVoidMethodA(env, receiver?.jclass, method.jmethod, arguments?.asJValues(this@memScoped))
        check()
    }
    fun callStaticVoidMethod(receiver: JniClass?, method: JniMethod, arguments: CPointer<jvalue>?) = memScoped {
        fCallStaticVoidMethodA(env, receiver?.jclass, method.jmethod, arguments)
        check()
    }
    fun callStaticVoidMethod(receiver: jclass?, method: JniMethod, vararg arguments: Any?) = memScoped {
        fCallStaticVoidMethodA(env, receiver, method.jmethod, toJValues(arguments, this@memScoped))
        check()
    }
    fun callStaticVoidMethod(receiver: jclass?, method: JniMethod, arguments: Array<Any>?) = memScoped {
        fCallStaticVoidMethodA(env, receiver, method.jmethod, arguments?.asJValues(this@memScoped))
        check()
    }
    fun callStaticVoidMethod(receiver: jclass?, method: JniMethod, arguments: CPointer<jvalue>?) = memScoped {
        fCallStaticVoidMethodA(env, receiver, method.jmethod, arguments)
        check()
    }

    fun callVoidMethod(receiver: JniObject?, method: JniMethod, vararg arguments: Any?) = memScoped {
        fCallVoidMethodA(env, receiver?.jobject, method.jmethod, toJValues(arguments, this@memScoped))
        check()
    }

    fun callVoidMethod(receiver: JniObject?, method: JniMethod, arguments: Array<Any>?) = memScoped {
        fCallVoidMethodA(env, receiver?.jobject, method.jmethod, arguments?.asJValues(this@memScoped))
        check()
    }

    fun callVoidMethod(receiver: JniObject?, method: JniMethod, arguments: CPointer<jvalue>?) = memScoped {
        fCallVoidMethodA(env, receiver?.jobject, method.jmethod, arguments)
        check()
    }

    fun callVoidMethod(receiver: jobject?, method: JniMethod, vararg arguments: Any?) = memScoped {
        fCallVoidMethodA(env, receiver, method.jmethod, toJValues(arguments, this@memScoped))
        check()
    }

    fun callVoidMethod(receiver: jobject?, method: JniMethod, arguments: Array<Any>?) = memScoped {
        fCallVoidMethodA(env, receiver, method.jmethod, arguments?.asJValues(this@memScoped))
        check()
    }

    fun callVoidMethod(receiver: jobject?, method: JniMethod, arguments: CPointer<jvalue>?) = memScoped {
        fCallVoidMethodA(env, receiver, method.jmethod, arguments)
        check()
    }

    fun callStaticObjectMethod(receiver: JniObject?, method: JniMethod, vararg arguments: Any?) = memScoped {
        val result = asJniObject(fCallStaticObjectMethodA(env, receiver?.jobject, method.jmethod, toJValues(arguments, this@memScoped)))
        check()
        result
    }
    fun callStaticObjectMethod(receiver: JniObject?, method: JniMethod, arguments: Array<Any>?) = memScoped {
        val result = asJniObject(fCallStaticObjectMethodA(env, receiver?.jobject, method.jmethod, arguments?.asJValues(this@memScoped)))
        check()
        result
    }
    fun callStaticObjectMethod(receiver: JniObject?, method: JniMethod, arguments: CPointer<jvalue>?) = memScoped {
        val result = asJniObject(fCallStaticObjectMethodA(env, receiver?.jobject, method.jmethod, arguments))
        check()
        result
    }
    fun callStaticObjectMethod(receiver: jclass?, method: JniMethod, vararg arguments: Any?) = memScoped {
        val result = asJniObject(fCallStaticObjectMethodA(env, receiver, method.jmethod, toJValues(arguments, this@memScoped)))
        check()
        result
    }
    fun callStaticObjectMethod(receiver: jclass?, method: JniMethod, arguments: Array<Any>?) = memScoped {
        val result = asJniObject(fCallStaticObjectMethodA(env, receiver, method.jmethod, arguments?.asJValues(this@memScoped)))
        check()
        result
    }
    fun callStaticObjectMethod(receiver: jclass?, method: JniMethod, arguments: CPointer<jvalue>?) = memScoped {
        val result = asJniObject(fCallStaticObjectMethodA(env, receiver, method.jmethod, arguments))
        check()
        result
    }

    fun callObjectMethod(receiver: JniObject?, method: JniMethod, vararg arguments: Any?) = memScoped {
        val result = asJniObject(fCallObjectMethodA(env, receiver?.jobject, method.jmethod, toJValues(arguments, this@memScoped)))
        check()
        result
    }

    fun callObjectMethod(receiver: JniObject?, method: JniMethod, arguments: Array<Any>?) = memScoped {
        val result = asJniObject(fCallObjectMethodA(env, receiver?.jobject, method.jmethod, arguments?.asJValues(this@memScoped)))
        check()
        result
    }

    fun callObjectMethod(receiver: JniObject?, method: JniMethod, arguments: CPointer<jvalue>?) = memScoped {
        val result = asJniObject(fCallObjectMethodA(env, receiver?.jobject, method.jmethod, arguments))
        check()
        result
    }

    fun callObjectMethod(receiver: jobject?, method: JniMethod, vararg arguments: Any?) = memScoped {
        val result = asJniObject(fCallObjectMethodA(env, receiver, method.jmethod, toJValues(arguments, this@memScoped)))
        check()
        result
    }
    fun callObjectMethod(receiver: jobject?, method: JniMethod, arguments: Array<Any>?) = memScoped {
        val result = asJniObject(fCallObjectMethodA(env, receiver, method.jmethod, arguments?.asJValues(this@memScoped)))
        check()
        result
    }
    fun callObjectMethod(receiver: jobject?, method: JniMethod, arguments: CPointer<jvalue>?) = memScoped {
        val result = asJniObject(fCallObjectMethodA(env, receiver, method.jmethod, arguments))
        check()
        result
    }

    inline fun <T> withLocalFrame(block: JniBridge.() -> T): T {
        if (fPushLocalFrame(env, 0) < 0)
            throw Error("Cannot push new local frame")
        try {
            return block()
        } finally {
            fPopLocalFrame(env, null)
        }
    }
    fun String.asJString() = toJString(this)!!.jobject
    fun jstring.asKString() = toKString(this)
    fun Array<*>.asJValues(scope: MemScope) = toJValues(this, scope)
    fun Array<String>.asJStringArray() = stringArrayToJStringArray(this)
    fun jobjectArray.asStringArray() = jstringArrayToStringArray(this)
}

@ExperimentalUnsignedTypes
inline fun <T> jniWith(env: CPointer<JNIEnvVar>, block: JniBridge.() -> T) = JniBridge(env).withLocalFrame(block)