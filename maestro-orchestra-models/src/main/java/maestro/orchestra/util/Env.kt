package maestro.orchestra.util

import maestro.js.JsEngine
import maestro.orchestra.DefineVariablesCommand
import maestro.orchestra.MaestroCommand

object Env {

    fun String.evaluateScripts(jsEngine: JsEngine): String {
        val result = "(?<!\\\\)\\\$\\{([^\$]*)}".toRegex()
            .replace(this) { match ->
                val script = match.groups[1]?.value ?: ""

                if (script.isNotBlank()) {
                    jsEngine.evaluateScript(script).toString()
                } else {
                    ""
                }
            }

        return result
            .replace("\\\\\\\$\\{([^\$]*)}".toRegex()) { match ->
                match.value.substringAfter('\\')
            }
    }

    fun List<MaestroCommand>.withEnv(env: Map<String, String>): List<MaestroCommand> {
        if (env.isEmpty()) {
            return this
        }

        return listOf(MaestroCommand(DefineVariablesCommand(env))) + this
    }

}
