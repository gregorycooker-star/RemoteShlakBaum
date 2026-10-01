package com.remoteshlakbaum

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import okhttp3.*

class RemoteService : Service() {
    private val client = OkHttpClient()
    private var ws: WebSocket? = null

    override fun onCreate() {
        super.onCreate()
        val channel = "remote_shlakbaum"
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "Remote ShlakBaum", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(7, NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Remote ShlakBaum")
            .setContentText("Удалённое управление активно")
            .setOngoing(true).build())
        connect()
    }

    private fun connect() {
        val prefs = getSharedPreferences("remote", MODE_PRIVATE)
        val topic = prefs.getString("topic", null) ?: return
        val request = Request.Builder().url("wss://ntfy.sh/$topic/ws?since=all").build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = Regex("\\\"message\\\":\\\"([^\\\"]*)").find(text)?.groupValues?.getOrNull(1) ?: return
                val parts = msg.split("|")
                if (parts.size != 3 || parts[0] != "CALL") return
                val pin = prefs.getString("pin", "1234") ?: "1234"
                if (parts[1] != pin) return
                val index = parts[2].toIntOrNull() ?: return
                val nums = prefs.getStringSet("numbers", emptySet())!!.toList().sorted()
                if (index !in nums.indices) return
                placeCall(nums[index])
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.os.Handler(mainLooper).postDelayed({ connect() }, 5000)
            }
        })
    }

    private fun placeCall(number: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { startActivity(intent) } catch (_: Exception) { }
    }

    override fun onDestroy() { ws?.close(1000, "stop"); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
