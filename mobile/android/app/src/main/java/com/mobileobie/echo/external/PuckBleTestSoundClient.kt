package com.mobileobie.echo.external

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** A foreground-only BLE control action. It never records or transfers audio. */
class PuckBleTestSoundClient(private val context: Context) {
    suspend fun playTestSound(expectedDeviceId: String): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Result.Unavailable("Bluetooth test requires Android 12 or later.")
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return Result.Unavailable("Bluetooth is not available on this phone.")
        if (!adapter.isEnabled) return Result.Unavailable("Turn on Bluetooth to test your Huh? Puck.")
        val scanner = adapter.bluetoothLeScanner
            ?: return Result.Unavailable("Bluetooth scanning is unavailable on this phone.")
        val device = withTimeoutOrNull(SCAN_TIMEOUT_MS) { scan(scanner) }
            ?: return Result.Unavailable("No nearby Huh? Puck was found. Make sure it is powered on and advertising.")
        return withTimeoutOrNull(GATT_TIMEOUT_MS) { sendCommand(device, expectedDeviceId) }
            ?: Result.Unavailable("The Huh? Puck did not respond. Try pairing it, then test again.")
    }

    @SuppressLint("MissingPermission")
    private suspend fun scan(scanner: BluetoothLeScanner): BluetoothDevice? = suspendCancellableCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (completed.compareAndSet(false, true)) {
                    scanner.stopScan(this)
                    continuation.resume(result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                if (completed.compareAndSet(false, true)) continuation.resume(null)
            }
        }
        continuation.invokeOnCancellation { scanner.stopScan(callback) }
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
    }

    @SuppressLint("MissingPermission", "DEPRECATION")
    private suspend fun sendCommand(device: BluetoothDevice, expectedDeviceId: String): Result =
        suspendCancellableCoroutine { continuation ->
            var gatt: BluetoothGatt? = null
            var command: BluetoothGattCharacteristic? = null
            var response: BluetoothGattCharacteristic? = null
            val requestId = (System.nanoTime() and 0xffff_ffffL).toInt()
            val done = AtomicBoolean(false)

            fun finish(result: Result) {
                if (done.compareAndSet(false, true)) {
                    gatt?.disconnect()
                    gatt?.close()
                    continuation.resume(result)
                }
            }

            val callback = object : BluetoothGattCallback() {
                fun handleIdentity(gatt: BluetoothGatt, status: Int, value: ByteArray) {
                    val identity = value.toString(StandardCharsets.UTF_8).split('|')
                    if (status != BluetoothGatt.GATT_SUCCESS || identity.size < 2 || identity[0] != "v1") {
                        finish(Result.Unavailable("Could not verify the Huh? Puck identity."))
                    } else if (expectedDeviceId.isNotBlank() && identity[1] != expectedDeviceId) {
                        finish(Result.Unavailable("The nearby Huh? Puck does not match the configured device ID."))
                    } else enableResponseNotifications(gatt)
                }

                fun handleResponse(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                    if (characteristic.uuid != RESPONSE_UUID) return
                    val response = parseResponse(value) ?: return
                    if (response.first != requestId) return
                    finish(
                        when (response.second) {
                            "OK" -> Result.Success
                            "BUSY" -> Result.Unavailable("The Huh? Puck is busy recording or already playing a test sound.")
                            "AUDIO_OUTPUT_UNAVAILABLE" -> Result.Unavailable("This Huh? Puck has no test sound or LED output available.")
                            else -> Result.Unavailable("Huh? Puck could not play the test sound (${response.second}).")
                        },
                    )
                }

                fun enableResponseNotifications(gatt: BluetoothGatt) {
                    val responseCharacteristic = response
                        ?: return finish(Result.Unavailable("Huh? Puck controls are unavailable."))
                    gatt.setCharacteristicNotification(responseCharacteristic, true)
                    val descriptor = responseCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                        ?: return finish(Result.Unavailable("The Huh? Puck cannot confirm the test command."))
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    if (!gatt.writeDescriptor(descriptor)) finish(Result.Unavailable("Could not enable Huh? Puck responses."))
                }

                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Result.Unavailable("Could not connect to the Huh? Puck. Pair it and try again."))
                    } else if (newState == BluetoothGatt.STATE_CONNECTED) {
                        gatt.discoverServices()
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        finish(Result.Unavailable("The Huh? Puck disconnected before it could confirm the test."))
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Result.Unavailable("Could not read the Huh? Puck controls."))
                        return
                    }
                    val service = gatt.getService(SERVICE_UUID)
                    command = service?.getCharacteristic(COMMAND_UUID)
                    response = service?.getCharacteristic(RESPONSE_UUID)
                    if (command == null || response == null) {
                        finish(Result.Unavailable("This Huh? Puck firmware does not support the test command."))
                        return
                    }
                    val identity = service?.getCharacteristic(IDENTITY_UUID)
                    if (identity == null || !gatt.readCharacteristic(identity)) enableResponseNotifications(gatt)
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    if (characteristic.uuid != IDENTITY_UUID) return
                    handleIdentity(gatt, status, characteristic.value)
                }

                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    status: Int,
                ) {
                    if (characteristic.uuid == IDENTITY_UUID) handleIdentity(gatt, status, value)
                }

                override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Result.Unavailable("Could not enable Huh? Puck responses."))
                        return
                    }
                    val commandCharacteristic = command ?: return finish(Result.Unavailable("Huh? Puck controls are unavailable."))
                    commandCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    commandCharacteristic.value = commandPayload(requestId)
                    if (!gatt.writeCharacteristic(commandCharacteristic)) {
                        finish(Result.Unavailable("Could not send the test command. Pair the Puck and try again."))
                    }
                }

                override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Result.Unavailable("The Huh? Puck rejected the test command. Pair it and try again."))
                    }
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    handleResponse(characteristic, characteristic.value)
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                ) {
                    handleResponse(characteristic, value)
                }
            }
            continuation.invokeOnCancellation { gatt?.disconnect(); gatt?.close() }
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else device.connectGatt(context, false, callback)
        }

    sealed interface Result {
        data object Success : Result
        data class Unavailable(val message: String) : Result
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 8_000L
        private const val GATT_TIMEOUT_MS = 12_000L
        private val SERVICE_UUID = UUID.fromString("7d2e0001-6f9b-4af7-ae8c-5e4f48554831")
        private val IDENTITY_UUID = UUID.fromString("7d2e0002-6f9b-4af7-ae8c-5e4f48554831")
        private val COMMAND_UUID = UUID.fromString("7d2e0004-6f9b-4af7-ae8c-5e4f48554831")
        private val RESPONSE_UUID = UUID.fromString("7d2e0005-6f9b-4af7-ae8c-5e4f48554831")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        internal fun commandPayload(requestId: Int): ByteArray =
            ByteBuffer.allocate(23).order(ByteOrder.LITTLE_ENDIAN).put("HHC1".toByteArray()).putInt(requestId)
                .put("PLAY_TEST_SOUND".toByteArray()).array()

        internal fun parseResponse(bytes: ByteArray): Pair<Int, String>? {
            val parts = bytes.toString(StandardCharsets.UTF_8).split('|', limit = 3)
            if (parts.size != 3 || parts[0] != "v1") return null
            return parts[1].toIntOrNull()?.let { it to parts[2] }
        }
    }
}
