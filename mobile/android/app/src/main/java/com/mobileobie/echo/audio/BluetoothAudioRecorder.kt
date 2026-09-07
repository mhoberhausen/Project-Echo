package com.mobileobie.echo.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** Records from a connected Bluetooth microphone, never silently falling back to the phone mic. */
class BluetoothAudioRecorder(private val context: Context) : AudioRecorder, IncrementalAudioRecorder {
    private var delegate: AndroidAudioRecorder? = null

    override suspend fun start() {
        recorder().start()
    }

    override suspend fun startIncremental(
        quietBoundaryMs: Long,
        onChunk: (RecordedAudioChunk) -> Unit,
    ) {
        recorder().startIncremental(quietBoundaryMs, onChunk)
    }

    override suspend fun stop(): RecordedAudio = checkNotNull(delegate) {
        "No Bluetooth recording is in progress."
    }.stop()

    override fun release() {
        delegate?.release()
        delegate = null
    }

    private fun recorder(): AndroidAudioRecorder {
        check(delegate == null) { "A Bluetooth recording is already in progress." }
        val audioManager = context.getSystemService(AudioManager::class.java)
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        } ?: error("Connect a Bluetooth device with a microphone before recording.")
        return AndroidAudioRecorder(preferredInputDevice = device).also { delegate = it }
    }
}
