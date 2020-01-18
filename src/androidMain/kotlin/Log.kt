import platform.android.ANDROID_LOG_DEBUG
import platform.android.ANDROID_LOG_ERROR
import platform.android.__android_log_print

actual object Log {
    val tag = "UBUS"
    actual fun debug(msg: String) {
        __android_log_print(ANDROID_LOG_DEBUG.toInt(), tag, msg)
    }

    actual fun error(msg: String) {
        __android_log_print(ANDROID_LOG_ERROR.toInt(), tag, msg)
    }
}