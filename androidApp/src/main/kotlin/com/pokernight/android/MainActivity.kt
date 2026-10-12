package com.pokernight.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.pokernight.android.ui.PokerApp
import com.pokernight.android.ui.PokerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PokerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PokerApp(viewModel)
        }
    }
}
