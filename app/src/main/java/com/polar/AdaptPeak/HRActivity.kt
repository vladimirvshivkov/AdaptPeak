package com.vladimirvshivkov.AdaptPeak

import java.util.UUID
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl.defaultImplementation
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable

class HRActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "HRActivity"
        private const val PERMISSION_REQUEST_CODE = 1
    }

    private lateinit var api: PolarBleApi
    private lateinit var textViewHR: TextView
    private lateinit var textViewRR: TextView
    private lateinit var textViewDeviceId: TextView
    private lateinit var deviceId: String
    private var hrDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hr)
        deviceId = intent.getStringExtra("id") ?: throw Exception("HRActivity couldn't be created, no deviceId given")
        textViewHR = findViewById(R.id.hr_value_label)
        textViewRR = findViewById(R.id.rr_value_label)
        textViewDeviceId = findViewById(R.id.device_id_label)

        api = defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )

        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BluetoothStateChanged $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connected ${polarDeviceInfo.deviceId}")
                Toast.makeText(applicationContext, R.string.connected, Toast.LENGTH_SHORT).show()
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connecting ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device disconnected ${polarDeviceInfo.deviceId}")
            }

            override fun disInformationReceived(
                identifier: String,
                disInfo: DisInfo
            ) {
                TODO("Not yet implemented")
            }

            override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
                Log.d(TAG, "Feature ready $feature")
                if (feature == PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) {
                    checkPermissionsAndStreamData()
                }
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d(TAG, "Dis information received: $identifier $uuid $value")
            }

            override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
                // Deprecated
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                // No operation
            }

            override fun polarFtpFeatureReady(identifier: String) {
                // No operation
            }
        })

        textViewDeviceId.text = deviceId
    }

    override fun onDestroy() {
        super.onDestroy()
        hrDisposable?.let {
            if (!it.isDisposed) {
                it.dispose()
            }
        }
        api.shutDown()
    }

    private fun checkPermissionsAndStreamData() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN), PERMISSION_REQUEST_CODE)
        } else {
            connectToDeviceAndStreamData()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToDeviceAndStreamData()
            } else {
                Toast.makeText(this, "Bluetooth permission denied. Cannot connect to the device.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectToDeviceAndStreamData() {
        try {
            api.connectToDevice(deviceId)
        } catch (polarInvalidArgument: PolarInvalidArgument) {
            Log.e(TAG, "Failed to connect. Reason $polarInvalidArgument")
        }
    }

    private fun streamHRData() {
        val isDisposed = hrDisposable?.isDisposed ?: true
        if (isDisposed) {
            hrDisposable = api.startHrStreaming(deviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarHrData: PolarHrData ->
                        for (sample in polarHrData.samples) {
                            val hrText = "Heart Rate: ${sample.hr} bpm"
                            textViewHR.text = hrText

                            if (sample.rrsMs.isNotEmpty()) {
                                val hrvText = "HRV (rMSSD): ${sample.rrsMs.average()} ms"
                                textViewRR.text = hrvText
                            }
                        }
                    },
                    { error: Throwable ->
                        val errorString = "HR stream failed: $error"
                        Log.e(TAG, errorString)
                        Toast.makeText(applicationContext, errorString, Toast.LENGTH_LONG).show()
                    },
                    { Log.d(TAG, "HR stream complete") }
                )
        } else {
            hrDisposable?.dispose()
            hrDisposable = null
        }
    }
}
