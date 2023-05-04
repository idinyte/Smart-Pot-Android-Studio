package com.example.smartpot

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nullable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


class MyForegroundService : Service(), ActivityResultRegistryOwner {
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var socket: BluetoothSocket? = null
    private lateinit var outputStream: OutputStream
    private lateinit var inputStream: InputStream
    private lateinit var workerThread: Thread
    private lateinit var readBuffer: ByteArray
    private var readBufferPosition: Int = 0
    var isSocketConnected = false
    private var outputSocketConnected = false
    private var drawableId = 0
    var recyclerViewPos = 0
    var desiredTemperature = 0
    private var cookingRecipeName = ""
    var timerStartsImmediately = false
    var automaticCooking = false
    var isPaused = false
    var manualCooking = false
    var temperatureReached = false

    @Volatile
    private var stopReader: Boolean = false
    var FINISH_COOKING = "16"
    var UPDATE_PROGRESS_BAR = "15"
    var TEMPERATURE_REACHED = "14"
    var POLLED_TEMPERATURE = "PT"
    var RECIPE_PAUSE = "10"
    var STOP_RECIPE = "17"
    var TRUE = "TRUE"

    var isRunning = false

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val NOTIFICATION_CHANNEL_ID = "BluetoothServiceChannel"
        private val HC06_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val HC06_MAC_ADDRESS: String = "00:22:06:30:7C:6C"
    }

    var mBinder: IBinder = LocalBinder()

    fun startService(context: Context) {
        val startIntent = Intent(context, MyForegroundService::class.java)
        ContextCompat.startForegroundService(context, startIntent)
    }

    fun stopService(context: Context) {
        disconnectBluetooth()
        val stopIntent = Intent(context, MyForegroundService::class.java)
        context.stopService(stopIntent)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return mBinder
    }


    inner class LocalBinder : Binder() {
        fun getServerInstance(): MyForegroundService? {
            return this@MyForegroundService
        }
    }

    override fun getActivityResultRegistry(): ActivityResultRegistry {
        return activityResultRegistry
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize any resources or setup code here
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        createNotificationChannel()
        LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(ServiceEchoReceiver(), IntentFilter("ping"))
    }

    private inner class ServiceEchoReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isSocketConnected) {
                LocalBroadcastManager
                    .getInstance(this@MyForegroundService)
                    .sendBroadcastSync(Intent("pong"))
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        enableBluetooth()

        // Create a notification for the foreground service
        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(R.drawable.smartpot)
            .build()

        startForegroundService(intent)
        // Put the service into foreground mode
        startForeground(NOTIFICATION_ID, notification)

        updateToDefaultNotification()

        // Return START_STICKY to ensure the service is restarted if it is killed by the system
        return START_STICKY
    }

    fun updateToDefaultNotification()
    {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updatedNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart pot connected")
            .setContentText("Disconnect it from the app once you are finished cooking")
            .setSmallIcon(R.drawable.smartpot)
            .build()

        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
    }

    fun updateNotificationToBoiling(temperature: String)
    {
        Log.d("MyLog", "Boiling notification")
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updatedNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart pot is making - $cookingRecipeName")
            .setContentText("Heating up $temperature°C/$desiredTemperature°C.")
            .setSmallIcon(R.drawable.smartpot)
            .build()

        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
    }

    fun updateProgressNotification(progress: String) {
        Log.d("MyLog", "Progress notification")
        val progressInt = progress.toIntOrNull() ?: return

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updatedNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart pot is making - $cookingRecipeName")
            .setContentText("$progressInt% done")
            .setSmallIcon(R.drawable.smartpot)
            .build()

        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
    }

    fun updateNotificationToManualCooking() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updatedNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart pot is in manual mode")
            .setContentText("")
            .setSmallIcon(R.drawable.smartpot)
            .build()

        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
    }

    fun updateNotificationToPause()
    {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updatedNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart pot is paused")
            .setContentText("")
            .setSmallIcon(R.drawable.smartpot)
            .build()

        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
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
        if (socket == null || socket != null && !socket!!.isConnected) {
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
                this.outputStream = this.socket!!.outputStream
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

    fun sendData(message: String) {
        try {
            val msg = (message + "\u0000").toByteArray(Charsets.UTF_8)
            Log.d("MyLog", "writing $msg")
            this.outputStream.write(msg)
        } catch (e: IOException) {
            Log.e("MyLog", "Error sending data: ${e.message}")
        }
    }

    fun setAutomaticRecipeVars(drawable: Int, pos: Int, name: String, startsImmediately: Boolean) {
        drawableId = drawable
        recyclerViewPos = pos
        cookingRecipeName = name
        automaticCooking = true
        timerStartsImmediately = startsImmediately
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
        Log.d("MyLog", "on Destroy called in service")
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}