package com.example.smartpot

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.smartpot.Constants.HC06_MAC_ADDRESS
import com.example.smartpot.Constants.HC06_UUID
import com.example.smartpot.Constants.NOTIFICATION_CHANNEL_ID
import com.example.smartpot.Constants.NOTIFICATION_ID
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


class MyForegroundService : Service(), ActivityResultRegistryOwner {
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var socket: BluetoothSocket? = null
    private lateinit var inputStream: InputStream
    private lateinit var workerThread: Thread
    private lateinit var readBuffer: ByteArray
    private var readBufferPosition: Int = 0
    private var outputSocketConnected = false

    @Volatile
    private var stopReader: Boolean = false
    var FINISH_COOKING = "16"
    var UPDATE_PROGRESS_BAR = "15"
    var TEMPERATURE_REACHED = "14"
    var POLLED_TEMPERATURE = "PT"
    var RECIPE_PAUSE = "10"
    var STOP_RECIPE = "17"
    var TRUE = "TRUE"

    companion object {
        private var drawableId = 0
        private var cookingRecipeName = ""
        private lateinit var outputStream: OutputStream
        var isSocketConnected = false
        var isServiceRunning = false
        var timerStartsImmediately = false
        var automaticCooking = false
        var isPaused = false
        var manualCooking = false
        var temperatureReached = false
        var recyclerViewPos = 0
        var desiredTemperature = 0

        fun setAutomaticRecipeVars(drawable: Int, pos: Int, name: String, startsImmediately: Boolean) {
            drawableId = drawable
            recyclerViewPos = pos
            cookingRecipeName = name
            automaticCooking = true
            timerStartsImmediately = startsImmediately
        }

        fun sendData(message: String) {
            try {
                val msg = (message + "\u0000").toByteArray(Charsets.UTF_8)
                Log.d("MyLog", "writing $msg")
                this.outputStream.write(msg)
            } catch (e: IOException) {
                Log.e("MyLog", "Error sending data: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun getActivityResultRegistry(): ActivityResultRegistry {
        return activityResultRegistry
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private inner class ServiceEchoReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("MyLog","Receive boroadcast ${intent.action}")
            when (intent.action) {
                "ping" -> {
                    if (socket != null && socket!!.isConnected) {
                        LocalBroadcastManager
                            .getInstance(this@MyForegroundService)
                            .sendBroadcastSync(Intent("pong"))
                    }
                }
                "DefaultNotification" -> {
                    updateToDefaultNotification()
                }
                "ManualCookingNotification" -> {
                    updateNotificationToManualCooking()
                }
            }

        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("MyLog", "Create notification channel")
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Bluetooth Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNotification()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        enableBluetooth()
        isServiceRunning = true
        val intentFilter = IntentFilter().apply {
            addAction("ping")
            addAction("DefaultNotification")
            addAction("ManualCookingNotification")
        }
        LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(ServiceEchoReceiver(), intentFilter)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext,
            1,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT
        )
        val alarmService = applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
        alarmService[AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000] =
            restartServicePendingIntent
        super.onTaskRemoved(rootIntent)
    }

    private fun showNotification()
    {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart pot connected")
            .setContentText("Disconnect it from the app once you are finished cooking")
            .setSmallIcon(R.drawable.smartpot)
            .setContentIntent(pendingIntent)

        startForeground(NOTIFICATION_ID, notification.build())
    }

    fun updateToDefaultNotification()
    {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updatedNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart pot connected")
            .setContentText("Disconnect it from the app once you are finished cooking")
            .setSmallIcon(R.drawable.smartpot)
            .setContentIntent(pendingIntent)

        notificationManager.notify(NOTIFICATION_ID, updatedNotification.build())
        startForeground(NOTIFICATION_ID, updatedNotification.build())
    }

    fun updateNotificationToBoiling(temperature: String)
    {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updatedNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart pot is making - $cookingRecipeName")
            .setContentText("Heating up $temperature°C/$desiredTemperature°C.")
            .setSmallIcon(R.drawable.smartpot)
            .setContentIntent(pendingIntent)

        notificationManager.notify(NOTIFICATION_ID, updatedNotification.build())
        startForeground(NOTIFICATION_ID, updatedNotification.build())
    }

    fun updateProgressNotification(progress: String) {
        val progressInt = progress.toIntOrNull() ?: return

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        val updatedNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart pot is making - $cookingRecipeName")
            .setContentText("$progressInt% done")
            .setSmallIcon(R.drawable.smartpot)
            .setContentIntent(pendingIntent)

        notificationManager.notify(NOTIFICATION_ID, updatedNotification.build())
        startForeground(NOTIFICATION_ID, updatedNotification.build())
    }

    fun updateNotificationToManualCooking() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updatedNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart pot is in manual mode")
            .setContentText("")
            .setSmallIcon(R.drawable.smartpot)
            .setContentIntent(pendingIntent)

        notificationManager.notify(NOTIFICATION_ID, updatedNotification.build())
        startForeground(NOTIFICATION_ID, updatedNotification.build())
    }

    fun updateNotificationToPause()
    {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updatedNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart pot is paused")
            .setContentText("")
            .setSmallIcon(R.drawable.smartpot)
            .setContentIntent(pendingIntent)

        notificationManager.notify(NOTIFICATION_ID, updatedNotification.build())
        startForeground(NOTIFICATION_ID, updatedNotification.build())
    }


    fun finishCooking(showFinishedNotification: Boolean) {
        if(showFinishedNotification)
        {
            MyNotificationManager().showNotification(
                applicationContext,
                cookingRecipeName,
                "Have finished cooking!",
                drawableId
            )
        }
        desiredTemperature = 0
        automaticCooking = false
        temperatureReached = false
        timerStartsImmediately = false
        updateToDefaultNotification()
    }

    fun temperatureReached() {
        temperatureReached = true
    }

    private fun enableBluetooth() {
        Log.d("MyLog","is socket connected $isSocketConnected")
        if (socket == null || socket != null && !socket!!.isConnected || !isSocketConnected) {
            connectSocket()
        }
    }

    private fun connectSocket() {
        val hc06Device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(HC06_MAC_ADDRESS)
        hc06Device?.let {
            try {
                if (this.socket == null) {
                    this.socket = hc06Device?.createRfcommSocketToServiceRecord(HC06_UUID)
                } else if (this.socket!!.isConnected) {
                    return
                }
                this.socket?.connect()
                outputStream = this.socket!!.outputStream
                outputSocketConnected = true
                inputStream = socket!!.inputStream
                isSocketConnected = true
                beginListenForData()
            } catch (e: IOException) {
                Log.e("MainActivity", "Error connecting to HC-06: ${e.message}")
                return
            }
        }
    }

    @SuppressLint("HandlerLeak")
    private fun beginListenForData() {
        val handler = object : Handler() {
            override fun handleMessage(msg: Message) {
                if (msg.what == 0) {
                    val data = msg.obj as String
                    var command = "00"
                    var parameters = ""
                    if (data.length >= 2) {
                        command = data.substring(0, 2)
                        if (data.length >= 3) {
                            parameters = data.substring(2)
                        }
                    }
                    try {
                        SecondFragment.bluetoothListener.onBluetoothReceived(command, parameters)
                    } catch (e: UninitializedPropertyAccessException) {
                        Log.e("MainActivity", "Second fragment not initialized $e")
                    }
                    if (command == FINISH_COOKING) {
                        finishCooking(true)
                    } else if (command == TEMPERATURE_REACHED) {
                        temperatureReached()
                    }
                    else if (command == UPDATE_PROGRESS_BAR && automaticCooking) {
                        updateProgressNotification(parameters)
                    }
                    else if (command == POLLED_TEMPERATURE && !temperatureReached && automaticCooking && !timerStartsImmediately) {
                        updateNotificationToBoiling(parameters)
                    }
                    else if(command == RECIPE_PAUSE)
                    {
                        if(parameters == TRUE)
                        {
                            isPaused = true
                            updateNotificationToPause()
                        }
                        else
                        {
                            isPaused = false
                        }
                    }
                    else if(command == STOP_RECIPE)
                    {
                        finishCooking(false)
                    }
                }
            }
        }

        stopReader = false
        readBufferPosition = 0
        readBuffer = ByteArray(1024)

        this.workerThread = Thread {
            while (!Thread.currentThread().isInterrupted && !stopReader) {
                try {
                    val bytesAvailable = inputStream.available()
                    if (bytesAvailable > 0) {
                        val packetBytes = ByteArray(bytesAvailable)
                        inputStream.read(packetBytes)
                        for (i in 0 until bytesAvailable) {
                            val b = packetBytes[i]
                            if (b == '\n'.code.toByte()) {
                                val encodedBytes = ByteArray(readBufferPosition)
                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.size)
                                val data = String(encodedBytes, Charsets.US_ASCII)
                                readBufferPosition = 0
                                handler.obtainMessage(0, data).sendToTarget()
                            } else {
                                readBuffer[readBufferPosition++] = b
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e("MyLog", "Error reading from input stream: ${e.message}")
                    stopReader = true
                }
            }
        }
        workerThread.start()
    }





    private fun disconnectBluetooth() {
        Log.d("MyLog", "Disconnect bluetooth called")
        try {
            if (socket != null && socket!!.isConnected) {
                isSocketConnected = false
                stopReader = true
                socket!!.close()
                outputStream.close()
                inputStream.close()
                socket = null
            }
        } catch (e: IOException) {
            Log.e("MainActivity", "Error disconnecting from HC-06: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectBluetooth()
        drawableId = 0
        cookingRecipeName = ""
        isSocketConnected = false
        isServiceRunning = false
        timerStartsImmediately = false
        automaticCooking = false
        isPaused = false
        manualCooking = false
        temperatureReached = false
        recyclerViewPos = 0
        desiredTemperature = 0
        socket = null
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(ServiceEchoReceiver())
    }
}