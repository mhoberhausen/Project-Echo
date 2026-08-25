#include "TcpAudioTransport.h"

#include "DeviceConfig.h"
#include "../device/DeviceIdentity.h"

#include <cerrno>
#include <fcntl.h>
#include <lwip/sockets.h>

namespace huh::transport {

TcpAudioTransport::TcpAudioTransport(uint16_t port,
                                     const protocol::HelloInfo& identity,
                                     runtime::StreamingController& controller)
    : port_(port), identity_(identity), controller_(controller), server_(port, 1), writer_(*this) {
  message_.reserve(protocol::kEnvelopeSize + 672);
  pendingMessage_.reserve(
      config::kTcpFramesPerPass * (protocol::kEnvelopeSize + 672));
}

void TcpAudioTransport::setNetworkAvailable(bool available) {
  if (networkAvailable_ == available) return;
  networkAvailable_ = available;
  if (!available) {
    endClient(protocol::StopReason::kDisconnect, false, true);
    server_.end();
    listening_ = false;
    controller_.setState(runtime::ConnectionState::kDisconnected);
  }
}

void TcpAudioTransport::poll() {
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
    controller_.setState(runtime::ConnectionState::kReady);
    Serial.printf("HUH1 TCP server listening on port %u.\n", port_);
  }
  if (!client_.connected()) {
    if (stream_ != nullptr) endClient(protocol::StopReason::kDisconnect, false, true);
    acceptClient();
    return;
  }
  handleClient();
}

bool TcpAudioTransport::acceptClient() {
  NetworkClient candidate = server_.accept();
  if (!candidate) return false;
  client_ = candidate;
  client_.setNoDelay(true);
  client_.setTimeout(250);
  configureClientSocket();
  stream_ = std::make_unique<protocol::AudioStreamSession>(
      device::DeviceIdentity::createStreamUuid());
  observedOverruns_ = controller_.overrunCount();
  streamStartedAt_ = millis();
  lastDiagnosticAt_ = streamStartedAt_;
  capturedAtStart_ = controller_.capturedFrames();
  transmittedFrames_ = 0;
  transmittedBytes_ = 0;
  totalWriteDurationMs_ = 0;
  maximumWriteDurationMs_ = 0;
  partialWriteCount_ = 0;
  writeFailureCount_ = 0;
  clearPending();
  if (!protocol::encodeHello(identity_, message_) || !send(message_) ||
      !stream_->encodeStartMessage(message_) || !send(message_)) {
    endClient(protocol::StopReason::kDisconnect, false, true);
    return false;
  }
  if (!controller_.startCapture(config::kMicClockPin, config::kMicDataPin)) {
    protocol::encodeError(1, "Microphone capture failed", message_);
    send(message_);
    endClient(protocol::StopReason::kCaptureFailure, true, true);
    return false;
  }
  controller_.setState(runtime::ConnectionState::kStreaming);
  lastHeartbeatAt_ = millis();
  Serial.printf("Android receiver connected from %s.\n", client_.remoteIP().toString().c_str());
  return true;
}

void TcpAudioTransport::handleClient() {
  if (controller_.captureFailed()) {
    failCapture(1, "Microphone capture failed");
    return;
  }
  if (controller_.overrunCount() != observedOverruns_) {
    failCapture(2, "Audio queue overrun");
    return;
  }

  const uint32_t passStartedAt = millis();
  size_t framesThisPass = 0;
  if (!pendingMessage_.empty()) {
    if (!flushPending()) {
      endClient(protocol::StopReason::kDisconnect, false, true);
      return;
    }
    if (!pendingMessage_.empty()) return;  // Resume after socket backpressure.
  }

  pendingStartedAt_ = millis();
  while (framesThisPass < config::kTcpFramesPerPass &&
         millis() - passStartedAt < config::kTcpPassBudgetMs) {
    audio::AudioFrame frame;
    if (!controller_.tryTakeFrame(frame, 0)) break;
    if (!stream_->encodeNextAudio(frame, message_)) {
      endClient(protocol::StopReason::kCaptureFailure, false, true);
      return;
    }
    pendingMessage_.insert(pendingMessage_.end(), message_.begin(), message_.end());
    ++pendingAudioFrames_;
    ++framesThisPass;
  }
  if (!pendingMessage_.empty() && !flushPending()) {
    endClient(protocol::StopReason::kDisconnect, false, true);
    return;
  }

  if (controller_.captureFailed()) {
    failCapture(1, "Microphone capture failed");
    return;
  }
  if (controller_.overrunCount() != observedOverruns_) {
    failCapture(2, "Audio queue overrun");
    return;
  }
  if (pendingMessage_.empty() &&
      millis() - lastHeartbeatAt_ >= config::kHeartbeatIntervalMs) {
    if (!stream_->encodeHeartbeatMessage(message_)) {
      endClient(protocol::StopReason::kCaptureFailure, false, true);
      return;
    }
    queuePending(message_, 0);
    if (!flushPending()) {
      endClient(protocol::StopReason::kDisconnect, false, true);
      return;
    }
    lastHeartbeatAt_ = millis();
  }
  if (millis() - lastDiagnosticAt_ >= config::kStreamDiagnosticIntervalMs) {
    printDiagnostics();
    lastDiagnosticAt_ = millis();
  }
}

void TcpAudioTransport::failCapture(uint16_t errorCode,
                                    const char* safeMessage) {
  const bool controlMessageIsSafe = pendingMessage_.empty() || pendingOffset_ == 0;
  clearPending();
  if (controlMessageIsSafe && client_.connected() &&
      protocol::encodeError(errorCode, safeMessage, message_)) {
    send(message_);  // Best effort; STOP is attempted separately by endClient.
  }
  endClient(protocol::StopReason::kCaptureFailure, controlMessageIsSafe, true);
}

void TcpAudioTransport::printDiagnostics() {
  const uint32_t elapsedMs = millis() - streamStartedAt_;
  const uint32_t captured = controller_.capturedFrames() - capturedAtStart_;
  const uint32_t averageWriteUs = transmittedFrames_ == 0
      ? 0
      : (totalWriteDurationMs_ * 1000UL) / transmittedFrames_;
  const uint32_t capturedFpsTimes10 = elapsedMs == 0
      ? 0
      : (captured * 10000UL) / elapsedMs;
  const uint32_t transmittedFpsTimes10 = elapsedMs == 0
      ? 0
      : (transmittedFrames_ * 10000UL) / elapsedMs;
  Serial.printf(
      "Stream metrics: elapsed_ms=%lu captured=%lu capture_fps_x10=%lu "
      "sent=%lu send_fps_x10=%lu bytes=%lu "
      "queue=%u high_water=%u avg_write_us=%lu max_write_ms=%lu "
      "partial_writes=%lu write_failures=%lu overruns=%lu\n",
      static_cast<unsigned long>(elapsedMs),
      static_cast<unsigned long>(captured),
      static_cast<unsigned long>(capturedFpsTimes10),
      static_cast<unsigned long>(transmittedFrames_),
      static_cast<unsigned long>(transmittedFpsTimes10),
      static_cast<unsigned long>(transmittedBytes_),
      static_cast<unsigned>(controller_.queuedFrames()),
      static_cast<unsigned>(controller_.queueHighWaterMark()),
      static_cast<unsigned long>(averageWriteUs),
      static_cast<unsigned long>(maximumWriteDurationMs_),
      static_cast<unsigned long>(partialWriteCount_),
      static_cast<unsigned long>(writeFailureCount_),
      static_cast<unsigned long>(controller_.overrunCount()));
}

void TcpAudioTransport::configureClientSocket() {
  const int socketFlags = fcntl(client_.fd(), F_GETFL, 0);
  if (socketFlags >= 0) {
    fcntl(client_.fd(), F_SETFL, socketFlags | O_NONBLOCK);
  }
  const int keepalive = 1;
  client_.setSocketOption(SOL_SOCKET, SO_KEEPALIVE, &keepalive,
                          sizeof(keepalive));
  const int idle = config::kTcpKeepaliveIdleSeconds;
  const int interval = config::kTcpKeepaliveIntervalSeconds;
  const int count = config::kTcpKeepaliveProbeCount;
  client_.setSocketOption(IPPROTO_TCP, TCP_KEEPIDLE, &idle, sizeof(idle));
  client_.setSocketOption(IPPROTO_TCP, TCP_KEEPINTVL, &interval, sizeof(interval));
  client_.setSocketOption(IPPROTO_TCP, TCP_KEEPCNT, &count, sizeof(count));
}

void TcpAudioTransport::stop(protocol::StopReason reason) {
  endClient(reason, true, false);
}

void TcpAudioTransport::endClient(protocol::StopReason reason, bool sendStop, bool interrupted) {
  if (stream_ == nullptr && !client_) return;
  if (stream_ != nullptr) printDiagnostics();
  controller_.stopCapture();
  const bool controlMessageIsSafe = pendingMessage_.empty() || pendingOffset_ == 0;
  clearPending();
  if (sendStop && controlMessageIsSafe && client_.connected() && stream_ != nullptr &&
      stream_->encodeStopMessage(reason, message_)) send(message_);
  client_.stop();
  stream_.reset();
  controller_.clearQueuedFrames();
  controller_.setState(runtime::ConnectionState::kReady);
  if (interrupted) ++interruptedStreams_; else ++completedStreams_;
  Serial.printf("Receiver disconnected; completed=%lu interrupted=%lu overruns=%lu\n",
                static_cast<unsigned long>(completedStreams_),
                static_cast<unsigned long>(interruptedStreams_),
                static_cast<unsigned long>(controller_.overrunCount()));
}

bool TcpAudioTransport::send(const std::vector<uint8_t>& message) {
  if (!client_.connected()) return false;
  const bool success = writer_.write(message);
  if (success) {
    transmittedBytes_ += message.size();
  } else {
    ++writeFailureCount_;
  }
  return success;
}

void TcpAudioTransport::queuePending(const std::vector<uint8_t>& message,
                                     size_t audioFrames) {
  pendingMessage_ = message;
  pendingOffset_ = 0;
  pendingAudioFrames_ = audioFrames;
  pendingStartedAt_ = millis();
}

bool TcpAudioTransport::flushPending() {
  if (pendingMessage_.empty()) return true;
  if (!client_.connected()) return false;

  const size_t remaining = pendingMessage_.size() - pendingOffset_;
  const int written = lwip_send(client_.fd(),
                                pendingMessage_.data() + pendingOffset_,
                                remaining, MSG_DONTWAIT);
  if (written > 0) {
    const size_t accepted = static_cast<size_t>(written);
    if (accepted < remaining) ++partialWriteCount_;
    pendingOffset_ += accepted;
    transmittedBytes_ += accepted;
    if (pendingOffset_ == pendingMessage_.size()) {
      if (pendingAudioFrames_ > 0) {
        const uint32_t duration = millis() - pendingStartedAt_;
        totalWriteDurationMs_ += duration;
        if (duration > maximumWriteDurationMs_) maximumWriteDurationMs_ = duration;
        transmittedFrames_ += pendingAudioFrames_;
      }
      clearPending();
    }
    return true;
  }
  if (written < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
    return true;
  }
  ++writeFailureCount_;
  return false;
}

void TcpAudioTransport::clearPending() {
  pendingMessage_.clear();
  pendingOffset_ = 0;
  pendingAudioFrames_ = 0;
  pendingStartedAt_ = 0;
}

size_t TcpAudioTransport::write(const uint8_t* bytes, size_t length) {
  if (!client_.connected()) return 0;
  const size_t written = client_.write(bytes, length);
  if (written < length) ++partialWriteCount_;
  return written;
}

bool TcpAudioTransport::shouldPollImmediately() const {
  return stream_ != nullptr &&
         (!pendingMessage_.empty() || controller_.queuedFrames() > 0);
}

}  // namespace huh::transport
