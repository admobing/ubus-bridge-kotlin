package ubus

import Log
import kotlinx.cinterop.*
import platform.posix.sleep
import kotlin.native.concurrent.*

@SharedImmutable
private val ubusCtx = AtomicReference<CPointer<ubus_context>?>(null)

@SharedImmutable
internal var configRef: COpaquePointer? = null

@SharedImmutable
var ubusListener: Listener? = null

@ThreadLocal
object Ubus {

    private var config: Config? = null

    fun run(path: String? = null): Future<Unit> {
        configRef = DetachedObjectGraph { Config(path) }.asCPointer()
        return Worker.start().execute(TransferMode.SAFE, {
            configRef
        }) {
            config = DetachedObjectGraph<Config>(it).attach()
            uloop_init()

            val timer = nativeHeap.alloc<uloop_timeout> {
                cb = staticCFunction { timer ->
                    initRuntimeIfNeeded()

                    registe()
                    uloop_timeout_set(timer, 2000)
                }
            }
            uloop_timeout_set(timer.ptr, 2000)

            start()
            Log.debug("uloop start")
            uloop_run()
        }
    }

    private fun start() {
        Log.debug("ubus connect ${config!!.server} ...")
        val connect = ubus_connect(config!!.server)
        if (connect == null) {
            restart()
            return
        }

        Log.debug("ubus connect success")
        connect.pointed.connection_lost = staticCFunction { _ ->
            initRuntimeIfNeeded()

            Log.debug("ubus disconnect")

            config!!.subscribers.forEach {
                it.onLeave()
            }
            config!!.providers.forEach {
                it.onLeave()
            }

            restart()
        }

        ubus_add_uloop(connect)
        ubusCtx.value = connect

        registe(connect)
    }

    private fun restart() {
        stop()
        sleep(1u) // delay 1s
        start()
    }

    fun stop() {
        ubusCtx.value?.let {
            ubus_free(it)
            ubusCtx.value = null
        }
    }

    private fun registe(connect: CPointer<ubus_context>? = null) {
        config!!.subscribers.forEach {
            registerSubscriberInner(it, connect)
        }

        config!!.providers.forEach {
            registerProviderInner(it, connect)
        }
    }

    private fun lookup(name: String): Int {
        return memScoped {
            val id = cValue<UIntVar>().getPointer(this)
            if (ubus_lookup_id(ubusCtx.value, name, id) != 0) {
                0
            } else {
                id[0].toInt()
            }
        }

    }

    private fun registerProviderInner(provider: Provider, ctx: CPointer<ubus_context>?): Int {
        if (provider.isRegistered()) {
            return 0
        }

        val mCtx = if (ctx == null) {
            ubusCtx.value
        } else {
            ctx
        }

        if (mCtx == null) {
            error("register provider failed. ctx not init")
            return -1
        }

        val ret = ubus_add_object(mCtx, provider.toUbus())
        if (ret != 0) {
            error("add  provider ${provider.pName} err. ${ubus_strerror(ret)?.toKString()}")
        } else {
            provider.onRegiste()
        }

        return ret
    }

    private fun registerSubscriberInner(subscriber: Subscriber, ctx: CPointer<ubus_context>?): Int {
        if (subscriber.isSubscribed()) {
            return 0
        }

        val mCtx = if (ctx == null) {
            ubusCtx.value
        } else {
            ctx
        }

        if (mCtx == null) {
            error("register subscriber failed. ctx not init")
            return -1
        }

        if (!subscriber.isRegistered()) {
            var ret = ubus_register_subscriber(mCtx, subscriber.toUbus())
            if (ret != 0) {
                error("${subscriber.sName} subscribe err. ${ubus_strerror(ret)?.toKString()}")
                return ret
            }

            subscriber.onRegiste()
        }

        val id = lookup(subscriber.sName)
        if (id == 0) {
            error("${subscriber.sName} subscribe err. lookup failed ")
            return -1
        }

        val ret = ubus_subscribe(mCtx, subscriber.toUbus(), id.toUInt())
        if (ret != 0) {
            error("${subscriber.sName} subscribe err. subscrib failed ${ubus_strerror(ret)?.toKString()}")
        } else {
            subscriber.onSubscribe()
        }

        return ret
    }

    fun registerProvider(provider: Provider, ctx: CPointer<ubus_context>? = null): Int {
        if (config == null) {
            config = DetachedObjectGraph<Config>(configRef).attach()
        }
        config!!.providers.add(provider)
        return registerProviderInner(provider, ctx)
    }

    fun registerSubscriber(subscriber: Subscriber, ctx: CPointer<ubus_context>? = null): Int {
        if (config == null) {
            config = DetachedObjectGraph<Config>(configRef).attach()
        }
        config!!.subscribers.add(subscriber)

        return registerSubscriberInner(subscriber, ctx)
    }

    fun invoke(obj: String, method: String, payload: String): Long {
        val ctx = ubusCtx.value
        if (ctx == null) {
            Log.debug("rsp err ctx not ready")
            return 0
        }

        return Invoker.invoke(ctx, obj, method, payload)
    }

    fun rsp(reqId: Long, payload: String) {
        val ctx = ubusCtx.value
        if (ctx == null) {
            Log.debug("rsp err ctx not ready")
            return
        }
        if (config == null) {
            config = DetachedObjectGraph<Config>(configRef).attach()
        }
        config!!.providers.forEach {
            if (it.rsp(ctx, reqId, payload)) {
                return
            }
        }
    }

    fun findProviderById(oid: UInt): Provider? {
        if (config == null) {
            config = DetachedObjectGraph<Config>(configRef).attach()
        }
        return config!!.providers.find { it.getUbusId() == oid }
    }

    fun findSubscriberById(oid: UInt): Subscriber? {
        if (config == null) {
            config = DetachedObjectGraph<Config>(configRef).attach()
        }
        return config!!.subscribers.find { it.getUbusId() == oid }
    }

}