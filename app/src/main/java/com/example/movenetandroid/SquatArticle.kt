package com.example.movenetandroid

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SquatArticle : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_squat_article)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById<View>(R.id.main),
            OnApplyWindowInsetsListener { v: View?, insets: WindowInsetsCompat? ->
                val systemBars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
                v!!.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            })

        val backButton = findViewById<ImageView>(R.id.backButton)
        backButton.setOnClickListener(View.OnClickListener { v: View? ->
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out) //animation
        })

        val cameraIcon = findViewById<ImageView>(R.id.cameraIcon)
        cameraIcon?.setOnClickListener {
            val intent = Intent(this, PoseActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out) //animation
        }
    }
}
