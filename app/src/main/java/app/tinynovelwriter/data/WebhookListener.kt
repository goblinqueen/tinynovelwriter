package app.tinynovelwriter.data

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * A tiny HTTP listener on 127.0.0.1 for FolderSync's webhooks. The user configures one
 * webhook per event, each POSTing to `http://127.0.0.1:8765/<event>` (successful, failed,
 * cancelled, starting). The path is all the app uses: FolderSync's JSON body reports
 * `transferredFiles` as 0 even when files were uploaded. Runs while the process is alive, not just while visible: the sync
 * trigger pauses the app and the webhook arrives after that. Anything else on the phone
 * could call it too, which at worst changes the sync status shown.
 */
object WebhookListener {
    const val PORT = 8765
    private const val TAG = "TNW-Webhook"
    @Volatile private var server: ServerSocket? = null

    /** [onEvent] is called on a background thread with the event name (the last path segment). */
    fun start(onEvent: (event: String) -> Unit) {
        if (server != null) return
        val socket = try {
            ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"))
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't listen on $PORT: $e")
            return
        }
        server = socket
        thread(name = "webhook-listener", isDaemon = true) {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { handle(client, onEvent) }
            }
        }
    }

    private fun handle(client: Socket, onEvent: (String) -> Unit) = client.use {
        try {
            client.soTimeout = 5000
            val input = BufferedInputStream(client.getInputStream())
            val head = readHead(input)
            val length = Regex("(?im)^content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
            // Drain the body (unused) so FolderSync sees a clean exchange.
            ByteArray(minOf(length, 65_536)).also { buf ->
                var read = 0
                while (read < buf.size) { val n = input.read(buf, read, buf.size - read); if (n < 0) break; read += n }
            }
            client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())

            val path = head.lineSequence().firstOrNull()?.split(" ")?.getOrNull(1) ?: return@use
            val event = path.substringBefore('?').trim('/').substringAfterLast('/')
            Log.i(TAG, "Webhook '$event'")
            onEvent(event)
        } catch (e: Exception) {
            Log.w(TAG, "Bad request: $e")
        }
    }

    /** Request line and headers, up to the blank line. */
    private fun readHead(input: BufferedInputStream): String {
        val out = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) break
            out.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A || (last4 and 0xFFFF) == 0x0A0A || out.size() > 16_384) break
        }
        return out.toString(Charsets.UTF_8.name()).trimEnd()
    }
}
