package com.example.movenetandroid

import android.app.Activity
import android.content.Intent
import android.os.Bundle


class SplashScreen : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        val callMain = Intent(this@SplashScreen, MainActivity::class.java)

        val timer: Thread = object : Thread() {
            override fun run() {
                try {
                    sleep(1500) // 1.5 seconds
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                } finally {
                    startActivity(callMain)
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    finish()
                }
            }
        }
        timer.start()
    }
}