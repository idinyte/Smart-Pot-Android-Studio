package com.example.smartpot

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import com.example.smartpot.databinding.FragmentFirstBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {
    private var _binding: FragmentFirstBinding? = null
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothService: MyForegroundService
    private lateinit var bluetoothServiceIntent: Intent
    private val binding get() = _binding!!
    private var isBluetoothServiceRunning = false


    var activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                startBluetoothService()
            }
        }

    val pong = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isBluetoothServiceRunning = true
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        bluetoothServiceIntent = Intent(context, MyForegroundService::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        val connectBluetoothButton: Button = view.findViewById(R.id.button_connect)
        connectBluetoothButton.setOnClickListener {
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                activityResultLauncher.launch(enableBtIntent)
            } else {
                startBluetoothService()
            }
        }

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(pong, IntentFilter("pong"))
        viewLifecycleOwner.lifecycleScope.launch {
            while (!isBluetoothServiceRunning) {
                LocalBroadcastManager.getInstance(requireContext()).sendBroadcastSync(Intent("ping"))
                delay(500)
            }
            requireActivity().findNavController(R.id.nav_host_fragment_content_main)
                .navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    private fun startBluetoothService() {
        (activity as MainActivity).startMyService()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}