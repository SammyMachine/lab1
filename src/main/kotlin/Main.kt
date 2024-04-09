import kotlinx.coroutines.*
import java.util.*
import kotlin.io.path.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    launchApp(arrayOf("both", "8080", "10000"))
}

fun launchApp(args: Array<String>) {
    if (args.isEmpty()) {
        println("\"server\", \"client\", or \"both\" expected in first arg")
        return
    }
    val launchType = args[0].uppercase(Locale.getDefault())
    runBlocking {
        when (launchType) {
            "SERVER" -> {
                val serverPort = args.getOrElse(1) { "80" }.toInt()
                if (serverPort !in 0..65535) {
                    println("Port $serverPort should be in range in 0..65535")
                    return@runBlocking
                }
                val terminationTime = args.getOrNull(2)?.toLong()?.milliseconds

                launch {
                    runServer(serverPort, terminationTime)
                }
            }

            "CLIENT" -> {
                if (args.size < 4) {
                    println("\"server ip\", \"port\", and \"file path\" expected")
                    return@runBlocking
                }

                val serverIp = args[1]
                val serverPort = args[2].toInt()
                if (serverPort !in 0..65535) {
                    println("Port $serverPort should be in range in 0..65535")
                    return@runBlocking
                }

                val filePath = args[3]

                launch {
                    val client = CustomClient(serverIp, serverPort)
                    client.receiveContent(Path(filePath))
                }
            }

            "BOTH" -> {
                val serverPort = args.getOrElse(1) { "80" }.toInt()
                if (serverPort !in 0..65535) {
                    println("Port $serverPort should be in range in 0..65535")
                    return@runBlocking
                }

                val modelTime = args.getOrNull(2)?.toLong()?.milliseconds

                launch {
                    runAll(serverPort, modelTime)
                }
            }

            else -> println("Wrong launch type: $launchType")
        }
    }
}

fun CoroutineScope.runServer(port: Int, terminationTime: Duration? = null): CustomServer {
    val server = CustomServer(port = port)
    launch {
        withContext(Dispatchers.IO) {
            server.start()
        }
    }
    launch {
        terminationTime?.let { disableAfter ->
            delay(disableAfter)
            server.stop()
        }
    }
    return server
}

fun CoroutineScope.runAll(port: Int = 80, modelTime: Duration? = 7.seconds) {
    var isServerRunning = { false }
    launch {
        val server = runServer(port, terminationTime = modelTime)
        isServerRunning = { !server.isClosed }
    }

    launch {
        while (!isServerRunning()) {
            delay(50)
        }

        while (isServerRunning()) {
            launch {
                try {
                    val client = CustomClient("127.0.0.1", port)
                    delay((500L..2000L).random())
                    client.receiveContent(randomPaths.random())
                } catch (_: Exception) {
                }
            }
            delay((100L..2000L).random())
        }
    }
}

private val randomPaths = arrayOf(
    Path("./src/main/resources/test.txt"),
    Path("./src/main/resources/test.html"),
    Path("./src/main/aboba.txt"),
)
