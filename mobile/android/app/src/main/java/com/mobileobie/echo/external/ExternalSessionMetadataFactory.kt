package com.mobileobie.echo.external

import com.mobileobie.echo.audio.PcmSourceDescriptor
import com.mobileobie.echo.audio.PcmSourceDiagnostics
import com.mobileobie.echo.model.ExternalDeviceSessionMetadata
import com.mobileobie.echo.model.SessionSource

fun PcmSourceDescriptor.externalSessionMetadata(
    diagnostics: PcmSourceDiagnostics,
    reconnectCount: Int = 0,
): ExternalDeviceSessionMetadata? {
    if (sessionSource != SessionSource.EXTERNAL_DEVICE) return null
    val stableDeviceId = deviceId?.takeIf(String::isNotBlank) ?: return null
    return ExternalDeviceSessionMetadata(
        deviceId = stableDeviceId,
        deviceName = deviceName?.takeIf(String::isNotBlank) ?: "External device",
        manufacturer = manufacturer,
        model = model,
        firmwareVersion = firmwareVersion,
        protocolVersion = protocolVersion ?: 1,
        transport = transport ?: "Unknown",
        streamId = diagnostics.streamId,
        gapCount = diagnostics.gapCount,
        reconnectCount = reconnectCount.coerceAtLeast(0),
        receiverOverrunCount = diagnostics.receiverOverrunCount,
        degraded = diagnostics.gapCount > 0 || diagnostics.receiverOverrunCount > 0,
        interrupted = diagnostics.interrupted,
    )
}
