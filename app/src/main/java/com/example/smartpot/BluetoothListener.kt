package com.example.smartpot

interface BluetoothListener {
    fun onBluetoothReceived(command: String, parameters: String)
}