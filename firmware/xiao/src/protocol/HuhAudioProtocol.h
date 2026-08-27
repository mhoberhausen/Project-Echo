#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

#include "../audio/AudioFrame.h"

namespace huh::protocol {

constexpr uint8_t kProtocolVersion = 1;
constexpr size_t kEnvelopeSize = 12;
constexpr uint32_t kMaximumPayloadBytes = 65536;

enum class MessageType : uint8_t {
  kHello = 1,
  kStart = 2,
  kAudio = 3,
  kHeartbeat = 4,
  kStop = 5,
  kError = 6,
  kAck = 7,
  kFetch = 8,
  kFileChunk = 9,
  kFileEnd = 10,
};

enum class StopReason : uint8_t {
  kUserStop = 1,
  kPause = 2,
  kDisconnect = 3,
  kStorageFailure = 4,
  kDeviceReboot = 5,
  kCaptureFailure = 6,
  kControlLeaseExpired = 7,
  kUnknown = 255,
};

using StreamUuid = std::array<uint8_t, 16>;

struct HelloInfo {
  std::string deviceId;
  std::string displayName;
  std::string manufacturer;
  std::string model;
  std::string firmwareVersion;
  uint32_t sampleRate = audio::kSampleRateHz;
  uint8_t channels = audio::kChannelCount;
  uint8_t sampleWidthBits = audio::kBitsPerSample;
  uint16_t samplesPerFrame = audio::kSamplesPerFrame;
};

bool encodeMessage(MessageType type, const uint8_t* payload,
                   size_t payloadLength, std::vector<uint8_t>& output);
bool encodeHello(const HelloInfo& info, std::vector<uint8_t>& output);
bool encodeStart(const StreamUuid& stream, std::vector<uint8_t>& output);
bool encodeAudio(const StreamUuid& stream, uint64_t sequence,
                 uint64_t firstSampleIndex, const audio::AudioFrame& frame,
                 std::vector<uint8_t>& output);
bool encodeHeartbeat(const StreamUuid& stream, uint64_t lastSequence,
                     std::vector<uint8_t>& output);
bool encodeStop(const StreamUuid& stream, StopReason reason,
                std::vector<uint8_t>& output);
bool encodeError(uint16_t code, const std::string& safeMessage,
                 std::vector<uint8_t>& output);
bool encodeAck(const StreamUuid& stream, std::vector<uint8_t>& output);
bool encodeFileChunk(const StreamUuid& stream, uint32_t offset,
                     const uint8_t* bytes, size_t length,
                     std::vector<uint8_t>& output);
bool encodeFileEnd(const StreamUuid& stream, uint32_t totalBytes,
                   std::vector<uint8_t>& output);

class AudioStreamSession {
 public:
  explicit AudioStreamSession(const StreamUuid& stream) : stream_(stream) {}

  bool encodeStartMessage(std::vector<uint8_t>& output) const;
  bool encodeNextAudio(const audio::AudioFrame& frame,
                       std::vector<uint8_t>& output);
  bool encodeHeartbeatMessage(std::vector<uint8_t>& output) const;
  bool encodeStopMessage(StopReason reason, std::vector<uint8_t>& output) const;

  const StreamUuid& uuid() const { return stream_; }
  uint64_t nextSequence() const { return nextSequence_; }
  uint64_t nextSampleIndex() const { return nextSampleIndex_; }

 private:
  StreamUuid stream_;
  uint64_t nextSequence_ = 0;
  uint64_t nextSampleIndex_ = 0;
};

}  // namespace huh::protocol
