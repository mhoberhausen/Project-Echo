#include "TcpAudioTransport.h"

#include <algorithm>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <lwip/sockets.h>

#include "DeviceConfig.h"

namespace huh::transport {
namespace {

constexpr size_t kHeaderBytes = protocol::kEnvelopeSize;
constexpr size_t kMaximumInboundBytes = kHeaderBytes + protocol::kMaximumPayloadBytes;

uint32_t readU32(const uint8_t* bytes) {
  return (static_cast<uint32_t>(bytes[0]) << 24) |
         (static_cast<uint32_t>(bytes[1]) << 16) |
         (static_cast<uint32_t>(bytes[2]) << 8) | bytes[3];
}

uint64_t readU64(const uint8_t* bytes) {
  uint64_t value = 0;
  for (size_t index = 0; index < 8; ++index) value = (value << 8) | bytes[index];
  return value;
}

}  // namespace

TcpAudioTransport::TcpAudioTransport(uint16_t port,
                                     const protocol::HelloInfo& identity,
                                     runtime::StreamingController& controller,
                                     storage::SdWavRecorder& recorder)
    : port_(port), identity_(identity), controller_(controller),
      recorder_(recorder), server_(port, 1), writer_(*this) {
  message_.reserve(protocol::kEnvelopeSize + 672);
  pendingMessage_.reserve(protocol::kEnvelopeSize + 4116);
  inbound_.reserve(128);
}

void TcpAudioTransport::setNetworkAvailable(bool available) {
  if (networkAvailable_ == available) return;
  networkAvailable_ = available;
  if (!available) {
    closeClient();
    server_.end();
    listening_ = false;
    if (phase_ == Phase::kTransferring || phase_ == Phase::kAwaitingAck) {
      if (transferFile_) transferFile_.close();
      clearPending();
      phase_ = Phase::kAwaitingFetch;
    }
    controller_.setState(runtime::ConnectionState::kDisconnected);
  }
}

void TcpAudioTransport::poll() {
  if (phase_ == Phase::kCapturing) {
    if (controller_.captureFailed()) {
      fail(3, "Durable capture failed", protocol::StopReason::kStorageFailure);
      return;
    }
    if (millis() - leaseRenewedAt_ >= config::kCaptureLeaseTimeoutMs) {
      Serial.println("Capture lease expired; finalizing microSD recording.");
      finalizeCapture(protocol::StopReason::kControlLeaseExpired, true);
    }
  }

  if (!networkAvailable_) return;
  if (!listening_) {
    server_.begin(port_, 1);
    server_.setNoDelay(true);
    listening_ = static_cast<bool>(server_);
    if (!listening_) {
      controller_.setState(runtime::ConnectionState::kError);
      Serial.println("ERROR: HUH1 TCP server could not start.");
      return;
    }
    if (phase_ == Phase::kIdle) controller_.setState(runtime::ConnectionState::kReady);
    Serial.printf("HUH1 TCP control server listening on port %u.\n", port_);
  }

  if (client_.fd() < 0) {
    if (phase_ == Phase::kIdle || phase_ == Phase::kAwaitingFetch) acceptClient();
    return;
  }
  // Do not call NetworkClient::connected() in the hot path. It performs a
  // recv(MSG_PEEK) that previously starved both control reads and TCP sends.
  if (phase_ != Phase::kCapturing && phase_ != Phase::kTransferring &&
      millis() - lastConnectionCheckAt_ >= 250) {
    lastConnectionCheckAt_ = millis();
    if (!client_.connected()) {
      closeClient();
      if (phase_ == Phase::kAwaitingAck) phase_ = Phase::kAwaitingFetch;
      return;
    }
  }
  if (!readCommands()) {
    closeClient();
    if (phase_ == Phase::kTransferring || phase_ == Phase::kAwaitingAck) {
      if (transferFile_) transferFile_.close();
      clearPending();
      phase_ = Phase::kAwaitingFetch;
    }
    return;
  }
  if (phase_ == Phase::kTransferring) pumpTransfer();
}

bool TcpAudioTransport::acceptClient() {
  NetworkClient candidate = server_.accept();
  if (!candidate) return false;
  client_ = candidate;
  client_.setNoDelay(true);
  client_.setTimeout(250);
  configureClientSocket();
  inbound_.clear();
  clearPending();
  if (!protocol::encodeHello(identity_, message_) || !send(message_)) {
    closeClient();
    return false;
  }
  controller_.setState(runtime::ConnectionState::kReady);
  Serial.printf("Controller connected from %s; awaiting START.\n",
                client_.remoteIP().toString().c_str());
  return true;
}

bool TcpAudioTransport::readCommands() {
  uint8_t bytes[256];
  while (client_.available() > 0) {
    const int count = client_.read(bytes, std::min<int>(client_.available(), sizeof(bytes)));
    if (count < 0) return false;
    inbound_.insert(inbound_.end(), bytes, bytes + count);
    if (inbound_.size() > kMaximumInboundBytes) {
      fail(10, "Control message is too large", protocol::StopReason::kCaptureFailure);
      return false;
    }
  }

  while (inbound_.size() >= kHeaderBytes) {
    const uint8_t* header = inbound_.data();
    if (memcmp(header, "HUH1", 4) != 0 ||
        header[4] != protocol::kProtocolVersion || header[6] != 0 || header[7] != 0) {
      fail(11, "Invalid HUH1 control envelope", protocol::StopReason::kCaptureFailure);
      return false;
    }
    const uint32_t payloadLength = readU32(header + 8);
    if (payloadLength > protocol::kMaximumPayloadBytes) {
      fail(10, "Control payload is too large", protocol::StopReason::kCaptureFailure);
      return false;
    }
    const size_t total = kHeaderBytes + payloadLength;
    if (inbound_.size() < total) break;
    if (!handleCommand(header[5], header + kHeaderBytes, payloadLength)) return false;
    inbound_.erase(inbound_.begin(), inbound_.begin() + total);
  }
  return true;
}

bool TcpAudioTransport::handleCommand(uint8_t rawType, const uint8_t* payload,
                                      size_t length) {
  const auto type = static_cast<protocol::MessageType>(rawType);
  if (type == protocol::MessageType::kStart) {
    if (length != protocol::StreamUuid{}.size() || phase_ != Phase::kIdle) {
      fail(12, "START is not valid in the current state", protocol::StopReason::kCaptureFailure);
      return false;
    }
    protocol::StreamUuid stream;
    memcpy(stream.data(), payload, stream.size());
    return startCapture(stream);
  }
  if (type == protocol::MessageType::kHeartbeat) {
    if (length != 24 || phase_ != Phase::kCapturing || !matchingStream(payload, length)) {
      fail(13, "HEARTBEAT does not own the active capture", protocol::StopReason::kCaptureFailure);
      return false;
    }
    const uint64_t counter = readU64(payload + 16);
    if (hasLeaseCounter_ && counter <= lastLeaseCounter_) return true;
    hasLeaseCounter_ = true;
    lastLeaseCounter_ = counter;
    leaseRenewedAt_ = millis();
    return true;
  }
  if (type == protocol::MessageType::kStop) {
    if (length != 17 || phase_ != Phase::kCapturing || !matchingStream(payload, length)) {
      fail(14, "STOP does not own the active capture", protocol::StopReason::kCaptureFailure);
      return false;
    }
    const auto requested = static_cast<protocol::StopReason>(payload[16]);
    const auto reason = requested == protocol::StopReason::kPause
        ? protocol::StopReason::kPause : protocol::StopReason::kUserStop;
    finalizeCapture(reason, false);
    return true;
  }
  if (type == protocol::MessageType::kAck) {
    if (length != 16 || phase_ != Phase::kAwaitingAck || !matchingStream(payload, length)) {
      fail(15, "ACK does not match the completed capture", protocol::StopReason::kCaptureFailure);
      return false;
    }
    acknowledge();
    return true;
  }
  if (type == protocol::MessageType::kFetch) {
    if (length != 20 ||
        (phase_ != Phase::kIdle && phase_ != Phase::kAwaitingFetch)) {
      fail(17, "FETCH does not match a completed capture",
           protocol::StopReason::kCaptureFailure);
      return false;
    }
    if (!matchingStream(payload, length) && !selectRetainedCapture(payload, length)) {
      fail(17, "FETCH does not match a retained capture",
           protocol::StopReason::kCaptureFailure);
      return false;
    }
    return beginFetch(readU32(payload + 16));
  }
  if (type == protocol::MessageType::kListCaptures) {
    if (length != 0 || (phase_ != Phase::kIdle && phase_ != Phase::kAwaitingFetch)) {
      fail(19, "LIST_CAPTURES is not valid in the current state",
           protocol::StopReason::kCaptureFailure);
      return false;
    }
    return sendCaptureList();
  }
  fail(16, "Unsupported inbound HUH1 message", protocol::StopReason::kCaptureFailure);
  return false;
}

bool TcpAudioTransport::selectRetainedCapture(const uint8_t* payload, size_t length) {
  if (length < protocol::StreamUuid{}.size()) return false;
  protocol::StreamUuid stream{};
  memcpy(stream.data(), payload, stream.size());
  if (!recorder_.selectFinalized(stream)) return false;
  stream_ = std::make_unique<protocol::AudioStreamSession>(stream);
  phase_ = Phase::kAwaitingFetch;
  finalReason_ = protocol::StopReason::kDeviceReboot;
  return true;
}

bool TcpAudioTransport::sendCaptureList() {
  const auto captures = recorder_.finalizedCaptures();
  if (captures.size() > UINT16_MAX) {
    fail(20, "Too many retained captures", protocol::StopReason::kStorageFailure);
    return false;
  }
  for (const auto& capture : captures) {
    if (!protocol::encodeCaptureInfo(capture.stream, capture.audioBytes, message_) ||
        !send(message_)) return false;
  }
  return protocol::encodeCaptureListEnd(static_cast<uint16_t>(captures.size()), message_) &&
         send(message_);
}

bool TcpAudioTransport::startCapture(const protocol::StreamUuid& stream) {
  if (!recorder_.isMounted() || !recorder_.start(stream)) {
    fail(4, "microSD recording could not start", protocol::StopReason::kStorageFailure);
    return false;
  }
  stream_ = std::make_unique<protocol::AudioStreamSession>(stream);
  if (!controller_.startCapture(config::kMicClockPin, config::kMicDataPin)) {
    recorder_.finish();
    fail(1, "Microphone capture failed", protocol::StopReason::kCaptureFailure);
    return false;
  }
  leaseRenewedAt_ = millis();
  lastLeaseCounter_ = 0;
  hasLeaseCounter_ = false;
  phase_ = Phase::kCapturing;
  if (!stream_->encodeStartMessage(message_) || !send(message_)) closeClient();
  controller_.setState(runtime::ConnectionState::kStreaming);
  Serial.println("Capture lease started; recording canonical PCM to microSD.");
  return true;
}

void TcpAudioTransport::finalizeCapture(protocol::StopReason reason,
                                        bool interrupted) {
  if (phase_ != Phase::kCapturing || stream_ == nullptr) return;
  controller_.stopCapture();
  if (!recorder_.finish()) {
    fail(4, "microSD recording could not be finalized",
         protocol::StopReason::kStorageFailure);
    return;
  }
  finalReason_ = reason;
  if (interrupted) ++interruptedStreams_; else ++completedStreams_;
  Serial.printf("Capture stopped; reason=%u completed=%lu interrupted=%lu.\n",
                static_cast<unsigned>(reason),
                static_cast<unsigned long>(completedStreams_),
                static_cast<unsigned long>(interruptedStreams_));
  phase_ = Phase::kAwaitingFetch;
  controller_.setState(runtime::ConnectionState::kReady);
  if (client_.fd() >= 0 && stream_->encodeStopMessage(reason, message_)) {
    send(message_);
  }
  Serial.println("Recording ready; awaiting FETCH while retained on microSD.");
}

bool TcpAudioTransport::beginFetch(uint32_t offset) {
  if (offset > recorder_.audioBytes()) {
    fail(18, "FETCH offset exceeds the recording size",
         protocol::StopReason::kStorageFailure);
    return false;
  }
  transferFile_ = recorder_.openFinalized();
  if (!transferFile_ || !transferFile_.seek(44 + offset)) {
    fail(4, "Finalized recording could not be opened at the requested offset",
         protocol::StopReason::kStorageFailure);
    return false;
  }
  transferOffset_ = offset;
  transferEndQueued_ = false;
  phase_ = Phase::kTransferring;
  Serial.printf("Transferring recording from byte %lu.\n",
                static_cast<unsigned long>(offset));
  return true;
}

void TcpAudioTransport::pumpTransfer() {
  if (!pendingMessage_.empty()) {
    if (!flushPending()) {
      closeClient();
      if (transferFile_) transferFile_.close();
      clearPending();
      phase_ = Phase::kAwaitingFetch;
      return;
    }
    if (!pendingMessage_.empty()) return;
    if (transferEndQueued_) {
      phase_ = Phase::kAwaitingAck;
      controller_.setState(runtime::ConnectionState::kReady);
      Serial.println("Recording transferred; awaiting ACK before SD deletion.");
      return;
    }
  }

  uint8_t chunk[4096];
  const size_t count = transferFile_.read(chunk, sizeof(chunk));
  if (count > 0) {
    if (!protocol::encodeFileChunk(stream_->uuid(), transferOffset_, chunk,
                                   count, message_)) {
      fail(5, "Recording transfer encode failed", protocol::StopReason::kStorageFailure);
      return;
    }
    transferOffset_ += static_cast<uint32_t>(count);
    pendingMessage_ = message_;
    pendingOffset_ = 0;
    if (!flushPending()) {
      closeClient();
      if (transferFile_) transferFile_.close();
      clearPending();
      phase_ = Phase::kAwaitingFetch;
    }
    return;
  }
  transferFile_.close();
  if (!protocol::encodeFileEnd(stream_->uuid(), recorder_.audioBytes(), message_)) {
    fail(5, "Recording transfer could not finish", protocol::StopReason::kStorageFailure);
    return;
  }
  pendingMessage_ = message_;
  pendingOffset_ = 0;
  transferEndQueued_ = true;
  if (!flushPending()) {
    closeClient();
    clearPending();
    phase_ = Phase::kAwaitingFetch;
  } else if (pendingMessage_.empty()) {
    phase_ = Phase::kAwaitingAck;
    controller_.setState(runtime::ConnectionState::kReady);
    Serial.println("Recording transferred; awaiting ACK before SD deletion.");
  }
}

void TcpAudioTransport::acknowledge() {
  if (!recorder_.removeFinalized()) {
    protocol::encodeError(6, "Transferred recording could not be deleted", message_);
    send(message_);
  }
  resetStream(false);
  controller_.setState(runtime::ConnectionState::kReady);
}

void TcpAudioTransport::fail(uint16_t errorCode, const char* safeMessage,
                             protocol::StopReason reason) {
  Serial.printf("ERROR: %s\n", safeMessage);
  if (client_.fd() >= 0 && protocol::encodeError(errorCode, safeMessage, message_)) send(message_);
  if (phase_ == Phase::kCapturing) {
    controller_.stopCapture();
    recorder_.finish();
  }
  if (client_.fd() >= 0 && stream_ && stream_->encodeStopMessage(reason, message_)) send(message_);
  closeClient();
  resetStream(false);
  controller_.setState(runtime::ConnectionState::kError);
}

bool TcpAudioTransport::matchingStream(const uint8_t* payload, size_t length) const {
  return stream_ != nullptr && length >= stream_->uuid().size() &&
         memcmp(payload, stream_->uuid().data(), stream_->uuid().size()) == 0;
}

void TcpAudioTransport::stop(protocol::StopReason reason) {
  if (phase_ == Phase::kCapturing) finalizeCapture(reason, false);
}

void TcpAudioTransport::configureClientSocket() {
  const int flags = fcntl(client_.fd(), F_GETFL, 0);
  if (flags >= 0) fcntl(client_.fd(), F_SETFL, flags | O_NONBLOCK);
  const int keepalive = 1;
  client_.setSocketOption(SOL_SOCKET, SO_KEEPALIVE, &keepalive, sizeof(keepalive));
  const int idle = config::kTcpKeepaliveIdleSeconds;
  const int interval = config::kTcpKeepaliveIntervalSeconds;
  const int count = config::kTcpKeepaliveProbeCount;
  client_.setSocketOption(IPPROTO_TCP, TCP_KEEPIDLE, &idle, sizeof(idle));
  client_.setSocketOption(IPPROTO_TCP, TCP_KEEPINTVL, &interval, sizeof(interval));
  client_.setSocketOption(IPPROTO_TCP, TCP_KEEPCNT, &count, sizeof(count));
}

void TcpAudioTransport::closeClient() {
  if (client_.fd() >= 0) client_.stop();
  inbound_.clear();
  clearPending();
}

void TcpAudioTransport::resetStream(bool removeFile) {
  if (transferFile_) transferFile_.close();
  if (removeFile) recorder_.removeFinalized();
  stream_.reset();
  phase_ = Phase::kIdle;
  transferEndQueued_ = false;
  transferOffset_ = 0;
  finalReason_ = protocol::StopReason::kUnknown;
  hasLeaseCounter_ = false;
  controller_.clearQueuedFrames();
}

bool TcpAudioTransport::send(const std::vector<uint8_t>& message) {
  return client_.fd() >= 0 && writer_.write(message);
}

bool TcpAudioTransport::flushPending() {
  if (pendingMessage_.empty()) return true;
  if (client_.fd() < 0) return false;
  const size_t remaining = pendingMessage_.size() - pendingOffset_;
  const int written = lwip_send(client_.fd(), pendingMessage_.data() + pendingOffset_,
                                remaining, MSG_DONTWAIT);
  if (written > 0) {
    pendingOffset_ += static_cast<size_t>(written);
    if (pendingOffset_ == pendingMessage_.size()) clearPending();
    return true;
  }
  if (written < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) return true;
  return false;
}

void TcpAudioTransport::clearPending() {
  pendingMessage_.clear();
  pendingOffset_ = 0;
}

size_t TcpAudioTransport::write(const uint8_t* bytes, size_t length) {
  return client_.fd() >= 0 ? client_.write(bytes, length) : 0;
}

bool TcpAudioTransport::shouldPollImmediately() const {
  // SD transfer can fill lwIP's send window in only a few 4 KiB chunks. A real
  // delay in the Arduino loop lets the Wi-Fi/lwIP tasks process peer ACKs and
  // reopen that window; taskYIELD alone only yields to equal-priority work and
  // can leave a non-blocking transfer stuck in EAGAIN indefinitely.
  return !inbound_.empty();
}

}  // namespace huh::transport
