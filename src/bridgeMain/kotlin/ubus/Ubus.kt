package ubus

import Log.debug
import Log.error
import kotlinx.cinterop.*
import platform.posix.sleep
import kotlin.native.concurrent.*

@SharedImmutable
private val ctx = nativeHeap.alloc<ubus_context>().ptr

@SharedImmutable
private val config = AtomicReference<Config?>(null)

@SharedImmutable
private val ctxAlive = AtomicInt(0)

@ThreadLocal
internal object Ubus {
    val reconnTimer = nativeHeap.alloc<uloop_timeout> {
        this.cb = staticCFunction { timer ->
            initRuntimeIfNeeded()

            if (ubus_reconnect(ctx, getConfig().server) == 0) {
                ubus_add_uloop(ctx)
            } else {
                setReconnTimer()
            }
        }
    }.ptr

    fun run(path: String? = null, listener: Listener): Future<Unit> {
        setConfig(Config(path, listener))

        return Worker.start().execute(TransferMode.SAFE, {
        }) {
            uloop_init()
            setRegisteTimer()
            start()
            debug("uloop start")
//            uloop_run()
        }
    }

    private fun start() {
        val config = getConfig()
        while (true) {
            debug("ubus connect ${config.server} ...")
            if (ubus_connect_ctx(ctx, config.server) == 0) {
                break
            }
            sleep(1u)
        }

        debug("ubus connect success")
        ctx.pointed.connection_lost = staticCFunction { _ ->
            initRuntimeIfNeeded()
            ctxAlive.value = 0

            debug("ubus disconnect")
            val config = getConfig()
            config.subscribers.forEach {
                it.onLeave()
            }
            config.providers.forEach {
                it.onLeave()
            }
            config.eventListeners.forEach {
                it.onLeave()
            }

            setReconnTimer()
        }

        ubus_add_uloop(ctx)
        ctxAlive.value = 1
        registe()
    }

    fun setRegisteTimer() {
        val timer = nativeHeap.alloc<uloop_timeout> {
            cb = staticCFunction { timer ->
                initRuntimeIfNeeded()

                registe()
                uloop_timeout_set(timer, 2000)
            }
        }.ptr
        uloop_timeout_set(timer, 2000)
    }

    fun setReconnTimer() {
        uloop_timeout_set(reconnTimer, 1000)
    }

    private fun registe() {
        val config = getConfig()
        config.subscribers.forEach {
            registerSubscriberInner(it)
        }
        config.providers.forEach {
            registerProviderInner(it)
        }
        config.eventListeners.forEach {
            registerEventListenerInner(it)
        }
    }

    private fun lookup(name: String): UInt {
        return memScoped {
            val id = alloc<UIntVar>().ptr
            if (ubus_lookup_id(ctx, name, id) != 0) {
                0u
            } else {
                id.pointed.value
            }
        }

    }

    private fun registerProviderInner(provider: Provider): Int {
        if (provider.isRegistered()) {
            return 0
        }

        if (ctxAlive.value == 0) {
            error("register provider failed. ctx not init")
            return -1
        }

        val ret = ubus_add_object(ctx, provider.toUbus())
        if (ret != 0) {
            error("add  provider ${provider.pName} err. ${ubus_strerror(ret)?.toKString()}")
        } else {
            provider.onRegiste()
        }

        return ret
    }

    private fun registerSubscriberInner(subscriber: Subscriber): Int {
        if (subscriber.isSubscribed()) {
            return 0
        }

        if (ctxAlive.value == 0) {
            error("register subscriber failed. ctx not init")
            return -1
        }

        if (!subscriber.isRegistered()) {
            var ret = ubus_register_subscriber(ctx, subscriber.toUbus())
            if (ret != 0) {
                error("${subscriber.sName} subscribe err. ${ubus_strerror(ret)?.toKString()}")
                return ret
            }

            subscriber.onRegiste()
        }

        val id = lookup(subscriber.sName)
        if (id == 0u) {
            error("${subscriber.sName} subscribe err. lookup failed ")
            return -1
        }

        val ret = ubus_subscribe(ctx, subscriber.toUbus(), id)
        if (ret != 0) {
            error("${subscriber.sName} subscribe err. subscrib failed ${ubus_strerror(ret)?.toKString()}")
        } else {
            subscriber.onSubscribe()
        }

        return ret
    }

    private fun registerEventListenerInner(eventListener: EventListener): Int {
        if (eventListener.isRegistered()) {
            return 0
        }
        if (ctxAlive.value == 0) {
            error("register event lstener failed. ctx not init")
            return -1
        }

        val ret = ubus_register_event_handler(ctx, eventListener.toBus(), eventListener.path)
        if (ret == 0) {
            eventListener.onRegiste()
        } else {
            debug("register event lstener failed. ${ubus_strerror(ret)?.toKString()}")
        }

        return ret
    }

    fun registerProvider(provider: Provider): Int {
        val config = getConfig().copy()
        config.providers.add(provider)
        setConfig(config)

        return registerProviderInner(provider)
    }

    fun registerSubscriber(subscriber: Subscriber): Int {
        val config = getConfig().copy()
        config.subscribers.add(subscriber)
        setConfig(config)

        return registerSubscriberInner(subscriber)
    }

    fun registerEventListener(eventListener: EventListener): Int {
        val config = getConfig().copy()
        config.eventListeners.add(eventListener)
        setConfig(config)

        return registerEventListenerInner(eventListener)
    }

    fun invoke(obj: String, method: String, payload: String): Long {
        if (ctxAlive.value == 0) {
            debug("rsp err ctx not ready")
            return 0
        }

        return Invoker.invoke(ctx, obj, method, payload)
    }

    fun rsp(reqId: Long, payload: String) {
        if (ctxAlive.value == 0) {
            debug("rsp err ctx not ready")
            return
        }
        
        Provider.rsp(ctx, reqId, payload)
    }

    fun notify(provider: String, type: String, payload: String) {
        if (ctxAlive.value == 0) {
            debug("notify err ctx not ready")
            return
        }
        val config = getConfig()
        config.providers.find { it.pName.equals(provider) }?.notify(ctx, type, payload)
    }

    fun sendEvent(event: String, payload: String) {
        if (ctxAlive.value == 0) {
            debug("send event err ctx not ready")
            return
        }

        debug(payload)

        memScoped {
            val buf = alloc<blob_buf>().ptr
            blob_buf_init(buf, 0)
            if (!blobmsg_add_json_from_string(buf, payload)) {
                debug("send event err, failed to parse payload ")
                return
            }
            ubus_send_event(ctx, event, buf.pointed.head)
        }


    }

    internal fun findProviderById(oid: UInt): Provider? {
        val config = getConfig()
        return config.providers.find { it.getUbusId() == oid }
    }

    internal fun findSubscriberById(oid: UInt): Subscriber? {
        val config = getConfig()
        return config.subscribers.find { it.getUbusId() == oid }
    }

    private fun setConfig(cfg: Config) {
        config.value = cfg.freeze()
    }

}

fun getUbusListener(): Listener {
    return getConfig().listener
}

internal fun getConfig(): Config {
    return config.value!!
}