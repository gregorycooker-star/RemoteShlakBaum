package com.remoteshlakbaum

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class RemoteService : Service() {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
    private val handler by lazy { android.os.Handler(mainLooper) }
    private val endpoint = "https://zkygfwcsarcgjkjbnhwo.supabase.co/functions/v1/remote-command"
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val channel = "remote_shlakbaum"
        if (android.os.Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Remote ShlakBaum", NotificationManager.IMPORTANCE_LOW))
        startForeground(7, NotificationCompat.Builder(this, channel).setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle("Remote ShlakBaum").setContentText("Удалённое управление активно").setOngoing(true).build())
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RemoteShlakBaum:poll").apply {
            setReferenceCounted(false)
            acquire()
        }
        poll()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun poll() {
        val prefs = getSharedPreferences("remote", MODE_PRIVATE)
        val deviceId = prefs.getString("topic", null) ?: run { schedule(); return }
        val pin = prefs.getString("pin", "1234") ?: "1234"
        val routeKey = sha256("$deviceId:$pin")
        val body = JSONObject().put("action", "poll").put("routeKey", routeKey).toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(endpoint).post(body).build()
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

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
