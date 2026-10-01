package com.remoteshlakbaum

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("remote", MODE_PRIVATE) }
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!prefs.contains("topic")) prefs.edit().putString("topic", "rsb-" + UUID.randomUUID().toString()).apply()
        val permissions = mutableListOf(Manifest.permission.CALL_PHONE)
        if (android.os.Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 10)
        showPinGate()
    }

    private fun showPinGate() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40,80,40,40) }
        val title = TextView(this).apply { text = "Remote ShlakBaum"; textSize = 26f }
        val pin = EditText(this).apply { hint = "PIN"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        val button = Button(this).apply { text = "ВОЙТИ" }
        root.addView(title); root.addView(pin); root.addView(button); setContentView(root)
        button.setOnClickListener {
            val saved = prefs.getString("pin", "1234") ?: "1234"
            if (pin.text.toString() == saved) showMain() else Toast.makeText(this,"Неверный PIN",Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMain() {
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,48,32,48) }
        scroll.addView(list); setContentView(scroll)
        list.addView(TextView(this).apply { text="Номера для звонка"; textSize=24f })
        val add = Button(this).apply { text="+ ДОБАВИТЬ НОМЕР" }
        val changePin = Button(this).apply { text="ИЗМЕНИТЬ PIN" }
        val remote = Button(this).apply { text="ВКЛЮЧИТЬ УДАЛЁННЫЙ РЕЖИМ" }
        val link = Button(this).apply { text="ПОКАЗАТЬ ССЫЛКУ ДЛЯ iPHONE" }
        list.addView(add); list.addView(changePin); list.addView(remote); list.addView(link)
        add.setOnClickListener { addNumberDialog() }
        changePin.setOnClickListener { changePinDialog() }
        remote.setOnClickListener {
            ContextCompat.startForegroundService(this, Intent(this, RemoteService::class.java))
            Toast.makeText(this,"Удалённый режим включён",Toast.LENGTH_SHORT).show()
        }
        link.setOnClickListener { showControllerInfo() }
        renderNumbers()
    }

    private fun numbers() = prefs.getStringSet("numbers", emptySet())!!.toMutableList().sorted().toMutableList()
    private fun saveNumbers(v: List<String>) = prefs.edit().putStringSet("numbers", v.toSet()).apply()

    private fun renderNumbers() {
        while(list.childCount > 5) list.removeViewAt(5)
        numbers().forEach { number ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            val call=Button(this).apply { text="📞 $number"; layoutParams=LinearLayout.LayoutParams(0,-2,1f) }
            val del=Button(this).apply { text="✕" }
            call.setOnClickListener { call(number) }
            del.setOnClickListener { val n=numbers(); n.remove(number); saveNumbers(n); renderNumbers() }
            row.addView(call); row.addView(del); list.addView(row)
        }
    }

    private fun addNumberDialog() {
        val input=EditText(this).apply { hint="+407xxxxxxxx"; inputType=InputType.TYPE_CLASS_PHONE }
        android.app.AlertDialog.Builder(this).setTitle("Добавить номер").setView(input)
            .setPositiveButton("Сохранить") { _,_-> val n=input.text.toString().trim(); if(n.isNotBlank()){ val a=numbers(); a.add(n); saveNumbers(a); renderNumbers()} }
            .setNegativeButton("Отмена",null).show()
    }

    private fun changePinDialog() {
        val input=EditText(this).apply { hint="Новый PIN (минимум 4 цифры)"; inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        android.app.AlertDialog.Builder(this).setTitle("Изменить PIN").setView(input)
            .setPositiveButton("Сохранить") { _,_-> if(input.text.length>=4) prefs.edit().putString("pin",input.text.toString()).apply() }
            .setNegativeButton("Отмена",null).show()
    }

    private fun showControllerInfo() {
        val topic = prefs.getString("topic", "") ?: ""
        val text = "Код устройства:\n$topic\n\nНа iPhone откройте страницу контроллера Remote ShlakBaum и введите этот код, PIN и выберите номер."
        android.app.AlertDialog.Builder(this).setTitle("Подключение iPhone").setMessage(text).setPositiveButton("OK",null).show()
    }

    private fun call(number:String) {
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED)
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}")))
        else ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CALL_PHONE),10)
    }
}
