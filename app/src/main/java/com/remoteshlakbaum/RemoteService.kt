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
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RemoteService : Service() {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
    private val handler by lazy { android.os.Handler(mainLooper) }
    private val endpoint = "https://zkygfwcsarcgjkjbnhwo.supabase.co/functions/v1/remote-command"
    private val apiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpreWdmd2NzYXJjZ2pramJuaHdvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA4NjgxMTUsImV4cCI6MjEwNjQ0NDExNX0.2TK2Uj67TSUI6AQXLF9xXiiE5tszk6wSFjmpHNPb0Mc"

    override fun onCreate() {
        super.onCreate()
        val channel = "remote_shlakbaum"
        if (android.os.Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Remote ShlakBaum", NotificationManager.IMPORTANCE_LOW))
        startForeground(7, NotificationCompat.Builder(this, channel).setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle("Remote ShlakBaum").setContentText("Удалённое управление активно").setOngoing(true).build())
        poll()
    }

    private fun poll() {
        val prefs = getSharedPreferences("remote", MODE_PRIVATE)
        val deviceId = prefs.getString("topic", null) ?: return
        val body = JSONObject().put("action", "poll").put("deviceId", deviceId).toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(endpoint).header("apikey", apiKey).header("Authorization", "Bearer $apiKey").post(body).build()
        client.newCall(req).enqueue(object: Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { schedule() }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val json = runCatching { JSONObject(it.body?.string() ?: "{}") }.getOrNull()
                        val command = json?.optJSONObject("command")
                        if (command != null) {
                            val slot = command.optInt("slot", 0)
                            val nums = prefs.getStringSet("numbers", emptySet())!!.toList().sorted()
                            if (slot in 1..nums.size) placeCall(nums[slot - 1])
                        }
                    }
                }
                schedule()
            }
        })
    }

    private fun schedule() { handler.postDelayed({ poll() }, 3000) }

    private fun placeCall(number: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try { startActivity(intent) } catch (_: Exception) { }
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
