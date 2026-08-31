#include "SdWavRecorder.h"

#include <SPI.h>
#include <cstring>

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

uint16_t readU16(const uint8_t* bytes) {
  return static_cast<uint16_t>(bytes[0]) |
         (static_cast<uint16_t>(bytes[1]) << 8);
}

uint32_t readU32(const uint8_t* bytes) {
  return static_cast<uint32_t>(bytes[0]) |
         (static_cast<uint32_t>(bytes[1]) << 8) |
         (static_cast<uint32_t>(bytes[2]) << 16) |
         (static_cast<uint32_t>(bytes[3]) << 24);
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
      const size_t repaired = recoverInterruptedCaptures();
      if (repaired > 0) Serial.printf("Recovery: finalized %u interrupted capture(s).\n", static_cast<unsigned>(repaired));
      const size_t recovered = printFinalizedCaptures(Serial);
      if (recovered > 0) Serial.printf("Recovery: %u finalized capture(s) retained on SD.\n", static_cast<unsigned>(recovered));
      return true;
    }
    SD.end();
  }
  Serial.println("ERROR: Active capture requires a writable microSD card.");
  return false;
}

size_t SdWavRecorder::printFinalizedCaptures(Print& out) const {
  const auto captures = finalizedCaptures();
  for (const auto& capture : captures) {
    out.printf("Retained capture: %s.wav (%lu audio bytes)\n",
               uuidText(capture.stream).c_str(),
               static_cast<unsigned long>(capture.audioBytes));
  }
  return captures.size();
}

std::vector<FinalizedCapture> SdWavRecorder::finalizedCaptures() const {
  std::vector<FinalizedCapture> captures;
  if (!mounted_) return captures;
  File directory = SD.open(config::kCaptureDirectory);
  if (!directory || !directory.isDirectory()) return captures;
  for (File entry = directory.openNextFile(); entry; entry = directory.openNextFile()) {
    protocol::StreamUuid stream{};
    const String name = entry.name();
    uint32_t audioBytes = 0;
    if (!entry.isDirectory() && parseCaptureName(name, stream) &&
        validateWav(entry, audioBytes)) {
      captures.push_back({stream, audioBytes});
    }
    entry.close();
  }
  directory.close();
  return captures;
}

bool SdWavRecorder::selectFinalized(const protocol::StreamUuid& stream) {
  if (!mounted_ || output_) return false;
  const String path = String(config::kCaptureDirectory) + "/" + uuidText(stream) + ".wav";
  File file = SD.open(path, FILE_READ);
  uint32_t audioBytes = 0;
  if (!file || !validateWav(file, audioBytes)) {
    if (file) file.close();
    return false;
  }
  audioBytes_ = audioBytes;
  file.close();
  finalizedPath_ = path;
  partialPath_.clear();
  return true;
}

size_t SdWavRecorder::recoverInterruptedCaptures() {
  File directory = SD.open(config::kCaptureDirectory);
  if (!directory || !directory.isDirectory()) return 0;
  std::vector<String> partials;
  for (File entry = directory.openNextFile(); entry; entry = directory.openNextFile()) {
    String name = entry.name();
    if (!entry.isDirectory() && name.endsWith(".part") && entry.size() >= 44 &&
        entry.size() - 44 <= UINT32_MAX && ((entry.size() - 44) % 2) == 0) {
      const int slash = name.lastIndexOf('/');
      const String base = slash >= 0 ? name.substring(slash + 1) : name;
      partials.push_back(String(config::kCaptureDirectory) + "/" + base);
    }
    entry.close();
  }
  directory.close();

  size_t recovered = 0;
  for (const String& partial : partials) {
    File file = SD.open(partial, FILE_WRITE);
    const uint32_t bytes = file ? static_cast<uint32_t>(file.size() - 44) : 0;
    const bool headerWritten = file && writeHeader(file, bytes);
    if (file) { file.flush(); file.close(); }
    String finalized = partial.substring(0, partial.length() - 5) + ".wav";
    if (headerWritten && !SD.exists(finalized) && SD.rename(partial, finalized)) ++recovered;
  }
  return recovered;
}

bool SdWavRecorder::validateWav(File& file, uint32_t& audioBytes) {
  audioBytes = 0;
  if (!file || file.size() < 44 || file.size() - 44 > UINT32_MAX || !file.seek(0)) return false;
  uint8_t header[44];
  if (file.read(header, sizeof(header)) != sizeof(header)) return false;
  const uint32_t expectedBytes = static_cast<uint32_t>(file.size() - 44);
  const bool valid = memcmp(header, "RIFF", 4) == 0 &&
      memcmp(header + 8, "WAVEfmt ", 8) == 0 &&
      readU32(header + 16) == 16 && readU16(header + 20) == 1 &&
      readU16(header + 22) == audio::kChannelCount &&
      readU32(header + 24) == audio::kSampleRateHz &&
      readU16(header + 34) == audio::kBitsPerSample &&
      memcmp(header + 36, "data", 4) == 0 &&
      readU32(header + 40) == expectedBytes && expectedBytes % 2 == 0;
  if (valid) audioBytes = expectedBytes;
  return valid;
}

bool SdWavRecorder::parseCaptureName(const String& name, protocol::StreamUuid& stream) {
  String base = name;
  const int slash = base.lastIndexOf('/');
  if (slash >= 0) base = base.substring(slash + 1);
  if (base.length() != 36 || !base.endsWith(".wav")) return false;
  auto nibble = [](char value) -> int {
    if (value >= '0' && value <= '9') return value - '0';
    if (value >= 'a' && value <= 'f') return value - 'a' + 10;
    if (value >= 'A' && value <= 'F') return value - 'A' + 10;
    return -1;
  };
  for (size_t i = 0; i < stream.size(); ++i) {
    const int high = nibble(base[i * 2]);
    const int low = nibble(base[i * 2 + 1]);
    if (high < 0 || low < 0) return false;
    stream[i] = static_cast<uint8_t>((high << 4) | low);
  }
  return true;
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
