package com.vladimirvshivkov.adaptpeak

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DeviceSearchActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "DeviceSearchActivity"
        private const val SCAN_PERIOD: Long = 10000 // Scanning time in milliseconds
        private const val PERMISSION_REQUEST_CODE = 200
    }

    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private lateinit var scanButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_search)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        setupViews()
        setupRecyclerView()
    }

    private fun setupViews() {
        scanButton = findViewById(R.id.scanButton)
        progressBar = findViewById(R.id.scanProgressBar)

        scanButton.setOnClickListener {
            if (!isScanning) {
                startScan()
            } else {
                stopScan()
            }
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.devicesList)
        deviceAdapter = BluetoothDeviceAdapter { device ->
            if (checkAndRequestPermissions()) {
                stopScan()
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val resultIntent = intent.apply {
                        putExtra("device_address", device.address)
                        putExtra("device_name", device.name)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@DeviceSearchActivity)
            adapter = deviceAdapter
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    PERMISSION_REQUEST_CODE
                )
                return false
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_REQUEST_CODE
                )
                return false
            }
        }
        return true
    }

    private fun startScan() {
        if (!checkAndRequestPermissions()) {
            return
        }

        deviceAdapter.clearDevices()
        isScanning = true
        scanButton.text = "Stop Scan"
        progressBar.visibility = View.VISIBLE

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Remove the device name filter to see all devices, we'll filter in the callback
        val scanFilters = listOf<ScanFilter>()  // empty list to see all devices

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothAdapter.bluetoothLeScanner?.startScan(
                scanFilters,
                scanSettings,
                scanCallback
            )

            handler.postDelayed({
                stopScan()
            }, SCAN_PERIOD)
        }
    }


    private fun stopScan() {
        if (!checkAndRequestPermissions()) {
            return
        }

        isScanning = false
        scanButton.text = "Start Scan"
        progressBar.visibility = View.GONE

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        }
    }

    // Update the scan callback to filter devices
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (ActivityCompat.checkSelfPermission(
                        this@DeviceSearchActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Check if device name contains "Polar H10"
                    val deviceName = device.name
                    if (deviceName != null && deviceName.contains("Polar H10", ignoreCase = true)) {
                        runOnUiThread {
                            deviceAdapter.addDevice(device)
                        }
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            runOnUiThread {
                Toast.makeText(
                    this@DeviceSearchActivity,
                    "Scan failed. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
                stopScan()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Permissions granted, start scanning
                    startScan()
                } else {
                    Toast.makeText(
                        this,
                        "Bluetooth scan permission is required for device search",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
    }
}

class BluetoothDeviceAdapter(
    private val devices: MutableList<BluetoothDevice> = mutableListOf(),
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.deviceName)
        val deviceAddress: TextView = view.findViewById(R.id.deviceAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        if (ActivityCompat.checkSelfPermission(
                holder.itemView.context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        holder.deviceName.text = device.name ?: "Unknown Device"
        holder.deviceAddress.text = device.address
        holder.itemView.setOnClickListener { onDeviceClick(device) }
    }

    override fun getItemCount() = devices.size

    fun addDevice(device: BluetoothDevice) {
        if (!devices.contains(device)) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }

    fun clearDevices() {
        devices.clear()
        notifyDataSetChanged()
    }
}
