package com.pokernight.android

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pokernight.shared.Environment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView =
            TextView(this).apply {
                text = Environment.getBuildInfo()
                textSize = 18f
                setPadding(48, 48, 48, 48)
            }
        setContentView(textView)
    }
}
