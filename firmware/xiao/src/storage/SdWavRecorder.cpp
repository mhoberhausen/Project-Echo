#include "SdWavRecorder.h"

#include <SPI.h>

#include "DeviceConfig.h"

namespace huh::storage {
namespace {

void writeU16(File& file, uint16_t value) {
  const uint8_t bytes[] = {static_cast<uint8_t>(value),
                           static_cast<uint8_t>(value >> 8)};
  file.write(bytes, sizeof(bytes));
}

void writeU32(File& file, uint32_t value) {
  const uint8_t bytes[] = {
      static_cast<uint8_t>(value), static_cast<uint8_t>(value >> 8),
      static_cast<uint8_t>(value >> 16), static_cast<uint8_t>(value >> 24)};
  file.write(bytes, sizeof(bytes));
}

}  // namespace

bool SdWavRecorder::begin() {
  if (mounted_) return true;
  for (const int chipSelect : config::kSdChipSelectCandidates) {
    SPI.end();
    SPI.begin(config::kSdClockPin, config::kSdMisoPin,
              config::kSdMosiPin, chipSelect);
    if (SD.begin(chipSelect, SPI, config::kSdFrequencyHz) &&
        SD.cardType() != CARD_NONE) {
      mounted_ = true;
      if (!SD.exists(config::kCaptureDirectory) &&
          !SD.mkdir(config::kCaptureDirectory)) {
        Serial.println("ERROR: Could not create /captures on microSD.");
        end();
        return false;
      }
      Serial.printf("microSD mounted for durable capture (CS GPIO %d, %llu MB).\n",
                    chipSelect, SD.cardSize() / (1024ULL * 1024ULL));
      return true;
    }
    SD.end();
  }
  Serial.println("ERROR: Active capture requires a writable microSD card.");
  return false;
}

void SdWavRecorder::end() {
  if (output_) output_.close();
  if (mounted_) SD.end();
  mounted_ = false;
}

String SdWavRecorder::uuidText(const protocol::StreamUuid& stream) {
  static const char hex[] = "0123456789abcdef";
  String result;
  result.reserve(32);
  for (const uint8_t byte : stream) {
    result += hex[byte >> 4];
    result += hex[byte & 0x0f];
  }
  return result;
}

bool SdWavRecorder::start(const protocol::StreamUuid& stream) {
  if (!mounted_ || output_) return false;
  const String stem = String(config::kCaptureDirectory) + "/" + uuidText(stream);
  partialPath_ = stem + ".part";
  finalizedPath_ = stem + ".wav";
  if (SD.exists(partialPath_)) SD.remove(partialPath_);
  if (SD.exists(finalizedPath_)) SD.remove(finalizedPath_);
  output_ = SD.open(partialPath_, FILE_WRITE);
  audioBytes_ = 0;
  if (!output_ || !writeHeader(output_, 0)) {
    if (output_) output_.close();
    return false;
  }
  return true;
}

bool SdWavRecorder::append(const audio::AudioFrame& frame) {
  if (!output_) return false;
  const size_t written = output_.write(frame.pcmLittleEndian.data(),
                                       frame.pcmLittleEndian.size());
  if (written != frame.pcmLittleEndian.size()) return false;
  audioBytes_ += static_cast<uint32_t>(written);
  return true;
}

bool SdWavRecorder::finish() {
  if (!output_) return false;
  const bool headerWritten = writeHeader(output_, audioBytes_);
  output_.flush();
  output_.close();
  if (!headerWritten) return false;
  if (SD.exists(finalizedPath_)) SD.remove(finalizedPath_);
  if (!SD.rename(partialPath_, finalizedPath_)) return false;
  Serial.printf("Capture finalized: %s (%lu audio bytes).\n",
                finalizedPath_.c_str(),
                static_cast<unsigned long>(audioBytes_));
  return true;
}

File SdWavRecorder::openFinalized() {
  if (!mounted_ || finalizedPath_.isEmpty()) return File();
  File file = SD.open(finalizedPath_, FILE_READ);
  if (file && !file.seek(44)) file.close();
  return file;
}

bool SdWavRecorder::removeFinalized() {
  if (!mounted_ || finalizedPath_.isEmpty()) return false;
  const bool removed = !SD.exists(finalizedPath_) || SD.remove(finalizedPath_);
  if (removed) {
    Serial.printf("Capture acknowledged and removed: %s\n", finalizedPath_.c_str());
    finalizedPath_.clear();
    partialPath_.clear();
    audioBytes_ = 0;
  }
  return removed;
}

bool SdWavRecorder::writeHeader(File& file, uint32_t dataBytes) {
  if (!file.seek(0)) return false;
  const uint32_t byteRate = audio::kSampleRateHz * audio::kChannelCount *
                            audio::kBitsPerSample / 8;
  const uint16_t blockAlign = audio::kChannelCount * audio::kBitsPerSample / 8;
  file.write(reinterpret_cast<const uint8_t*>("RIFF"), 4);
  writeU32(file, 36 + dataBytes);
  file.write(reinterpret_cast<const uint8_t*>("WAVEfmt "), 8);
  writeU32(file, 16);
  writeU16(file, 1);
  writeU16(file, audio::kChannelCount);
  writeU32(file, audio::kSampleRateHz);
  writeU32(file, byteRate);
  writeU16(file, blockAlign);
  writeU16(file, audio::kBitsPerSample);
  file.write(reinterpret_cast<const uint8_t*>("data"), 4);
  writeU32(file, dataBytes);
  return file.position() == 44;
}

}  // namespace huh::storage
