import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path as KotlinPath
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

class CustomServer internal constructor(private val socket: ServerSocket) : Closeable {
    constructor(port: Int = 80, timeout: Duration = 5000.milliseconds) : this(ServerSocket(port)) {
        socket.soTimeout = timeout.toInt(DurationUnit.MILLISECONDS)
    }

    private lateinit var job: Job
    var isClosed = false
        private set

    private var numOfConnections = AtomicInteger(0)

    suspend fun start() = coroutineScope {
        if (isClosed) return@coroutineScope

        job = launch {
            outer@ while (isActive) {
                println("Waiting client on port ${socket.localPort}")
                val deferredClient = async { socket.acceptCancellable() }

                val clientConnection = deferredClient.await() ?: break@outer
                numOfConnections.incrementAndGet()
                println("Connected: ${clientConnection.inetAddress}:${clientConnection.port}")
                println("Number of connected clients: $numOfConnections")
                receiveClient(clientConnection)
            }
            println("End loop")
        }
    }

    private fun CoroutineScope.receiveClient(client: Socket) = launch {
        BufferedReader(InputStreamReader(client.inputStream)).use { inputStream ->
            PrintWriter(OutputStreamWriter(client.outputStream)).use { outputStream ->
                try {
                    val path = readFileContent(client, inputStream)
                    println("File $path from ${client.inetAddress.hostAddress}:${client.port} asked")
                    if (path.isRegularFile()) {
                        println("Response to ${client.inetAddress.hostAddress}:${client.port}")
                        sendResponse(outputStream, path.readText())
                    } else {
                        println("File $path asked by ${client.inetAddress.hostAddress}:${client.port} undefined")
                        sendError(outputStream, "Not Found", 404)
                    }
                } catch (e: IOException) {
                    println("Request from ${client.inetAddress.hostAddress}:${client.port}: submitted error ${e.message}")
                    sendError(outputStream, "Server Error", 500)
                }
            }
        }

        delay(100)
        println("${client.inetAddress}:${client.port} disconnected")
        client.close()
        numOfConnections.decrementAndGet()
    }

    private fun CoroutineScope.readFileContent(client: Socket, inputStream: BufferedReader): Path {
        if (!isActive || client.isClosed) throw IOException("Connection closed")
        val clientMethodLine = inputStream.readLine() ?: throw IOException("No messages received")

        return clientMethodLine.parseRequest()
    }

    private fun sendError(outputStream: PrintWriter, message: String, errorCode: Int) {
        outputStream.println(
            """
HTTP/1.1 $errorCode $message
            """.trimIndent()
        )
    }

    private fun sendResponse(outputStream: PrintWriter, content: String, contentType: String = "text/plain") {
        outputStream.println(
            """
HTTP/1.1 200 OK
Content-Type: $contentType

$content
            """.trimIndent()
        )
    }

    override fun close() {
        isClosed = true
        runBlocking {
            job.cancel()
            socket.close()
        }
    }

    fun stop() = close()
}

private fun ServerSocket.acceptCancellable() = try {
    accept()
} catch (e: IOException) {
    null
}

private fun String.parseRequest(): Path {
    val trim = trimIndent()
    if (trim.isEmpty()) {
        throw IOException("Invalid request")
    }

    val split = trim.split("""\s+""".toRegex())
    if (!split.first().equals("GET", ignoreCase = true)) {
        throw IOException("Method invalid: ${split.first()}")
    }

    if (!split.last().startsWith("HTTP/", ignoreCase = true)) {
        throw IOException("Scheme invalid: ${split.last()}")
    }

    if (split.last().substringAfter("HTTP/") != "1.1") {
        throw IOException("HTTP version invalid: ${split.last().substringAfter("HTTP/")}")
    }

    return KotlinPath(split.subList(1, split.lastIndex).joinToString(" "))
}
