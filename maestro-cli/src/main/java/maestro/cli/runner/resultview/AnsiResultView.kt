/*
 *
 *  Copyright (c) 2022 mobile.dev inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package maestro.cli.runner.resultview

import io.ktor.util.encodeBase64
import maestro.cli.runner.CommandState
import maestro.cli.runner.CommandStatus
import org.fusesource.jansi.Ansi

class AnsiResultView(
    private val prompt: String? = null
): ResultView {

    private val startTimestamp = System.currentTimeMillis()

    private val frames = mutableListOf<Frame>()

    private var previousFrame: String? = null
    
    init {
        println(Ansi.ansi().eraseScreen())
    }

    override fun setState(state: UiState) {
        when (state) {
            is UiState.Running -> renderRunningState(state)
            is UiState.Error -> renderErrorState(state)
        }
    }

    fun getFrames(): List<Frame> {
        return frames.toList()
    }

    private fun renderErrorState(state: UiState.Error) {
        renderFrame {
            fgRed()
            render(state.message)
            render("\n")
        }
    }

    private fun renderRunningState(state: UiState.Running) = renderFrame {
        state.device?.let {
            render("Running on ${state.device.description}\n")
        }
        render("\n")
        if (state.initCommands.isNotEmpty()) {
            render(" ║\n")
            render(" ║  > Init Flow\n")
            render(" ║\n")
            renderCommands(state.initCommands)
        }
        render(" ║\n")
        render(" ║  > Flow\n")
        render(" ║\n")
        renderCommands(state.commands)
        render(" ║\n")
        renderPrompt()
    }

    private fun Ansi.renderPrompt() {
        prompt?.let {
            render(" ║\n")
            render(" ║  $prompt\n")
        }
    }

    private fun Ansi.renderCommands(
        commands: List<CommandState>,
        indent: Int = 0,
    ) {
        val statusColumnWidth = 3
        commands
            .filter { it.command.asCommand()?.visible() ?: true }
            .forEach {
                val statusSymbol = status(it.status)
                fgDefault()
                render(" ║    ")
                repeat(indent) {
                    render("  ")
                }
                render(statusSymbol)
                render(String(CharArray(statusColumnWidth - statusSymbol.length) { ' ' }))
                render(
                    it.command.description()
                        .replace("(?<!\\\\)\\\$\\{.*}".toRegex()) { match ->
                            "@|cyan ${match.value} |@"
                        }
                )

                if (it.status == CommandStatus.SKIPPED) {
                    render(" (skipped)")
                } else if (it.numberOfRuns != null) {
                    val timesWord = if (it.numberOfRuns == 1) "time" else "times"
                    render(" (completed ${it.numberOfRuns} $timesWord)")
                }

                render("\n")

                val expand = it.status in setOf(CommandStatus.RUNNING, CommandStatus.FAILED) &&
                    (it.subCommands?.any { subCommand -> subCommand.status != CommandStatus.PENDING } ?: false)

                if (expand) {
                    it.subCommands?.let { subCommands ->
                        renderCommands(subCommands, indent + 1)
                    }
                }
            }
    }

    private fun status(status: CommandStatus): String {
        return when (status) {
            CommandStatus.COMPLETED -> "✅"
            CommandStatus.FAILED -> "❌"
            CommandStatus.RUNNING -> "⏳"
            CommandStatus.PENDING -> "\uD83D\uDD32"
            CommandStatus.SKIPPED -> "⚪️"
        }
    }

    private fun renderFrame(block: Ansi.() -> Any) {
        renderFrame(StringBuilder().apply {
            val ansi = Ansi().cursor(0, 0)
            ansi.block()
            append(ansi)
        }.toString())
    }

    private fun renderFrame(frame: String) {
        // Clear previous frame
        previousFrame?.let { previousFrame ->
            val lines = previousFrame.lines()
            val height = lines.size
            val width = lines.maxOf { it.length }
            Ansi.ansi().let { ansi ->
                ansi.cursor(0, 0)
                repeat(height) {
                    ansi.render(" ".repeat(width))
                    ansi.render("\n")
                }
                ansi.cursor(0, 0)
                println(ansi)
            }
        }
        print(frame)
        frames.add(createFrame(frame))
        previousFrame = frame
    }

    private fun createFrame(frame: String): Frame {
        val content = frame.encodeBase64()
        return Frame(System.currentTimeMillis() - startTimestamp, content)
    }

    data class Frame(val timestamp: Long, val content: String)
}
