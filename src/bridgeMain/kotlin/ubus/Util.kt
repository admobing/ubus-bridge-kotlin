package ubus

import kotlinx.cinterop.CValuesRef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.content
import kotlinx.serialization.json.int

fun buildUbusParam(payload: String, buf: CValuesRef<blob_buf>) {
    Json(JsonConfiguration.Stable).parseJson(payload).jsonArray.forEach {
        val param = it.jsonObject.content
        val name = param["name"]!!.content
        val type = ParamType.nameOf(param["type"]!!.content)
        when (type) {
            ParamType.STRING -> {
                blobmsg_add_string(buf, name, param["value"]!!.content)
            }
            ParamType.INT -> {
                blobmsg_add_u32(buf, name, param["value"]!!.int.toUInt())
            }
            ParamType.BOOL -> {
                blobmsg_add_u8(buf, name, if ("true".equals(param["value"]!!.content)) 1u else 0u)
            }
        }
    }
}