package com.pokernight.android.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pokernight.android.MainActivity
import com.pokernight.shared.domain.betting.BettingState
import com.pokernight.shared.model.Session
import com.pokernight.shared.multiplayer.server.PokerHostServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PokerHostService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var server: PokerHostServer? = null
    private var localIp: String = "127.0.0.1"

    private val _isHosting = MutableStateFlow(false)
    val isHosting: StateFlow<Boolean> = _isHosting.asStateFlow()

    private val _connectedCount = MutableStateFlow(0)
    val connectedCount: StateFlow<Int> = _connectedCount.asStateFlow()

    private val _hostUrl = MutableStateFlow("")
    val hostUrl: StateFlow<String> = _hostUrl.asStateFlow()

    private val _pairingSecret = MutableStateFlow("")
    val pairingSecret: StateFlow<String> = _pairingSecret.asStateFlow()

    private val _roomId = MutableStateFlow("")
    val roomId: StateFlow<String> = _roomId.asStateFlow()

    inner class LocalBinder : Binder() {
        val service: PokerHostService get() = this@PokerHostService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun startHosting(port: Int = 8080) {
        if (server != null) return

        localIp = NetworkUtils.getLocalIpAddress()
        val newServer = PokerHostServer(port = port)
        this.server = newServer

        _roomId.value = newServer.roomId
        _pairingSecret.value = newServer.pairingSecret
        _hostUrl.value = "http://$localIp:$port"
        _isHosting.value = true

        newServer.start()

        serviceScope.launch {
            newServer.connectedCount.collect { count ->
                _connectedCount.value = count
                updateNotification()
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    fun stopHosting() {
        server?.stop()
        server = null
        _isHosting.value = false
        _connectedCount.value = 0
        _hostUrl.value = ""
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun updateGameState(session: Session, bettingState: BettingState?) {
        server?.updateGameState(session, bettingState)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Poker Night Host Room",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows status of the local Poker Night multiplayer server"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Poker Night Host Active")
            .setContentText("${_roomId.value} | ${_hostUrl.value} | ${_connectedCount.value} connected")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        if (!_isHosting.value) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        server = null
    }

    companion object {
        private const val CHANNEL_ID = "poker_host_channel"
        private const val NOTIFICATION_ID = 2001
    }
}
