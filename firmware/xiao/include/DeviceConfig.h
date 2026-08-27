#pragma once

#include <Arduino.h>

namespace config {

constexpr uint32_t kSerialBaud = 115200;
constexpr uint32_t kSerialWaitMs = 3000;

constexpr int kMicClockPin = 42;
constexpr int kMicDataPin = 41;
constexpr uint32_t kSampleRate = 16000;
constexpr uint16_t kBitsPerSample = 16;
constexpr uint16_t kChannelCount = 1;

constexpr int kSdClockPin = 7;
constexpr int kSdMisoPin = 8;
constexpr int kSdMosiPin = 9;
// Sense board revisions/documentation use GPIO 3 or GPIO 21 for SD CS.
constexpr int kSdChipSelectCandidates[] = {3, 21};
constexpr uint32_t kSdFrequencyHz = 10000000;

constexpr uint32_t kRecordingSeconds = 10;
constexpr char kRecordingPath[] = "/test.wav";
constexpr char kCaptureDirectory[] = "/captures";

// The SD-first path does not queue live PCM. Keep the legacy queue minimally
// allocated until the streaming controller interface is simplified.
constexpr size_t kAudioQueueFrames = 1;
constexpr uint32_t kFrameDiagnosticInterval = 500;
constexpr BaseType_t kAudioCaptureCore = 0;

constexpr uint16_t kTcpAudioPort = 8765;
constexpr uint32_t kHeartbeatIntervalMs = 5000;
// Android renews the active capture lease at kHeartbeatIntervalMs. Capture is
// finalized safely if no matching control heartbeat arrives before this limit.
constexpr uint32_t kCaptureLeaseTimeoutMs = 15000;
// Keep each live send below the TCP MSS and let ACKs advance the small lwIP
// send window between frames instead of filling it with a multi-frame burst.
constexpr size_t kTcpFramesPerPass = 1;
constexpr uint32_t kTcpPassBudgetMs = 12;
constexpr uint32_t kStreamDiagnosticIntervalMs = 5000;
constexpr int kTcpKeepaliveIdleSeconds = 10;
constexpr int kTcpKeepaliveIntervalSeconds = 3;
constexpr int kTcpKeepaliveProbeCount = 3;
constexpr uint32_t kWifiConnectTimeoutMs = 15000;
constexpr uint32_t kWifiRetryIntervalMs = 30000;
constexpr size_t kMaximumSerialCommandBytes = 96;

}  // namespace config
