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

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("remote", MODE_PRIVATE) }
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 10)
        }
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
        val heading = TextView(this).apply { text="Номера для звонка"; textSize=24f }
        val add = Button(this).apply { text="+ ДОБАВИТЬ НОМЕР" }
        val changePin = Button(this).apply { text="ИЗМЕНИТЬ PIN" }
        list.addView(heading); list.addView(add); list.addView(changePin)
        add.setOnClickListener { addNumberDialog() }
        changePin.setOnClickListener { changePinDialog() }
        renderNumbers()
    }

    private fun numbers(): MutableList<String> = prefs.getStringSet("numbers", emptySet())!!.toMutableList().sorted().toMutableList()
    private fun saveNumbers(v: List<String>) = prefs.edit().putStringSet("numbers", v.toSet()).apply()

    private fun renderNumbers() {
        while(list.childCount > 3) list.removeViewAt(3)
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
        val input=EditText(this).apply { hint="Новый PIN"; inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        android.app.AlertDialog.Builder(this).setTitle("Изменить PIN").setView(input)
            .setPositiveButton("Сохранить") { _,_-> if(input.text.length>=4) prefs.edit().putString("pin",input.text.toString()).apply() }
            .setNegativeButton("Отмена",null).show()
    }

    private fun call(number:String) {
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}")))
        } else ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CALL_PHONE),10)
    }
}
