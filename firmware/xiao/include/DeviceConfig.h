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
constexpr uint16_t kDiagnosticPort = 8766;
constexpr uint32_t kDiagnosticIntervalMs = 2000;
constexpr uint16_t kOtaPort = 3232;
#ifndef HUH_OTA_PASSWORD
#define HUH_OTA_PASSWORD "REDACTED_CREDENTIAL"
#endif
constexpr char kOtaPassword[] = HUH_OTA_PASSWORD;
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

// Optional passive-piezo verification output. D1 maps to GPIO2 on the XIAO
// ESP32S3 and is unused by the microphone and microSD configuration above.
// Set to -1 at build time with HUH_TEST_TONE_PIN=-1 to disable it.
#ifndef HUH_TEST_TONE_PIN
#define HUH_TEST_TONE_PIN 2
#endif
constexpr int kTestTonePin = HUH_TEST_TONE_PIN;
constexpr int kConnectionTestLedPin = LED_BUILTIN;
constexpr bool kConnectionTestLedActiveLow = true;

// Development pairing PIN. Override with -DHUH_BLE_STATIC_PASSKEY=<six digits>.
// Set HUH_BLE_USE_STATIC_PASSKEY=0 to generate a new PIN at each boot.
#ifndef HUH_BLE_USE_STATIC_PASSKEY
#define HUH_BLE_USE_STATIC_PASSKEY 1
#endif
#ifndef HUH_BLE_STATIC_PASSKEY
#define HUH_BLE_STATIC_PASSKEY REDACTED_CREDENTIAL
#endif
constexpr bool kBleUseStaticPasskey = HUH_BLE_USE_STATIC_PASSKEY != 0;
constexpr uint32_t kBleStaticPasskey = HUH_BLE_STATIC_PASSKEY;

}  // namespace config
