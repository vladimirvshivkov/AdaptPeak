package com.vladimirvshivkov.adaptpeak

import android.Manifest
import android.bluetooth.*
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.vladimirvshivkov.adaptpeak.R

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "Polar_MainActivity"
        private const val SHARED_PREFS_KEY = "polar_device_id"
        private const val PERMISSION_REQUEST_CODE = 100
        private const val REQUEST_DEVICE_SEARCH = 101
    }

    private lateinit var sharedPreferences: SharedPreferences
    private val bluetoothOnActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK) {
            Log.w(TAG, "Bluetooth off")
        }
    }
    private var deviceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPreferences = getPreferences(MODE_PRIVATE)
        deviceId = sharedPreferences.getString(SHARED_PREFS_KEY, "")

        val hrConnectButton: Button = findViewById(R.id.buttonConnectHr)
        val searchDevicesButton: Button = findViewById(R.id.buttonSearchDevices)
        checkBT()

        hrConnectButton.setOnClickListener { onClickConnectHr(it) }
        searchDevicesButton.setOnClickListener { onClickSearchDevices() }
    }

    private fun onClickConnectHr(view: View) {
        checkBT()
        if (deviceId == null || deviceId == "") {
            deviceId = sharedPreferences.getString(SHARED_PREFS_KEY, "")
            showDialog(view)
        } else {
            showToast(getString(R.string.connecting) + " " + deviceId)
            val intent = Intent(this, HRActivity::class.java)
            intent.putExtra("id", deviceId)
            startActivity(intent)
        }
    }

    private val deviceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val deviceAddress = result.data?.getStringExtra("device_address")
            deviceId = deviceAddress
            val editor = sharedPreferences.edit()
            editor.putString(SHARED_PREFS_KEY, deviceId)
            editor.apply()
            showToast("Device selected: $deviceId")
        }
    }

    private fun onClickSearchDevices() {
        if (checkAndRequestPermissions()) {
            val intent = Intent(this, DeviceSearchActivity::class.java)
            deviceSearchLauncher.launch(intent)
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

    private fun showDialog(view: View) {
        val dialog = AlertDialog.Builder(this, R.style.PolarTheme)
        dialog.setTitle("Enter your Polar device's ID")
        val viewInflated = LayoutInflater.from(applicationContext).inflate(R.layout.device_id_dialog_layout, view.rootView as ViewGroup, false)
        val input = viewInflated.findViewById<EditText>(R.id.input)
        if (deviceId?.isNotEmpty() == true) input.setText(deviceId)
        input.inputType = InputType.TYPE_CLASS_TEXT
        dialog.setView(viewInflated)
        dialog.setPositiveButton("OK") { _: DialogInterface?, _: Int ->
            deviceId = input.text.toString().uppercase()
            val editor = sharedPreferences.edit()
            editor.putString(SHARED_PREFS_KEY, deviceId)
            editor.apply()
        }
        dialog.setNegativeButton("Cancel") { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        dialog.show()
    }

    private fun checkBT() {
        val btManager = applicationContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = btManager.adapter
        if (bluetoothAdapter == null) {
            showToast("Device doesn't support Bluetooth")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothOnActivityResultLauncher.launch(enableBtIntent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
            } else {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "Needed permissions are granted")
                onClickSearchDevices()
            } else {
                Log.w(TAG, "Needed permissions are missing")
                showToast("Needed permissions are required to search for Bluetooth devices")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEVICE_SEARCH && resultCode == RESULT_OK) {
            val deviceAddress = data?.getStringExtra("device_address")
            deviceId = deviceAddress
            val editor = sharedPreferences.edit()
            editor.putString(SHARED_PREFS_KEY, deviceId)
            editor.apply()
            showToast("Device selected: $deviceId")
        }
    }

    private fun showToast(message: String) {
        val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
        toast.show()
    }
}
