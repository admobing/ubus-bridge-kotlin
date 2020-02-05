import kotlin.native.concurrent.Worker

actual object Log {
    actual fun debug(msg: String) {
        println("worker ${Worker.current.name}: ${msg}")
    }

    actual fun error(msg: String) {
        println("worker ${Worker.current.name}: ${msg}")
    }
}