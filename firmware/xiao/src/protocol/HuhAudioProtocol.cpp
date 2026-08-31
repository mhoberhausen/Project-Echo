#include "HuhAudioProtocol.h"

#include <limits>

namespace huh::protocol {
namespace {

void appendU16(std::vector<uint8_t>& bytes, uint16_t value) {
  bytes.push_back(static_cast<uint8_t>(value >> 8));
  bytes.push_back(static_cast<uint8_t>(value));
}

void appendU32(std::vector<uint8_t>& bytes, uint32_t value) {
  bytes.push_back(static_cast<uint8_t>(value >> 24));
  bytes.push_back(static_cast<uint8_t>(value >> 16));
  bytes.push_back(static_cast<uint8_t>(value >> 8));
  bytes.push_back(static_cast<uint8_t>(value));
}

void appendU64(std::vector<uint8_t>& bytes, uint64_t value) {
  for (int shift = 56; shift >= 0; shift -= 8) {
    bytes.push_back(static_cast<uint8_t>(value >> shift));
  }
}

bool appendString(std::vector<uint8_t>& bytes, const std::string& value) {
  if (value.size() > std::numeric_limits<uint16_t>::max()) return false;
  appendU16(bytes, static_cast<uint16_t>(value.size()));
  bytes.insert(bytes.end(), value.begin(), value.end());
  return bytes.size() <= kMaximumPayloadBytes;
}

bool wrap(MessageType type, const std::vector<uint8_t>& payload,
          std::vector<uint8_t>& output) {
  return encodeMessage(type, payload.data(), payload.size(), output);
}

bool beginMessage(MessageType type, size_t payloadLength,
                  std::vector<uint8_t>& output) {
  if (payloadLength > kMaximumPayloadBytes) {
    output.clear();
    return false;
  }

  output.clear();
  output.reserve(kEnvelopeSize + payloadLength);
  output.insert(output.end(), {'H', 'U', 'H', '1'});
  output.push_back(kProtocolVersion);
  output.push_back(static_cast<uint8_t>(type));
  appendU16(output, 0);  // Flags are zero in v1.
  appendU32(output, static_cast<uint32_t>(payloadLength));
  return true;
}

}  // namespace

bool encodeMessage(MessageType type, const uint8_t* payload,
                   size_t payloadLength, std::vector<uint8_t>& output) {
  if (payloadLength > kMaximumPayloadBytes ||
      (payloadLength > 0 && payload == nullptr)) {
    output.clear();
    return false;
  }

  beginMessage(type, payloadLength, output);
  if (payloadLength > 0) output.insert(output.end(), payload, payload + payloadLength);
  return true;
}

bool encodeHello(const HelloInfo& info, std::vector<uint8_t>& output) {
  std::vector<uint8_t> payload;
  if (!appendString(payload, info.deviceId) ||
      !appendString(payload, info.displayName) ||
      !appendString(payload, info.manufacturer) ||
      !appendString(payload, info.model) ||
      !appendString(payload, info.firmwareVersion)) {
    output.clear();
    return false;
  }
  appendU32(payload, info.sampleRate);
  payload.push_back(info.channels);
  payload.push_back(info.sampleWidthBits);
  appendU16(payload, info.samplesPerFrame);
  return wrap(MessageType::kHello, payload, output);
}

bool encodeStart(const StreamUuid& stream, std::vector<uint8_t>& output) {
  return encodeMessage(MessageType::kStart, stream.data(), stream.size(), output);
}

bool encodeAudio(const StreamUuid& stream, uint64_t sequence,
                 uint64_t firstSampleIndex, const audio::AudioFrame& frame,
                 std::vector<uint8_t>& output) {
  constexpr size_t kAudioPayloadBytes = 16 + 8 + 8 + audio::kBytesPerFrame;
  if (!beginMessage(MessageType::kAudio, kAudioPayloadBytes, output)) {
    return false;
  }
  output.insert(output.end(), stream.begin(), stream.end());
  appendU64(output, sequence);
  appendU64(output, firstSampleIndex);
  output.insert(output.end(), frame.pcmLittleEndian.begin(),
                frame.pcmLittleEndian.end());
  return true;
}

bool encodeHeartbeat(const StreamUuid& stream, uint64_t lastSequence,
                     std::vector<uint8_t>& output) {
  std::vector<uint8_t> payload(stream.begin(), stream.end());
  appendU64(payload, lastSequence);
  return wrap(MessageType::kHeartbeat, payload, output);
}

bool encodeStop(const StreamUuid& stream, StopReason reason,
                std::vector<uint8_t>& output) {
  std::vector<uint8_t> payload(stream.begin(), stream.end());
  payload.push_back(static_cast<uint8_t>(reason));
  return wrap(MessageType::kStop, payload, output);
}

bool encodeError(uint16_t code, const std::string& safeMessage,
                 std::vector<uint8_t>& output) {
  std::vector<uint8_t> payload;
  appendU16(payload, code);
  if (!appendString(payload, safeMessage)) {
    output.clear();
    return false;
  }
  return wrap(MessageType::kError, payload, output);
}

bool encodeAck(const StreamUuid& stream, std::vector<uint8_t>& output) {
  return encodeMessage(MessageType::kAck, stream.data(), stream.size(), output);
}

bool encodeFileChunk(const StreamUuid& stream, uint32_t offset,
                     const uint8_t* bytes, size_t length,
                     std::vector<uint8_t>& output) {
  if (bytes == nullptr || length == 0 || length > kMaximumPayloadBytes - 20) {
    output.clear();
    return false;
  }
  if (!beginMessage(MessageType::kFileChunk, 20 + length, output)) return false;
  output.insert(output.end(), stream.begin(), stream.end());
  appendU32(output, offset);
  output.insert(output.end(), bytes, bytes + length);
  return true;
}

bool encodeFileEnd(const StreamUuid& stream, uint32_t totalBytes,
                   std::vector<uint8_t>& output) {
  std::vector<uint8_t> payload(stream.begin(), stream.end());
  appendU32(payload, totalBytes);
  return wrap(MessageType::kFileEnd, payload, output);
}

bool encodeCaptureInfo(const StreamUuid& stream, uint32_t totalBytes,
                       std::vector<uint8_t>& output) {
  std::vector<uint8_t> payload(stream.begin(), stream.end());
  appendU32(payload, totalBytes);
  return wrap(MessageType::kCaptureInfo, payload, output);
}

bool encodeCaptureListEnd(uint16_t count, std::vector<uint8_t>& output) {
  std::vector<uint8_t> payload;
  appendU16(payload, count);
  return wrap(MessageType::kCaptureListEnd, payload, output);
}

bool AudioStreamSession::encodeStartMessage(std::vector<uint8_t>& output) const {
  return encodeStart(stream_, output);
}

bool AudioStreamSession::encodeNextAudio(const audio::AudioFrame& frame,
                                         std::vector<uint8_t>& output) {
  if (!encodeAudio(stream_, nextSequence_, nextSampleIndex_, frame, output)) {
    return false;
  }
  ++nextSequence_;
  nextSampleIndex_ += audio::kSamplesPerFrame;
  return true;
}

bool AudioStreamSession::encodeHeartbeatMessage(
    std::vector<uint8_t>& output) const {
  const uint64_t lastSequence = nextSequence_ == 0 ? 0 : nextSequence_ - 1;
  return encodeHeartbeat(stream_, lastSequence, output);
}

bool AudioStreamSession::encodeStopMessage(
    StopReason reason, std::vector<uint8_t>& output) const {
  return encodeStop(stream_, reason, output);
}

}  // namespace huh::protocol
