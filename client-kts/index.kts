import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// ---------- Config ----------
object Config {
    private val env = mutableMapOf<String, String>()

    init {
        val envFile = File(".env")
        if (envFile.exists()) {
            envFile.readLines().forEach { line ->
                var sanitized = line.trim()

                if (sanitized.startsWith("\uFEFF")) {
                    sanitized = sanitized.substring(1)
                }

                if (sanitized.isNotEmpty() && !sanitized.startsWith("#") && sanitized.contains("=")) {
                    val parts = sanitized.split("=", limit = 2)
                    env[parts[0].trim()] = parts[1].trim()
                }
            }
        }
    }

    private fun get(key: String): String? = env[key] ?: System.getenv(key)

    val targetIp: String = get("TARGET_IP") ?: "127.0.0.1"
    val targetPort: Int = get("TARGET_PORT")?.toIntOrNull() ?: 41234
    val responseTimeoutMs: Long = get("RESPONSE_TIMEOUT_MS")?.toLongOrNull() ?: 3000L
    val probeIntervalMs: Long = get("PROBE_INTERVAL_MS")?.toLongOrNull() ?: 1000L
}

// 모든 상태 변경(연결 여부, 타이머 등)을 이 스레드 하나에서만 실행해서 레이스 컨디션을 없앤다.
val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

// scheduleAtFixedRate는 태스크에서 예외가 새면 이후 반복 실행이 조용히 멈춘다. 그걸 막기 위한 안전망.
fun safe(block: () -> Unit): Runnable = Runnable {
    try {
        block()
    } catch (e: Throwable) {
        println("[예기치 못한 오류]: ${e.message}")
    }
}

// ---------- UdpClient ----------
class UdpClient(targetIp: String, private val targetPort: Int) {
    private val socket = DatagramSocket()
    private val address = InetAddress.getByName(targetIp)
    private val messageListeners = mutableListOf<(String) -> Unit>()

    fun onMessage(listener: (String) -> Unit) {
        messageListeners.add(listener)
    }

    fun start() {
        thread {
            val buffer = ByteArray(255)
            while (true) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val response = String(packet.data, 0, packet.length)
                    println("[KTS Client] 보드(${packet.address}:${packet.port}) 응답 수신, $response")
                    scheduler.execute(safe {
                        messageListeners.forEach { it(response) }
                    })
                } catch (e: Exception) {
                    println("[소켓 에러]: ${e.message}")
                    break
                }
            }
        }
    }

    fun send(message: String) {
        val buffer = message.toByteArray()
        val packet = DatagramPacket(buffer, buffer.size, address, targetPort)
        socket.send(packet)
    }
}

// ---------- AngleSender ----------
class AngleSender(
    private val client: UdpClient,
    private val responseTimeoutMs: Long,
    private val onResponseTimeout: (String) -> Unit,
) {
    private val angles = arrayOf("0", "45", "90", "135", "180", "135", "90", "45")
    private val probeAngle = angles[0]
    private var currentIndex = 0
    private var responseTimeoutFuture: ScheduledFuture<*>? = null

    private fun clearResponseTimeout() {
        responseTimeoutFuture?.cancel(false)
        responseTimeoutFuture = null
    }

    fun notifyResponseReceived() {
        clearResponseTimeout()
    }

    private fun send(angleStr: String) {
        println("\n[KTS Client] 모터 각도 전송 명령, ${angleStr}도")

        try {
            client.send(angleStr)
        } catch (e: Exception) {
            println("[송신 실패]: ${e.message}")
            return
        }

        responseTimeoutFuture = scheduler.schedule(safe {
            responseTimeoutFuture = null
            println(
                "[응답 타임아웃] ${angleStr}도 명령에 대한 보드 응답이 ${responseTimeoutMs}ms 내에 없습니다. 하드웨어 연결을 확인하세요.",
            )
            onResponseTimeout(angleStr)
        }, responseTimeoutMs, TimeUnit.MILLISECONDS)
    }

    fun sendProbe() = send(probeAngle)

    fun sendAngleCommand() {
        send(angles[currentIndex])
        currentIndex = (currentIndex + 1) % angles.size
    }
}

// ---------- ConnectionManager ----------
class ConnectionManager(
    private val probeIntervalMs: Long,
    private val sendProbe: () -> Unit,
    private val sendCommand: () -> Unit,
) {
    private val mainIntervalMs = 2000L
    private var connected = false
    private var mainTask: ScheduledFuture<*>? = null
    private var probeTask: ScheduledFuture<*>? = null

    private fun stopProbing() {
        probeTask?.cancel(false)
        probeTask = null
    }

    fun startProbing() {
        if (probeTask != null) return
        println("[UDP PoC] 보드 연결 대기 중...")
        sendProbe()
        probeTask = scheduler.scheduleAtFixedRate(
            safe { sendProbe() },
            probeIntervalMs,
            probeIntervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    fun handleResponse() {
        if (connected) return
        connected = true
        println("[KTS Client] 보드 연결 확인됨. 명령 전송을 시작합니다.")
        stopProbing()
        mainTask = scheduler.scheduleAtFixedRate(
            safe { sendCommand() },
            mainIntervalMs,
            mainIntervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    fun handleTimeout() {
        if (!connected) return
        connected = false
        println("[KTS Client] 보드 응답 끊김. 명령 전송을 중단하고 재연결을 시도합니다.")
        mainTask?.cancel(false)
        mainTask = null
        startProbing()
    }
}

// ---------- Wiring ----------
println("Bemo PoC 서보 모터 제어 Kotlin UDP 클라이언트 구동 시작")
println("타겟 보드 IP: ${Config.targetIp}, 포트: ${Config.targetPort}")

val client = UdpClient(Config.targetIp, Config.targetPort)

lateinit var connection: ConnectionManager
val angleSender = AngleSender(client, Config.responseTimeoutMs) { connection.handleTimeout() }
connection = ConnectionManager(Config.probeIntervalMs, angleSender::sendProbe, angleSender::sendAngleCommand)

client.onMessage {
    angleSender.notifyResponseReceived()
    connection.handleResponse()
}

client.start()
scheduler.execute { connection.startProbing() }
