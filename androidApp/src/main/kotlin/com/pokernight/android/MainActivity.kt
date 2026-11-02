package com.pokernight.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.pokernight.android.network.PokerHostService
import com.pokernight.android.ui.PokerApp
import com.pokernight.android.ui.PokerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PokerViewModel by viewModels()
    private var hostServiceBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            val localBinder = binder as PokerHostService.LocalBinder
            viewModel.bindHostService(localBinder.service)
            hostServiceBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            hostServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind to Host Service so the server lifecycle survives screen-locks
        val intent = Intent(this, PokerHostService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            PokerApp(viewModel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (hostServiceBound) {
            unbindService(connection)
            hostServiceBound = false
        }
    }
}
