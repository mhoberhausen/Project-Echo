#include <Arduino.h>

#if ENABLE_SD_RECORDING_TEST
#include <FS.h>
#include <SD.h>
#include <SPI.h>
#endif

#include "DeviceConfig.h"
#include "audio/AudioFrame.h"
#include "audio/PdmAudioCapture.h"
#include "device/DeviceIdentity.h"
#if !ENABLE_SD_RECORDING_TEST
#include "device/SerialProvisioner.h"
#include "device/WifiCredentialStore.h"
#endif
#include "protocol/HuhAudioProtocol.h"
#include "runtime/StreamingController.h"
#if !ENABLE_SD_RECORDING_TEST
#include "transport/TcpAudioTransport.h"
#include "transport/TrustedLanWifi.h"

#include <memory>
#endif

namespace {

huh::audio::PdmAudioCapture microphone;
#if !ENABLE_SD_RECORDING_TEST
huh::runtime::StreamingController streamingController(microphone);
huh::device::WifiCredentialStore credentialStore;
huh::device::SerialProvisioner serialProvisioner(credentialStore);
huh::transport::TrustedLanWifi trustedLanWifi;
std::unique_ptr<huh::transport::TcpAudioTransport> tcpTransport;

void printWifiStatus() {
  Serial.printf("Wi-Fi: %s\n", trustedLanWifi.stateName());
  if (!trustedLanWifi.configuredSsid().isEmpty()) {
    Serial.printf("SSID: %s\n", trustedLanWifi.configuredSsid().c_str());
  }
  if (trustedLanWifi.isConnected()) {
    Serial.printf("IP: %s\n", WiFi.localIP().toString().c_str());
    Serial.printf("Signal: %ld dBm\n", static_cast<long>(WiFi.RSSI()));
  }
  Serial.printf("HUH1 TCP server: %s; port=%u; client=%s\n",
                tcpTransport != nullptr && tcpTransport->isListening() ? "listening" : "stopped",
                config::kTcpAudioPort,
                tcpTransport != nullptr && tcpTransport->hasClient() ? "connected" : "none");
}

void loadAndConnectWifi() {
  huh::device::WifiCredentials credentials;
  if (!credentialStore.load(credentials)) {
    trustedLanWifi.clearConfiguration();
    return;
  }
  trustedLanWifi.configure(credentials);
}
#endif

#if ENABLE_SD_RECORDING_TEST
void writeUint16LE(File& file, uint16_t value) {
  const uint8_t bytes[] = {
      static_cast<uint8_t>(value),
      static_cast<uint8_t>(value >> 8),
  };
  file.write(bytes, sizeof(bytes));
}

void writeUint32LE(File& file, uint32_t value) {
  const uint8_t bytes[] = {
      static_cast<uint8_t>(value),
      static_cast<uint8_t>(value >> 8),
      static_cast<uint8_t>(value >> 16),
      static_cast<uint8_t>(value >> 24),
  };
  file.write(bytes, sizeof(bytes));
}

bool writeWavHeader(File& file, uint32_t dataBytes) {
  const uint32_t byteRate = config::kSampleRate * config::kChannelCount *
                            config::kBitsPerSample / 8;
  const uint16_t blockAlign =
      config::kChannelCount * config::kBitsPerSample / 8;

  if (!file.seek(0)) {
    return false;
  }

  file.write(reinterpret_cast<const uint8_t*>("RIFF"), 4);
  writeUint32LE(file, 36 + dataBytes);
  file.write(reinterpret_cast<const uint8_t*>("WAVE"), 4);
  file.write(reinterpret_cast<const uint8_t*>("fmt "), 4);
  writeUint32LE(file, 16);
  writeUint16LE(file, 1);  // PCM
  writeUint16LE(file, config::kChannelCount);
  writeUint32LE(file, config::kSampleRate);
  writeUint32LE(file, byteRate);
  writeUint16LE(file, blockAlign);
  writeUint16LE(file, config::kBitsPerSample);
  file.write(reinterpret_cast<const uint8_t*>("data"), 4);
  writeUint32LE(file, dataBytes);
  return file.position() == 44;
}

bool mountSdCard() {
  for (const int chipSelect : config::kSdChipSelectCandidates) {
    Serial.printf("Trying microSD with CS on GPIO %d...\n", chipSelect);
    SPI.end();
    SPI.begin(config::kSdClockPin, config::kSdMisoPin,
              config::kSdMosiPin, chipSelect);

    if (SD.begin(chipSelect, SPI, config::kSdFrequencyHz) &&
        SD.cardType() != CARD_NONE) {
      Serial.printf("microSD mounted (CS GPIO %d, %llu MB).\n", chipSelect,
                    SD.cardSize() / (1024ULL * 1024ULL));
      return true;
    }

    SD.end();
  }

  Serial.println("ERROR: Could not mount a microSD card.");
  Serial.println("Insert a FAT32 card (32 GB or smaller), then reset.");
  return false;
}

bool recordTestWav() {
  if (SD.exists(config::kRecordingPath) &&
      !SD.remove(config::kRecordingPath)) {
    Serial.println("ERROR: Could not replace the existing /test.wav.");
    return false;
  }

  File output = SD.open(config::kRecordingPath, FILE_WRITE);
  if (!output) {
    Serial.println("ERROR: Could not create /test.wav.");
    return false;
  }

  if (!writeWavHeader(output, 0)) {
    Serial.println("ERROR: Could not write the WAV header.");
    output.close();
    return false;
  }

  const uint32_t targetFrames = config::kRecordingSeconds * 1000 /
                                huh::audio::kFrameDurationMs;
  uint32_t bytesWritten = 0;
  uint32_t lastReportedSecond = 0;

  Serial.printf("Recording for %lu seconds...\n",
                static_cast<unsigned long>(config::kRecordingSeconds));

  for (uint32_t frameIndex = 0; frameIndex < targetFrames; ++frameIndex) {
    huh::audio::AudioFrame frame;
    if (!microphone.readFrame(frame)) {
      Serial.println("ERROR: Microphone returned no audio data.");
      output.close();
      return false;
    }

    const size_t written = output.write(frame.pcmLittleEndian.data(),
                                        frame.pcmLittleEndian.size());
    if (written != frame.pcmLittleEndian.size()) {
      Serial.println("ERROR: microSD write did not complete.");
      output.close();
      return false;
    }
    bytesWritten += written;

    const uint32_t recordedSecond = bytesWritten /
        (config::kSampleRate * (config::kBitsPerSample / 8));
    if (recordedSecond > lastReportedSecond) {
      lastReportedSecond = recordedSecond;
      Serial.printf("  %lu/%lu seconds\n",
                    static_cast<unsigned long>(recordedSecond),
                    static_cast<unsigned long>(config::kRecordingSeconds));
    }
  }

  if (!writeWavHeader(output, bytesWritten)) {
    Serial.println("ERROR: Could not finalize the WAV header.");
    output.close();
    return false;
  }

  output.flush();
  output.close();
  File verification = SD.open(config::kRecordingPath, FILE_READ);
  const size_t fileBytes = verification ? verification.size() : 0;
  if (verification) verification.close();
  const size_t expectedBytes = 44 + bytesWritten;
  if (fileBytes != expectedBytes) {
    Serial.printf("ERROR: WAV size is %u bytes; expected %u.\n",
                  static_cast<unsigned>(fileBytes),
                  static_cast<unsigned>(expectedBytes));
    return false;
  }
  Serial.printf("SUCCESS: Wrote %s (%u bytes; %lu audio bytes).\n",
                config::kRecordingPath, static_cast<unsigned>(fileBytes),
                static_cast<unsigned long>(bytesWritten));
  return true;
}
#endif

}  // namespace

void setup() {
  Serial.begin(config::kSerialBaud);
  const uint32_t serialDeadline = millis() + config::kSerialWaitMs;
  while (!Serial && static_cast<int32_t>(serialDeadline - millis()) > 0) {
    delay(10);
  }

  Serial.println();
  Serial.println("Project Echo firmware: XIAO ESP32S3 Sense bring-up");
  const auto identity = huh::device::DeviceIdentity::helloInfo();
  Serial.printf("Device: %s; firmware=%s; protocol=1\n",
                identity.deviceId.c_str(), identity.firmwareVersion.c_str());

#if ENABLE_SD_RECORDING_TEST
  if (!mountSdCard() ||
      !microphone.begin(config::kMicClockPin, config::kMicDataPin)) {
    Serial.println("Bring-up stopped. Correct the error and reset the board.");
    return;
  }
  Serial.println("PDM microphone initialized at 16 kHz, 16-bit mono.");

  recordTestWav();
  microphone.end();
  SD.end();
  Serial.println("Recording complete. It is safe to remove the microSD card.");
#else
  if (!streamingController.begin(config::kAudioQueueFrames)) {
    Serial.println("Bring-up stopped. Could not allocate the bounded audio queue.");
    return;
  }
  Serial.printf("Configured for %u-byte/20 ms microphone frames; queue=%u frames.\n",
                static_cast<unsigned>(huh::audio::kBytesPerFrame),
                static_cast<unsigned>(config::kAudioQueueFrames));
  Serial.println("Trusted-LAN POC: audio starts only after an Android TCP connection.");
  Serial.println("Build the xiao_esp32s3_sense_sd_test environment to record /test.wav.");
  Serial.flush();
  tcpTransport = std::make_unique<huh::transport::TcpAudioTransport>(
      config::kTcpAudioPort, identity, streamingController);
  huh::device::WifiCredentials credentials;
  const bool hasCredentials = credentialStore.load(credentials);
  serialProvisioner.begin(hasCredentials);
  if (hasCredentials) trustedLanWifi.configure(credentials);
#endif
}

void loop() {
#if ENABLE_SD_RECORDING_TEST
  delay(1000);
#else
  switch (serialProvisioner.poll()) {
    case huh::device::ProvisioningEvent::kCredentialsSaved:
      if (tcpTransport != nullptr) tcpTransport->setNetworkAvailable(false);
      loadAndConnectWifi();
      break;
    case huh::device::ProvisioningEvent::kCredentialsReset:
      if (tcpTransport != nullptr) tcpTransport->setNetworkAvailable(false);
      trustedLanWifi.clearConfiguration();
      break;
    case huh::device::ProvisioningEvent::kStatusRequested:
      printWifiStatus();
      break;
    case huh::device::ProvisioningEvent::kNone:
      break;
  }
  trustedLanWifi.poll();
  if (tcpTransport != nullptr) {
    tcpTransport->setNetworkAvailable(trustedLanWifi.isConnected());
    tcpTransport->poll();
  }
  if (tcpTransport == nullptr || !tcpTransport->shouldPollImmediately()) {
    delay(1);
  } else {
    taskYIELD();
  }
#endif
}
