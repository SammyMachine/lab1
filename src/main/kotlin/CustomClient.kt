import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

class CustomClient internal constructor(private val socket: Socket) : AutoCloseable {
    constructor(
        serverIp: String = "127.0.0.1",
        serverPort: Int = 80,
        sleepTimeout: Duration = 1000.milliseconds
    ) : this(Socket(serverIp, serverPort)) {
        socket.soTimeout = sleepTimeout.toInt(DurationUnit.MILLISECONDS)
    }

    override fun close() {
        socket.close()
    }

    fun receiveContent(filePath: Path) {
        if (socket.isClosed) return
        val inStream = BufferedReader(InputStreamReader(socket.inputStream))
        val outStream = PrintWriter(OutputStreamWriter(socket.outputStream), true)

        outStream.println(constructRequest(socket.inetAddress.hostAddress, filePath))

        val (code, message) = inStream.readLine().receiveResponse()
        if (code != 200) {
            logError(code, message)
        } else {
            logSuccess("Content successfully received!")

            inStream.useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) println(line)
                    else return@forEach
                }
            }
        }

        if (!socket.isClosed) socket.close()
        logSuccess("Connection closed!")
    }
}

private fun String.receiveResponse(): Pair<Int, String> {
    val split = split("\\s+".toRegex())
    return Pair(split[1].toInt(), split.subList(2, split.size).joinToString(" "))
}

private fun constructRequest(host: String, filepath: Path) = """
GET $filepath HTTP/1.1
Host: $host
Connection: Keep-Alive
""".trimIndent()

private fun logError(code: Int, message: String) {
    println(" ### Error ### Fail while loading content. Error code: $code. Message: $message")
}

private fun logSuccess(message: String) {
    println(" ### Success ### $message")
}
