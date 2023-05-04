package com.example.smartpot

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.example.smartpot.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var mConnection: ServiceConnection

    private var mBounded = false
    var bluetoothService: MyForegroundService? = null

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        mConnection = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName) {
                mBounded = false
                bluetoothService = null
            }

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val mLocalBinder = service as MyForegroundService.LocalBinder
                bluetoothService = mLocalBinder.getServerInstance()
            }
        }

        val startIntent = Intent(this, MyForegroundService::class.java)
        bindService(startIntent, mConnection as ServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    fun startMyService() {
        bluetoothService!!.startService(this)
    }

    fun stopMyService() {
        if (mBounded) {
            unbindService(mConnection)
            mBounded = false
        }
        bluetoothService!!.stopService(this)
    }

    fun sendData(message: String)
    {
        Log.d("MyLog", "Sending $message")
        bluetoothService!!.sendData(message)
    }
}