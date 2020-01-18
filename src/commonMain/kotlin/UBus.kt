package ubus

enum class ParamType {
    STRING, INT, BOOL;

    companion object {
        fun nameOf(type: String): ParamType {
            return when (type) {
                STRING.name -> STRING
                INT.name -> INT
                BOOL.name -> BOOL
                else -> STRING
            }
        }
    }
}

data class MethodParam(val name: String, val type: ParamType)

data class Method(val name: String, var params: List<MethodParam>)