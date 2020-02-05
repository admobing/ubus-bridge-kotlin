import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.parseList
import ubus.Method

@ImplicitReflectionSerializer
internal fun parseMethodSig(payload: String): List<Method> {
    return Json(JsonConfiguration.Stable).parseList(payload)
}