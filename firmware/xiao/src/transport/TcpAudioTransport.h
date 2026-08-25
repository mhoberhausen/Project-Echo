#pragma once

#include <Arduino.h>
#include <NetworkClient.h>
#include <NetworkServer.h>

#include <memory>
#include <vector>

#include "../protocol/HuhAudioProtocol.h"
#include "../protocol/HuhMessageWriter.h"
#include "../runtime/StreamingController.h"

namespace huh::transport {

class TcpAudioTransport : public protocol::ByteSink {
 public:
  TcpAudioTransport(uint16_t port, const protocol::HelloInfo& identity,
                    runtime::StreamingController& controller);
  void setNetworkAvailable(bool available);
  void poll();
  void stop(protocol::StopReason reason);
  bool isListening() const { return listening_; }
  bool hasClient() { return client_.connected(); }
  uint32_t completedStreams() const { return completedStreams_; }
  uint32_t interruptedStreams() const { return interruptedStreams_; }
  bool shouldPollImmediately() const;
  size_t write(const uint8_t* bytes, size_t length) override;

 private:
  bool send(const std::vector<uint8_t>& message);
  void queuePending(const std::vector<uint8_t>& message, size_t audioFrames);
  bool flushPending();
  void clearPending();
  bool acceptClient();
  void handleClient();
  void failCapture(uint16_t errorCode, const char* safeMessage);
  void printDiagnostics();
  void configureClientSocket();
  void endClient(protocol::StopReason reason, bool sendStop, bool interrupted);

  uint16_t port_;
  protocol::HelloInfo identity_;
  runtime::StreamingController& controller_;
  NetworkServer server_;
  NetworkClient client_;
  std::unique_ptr<protocol::AudioStreamSession> stream_;
  protocol::HuhMessageWriter writer_;
  std::vector<uint8_t> message_;
  std::vector<uint8_t> pendingMessage_;
  size_t pendingOffset_ = 0;
  size_t pendingAudioFrames_ = 0;
  uint32_t pendingStartedAt_ = 0;
  bool networkAvailable_ = false;
  bool listening_ = false;
  uint32_t lastHeartbeatAt_ = 0;
  uint32_t observedOverruns_ = 0;
  uint32_t completedStreams_ = 0;
  uint32_t interruptedStreams_ = 0;
  uint32_t streamStartedAt_ = 0;
  uint32_t lastDiagnosticAt_ = 0;
  uint32_t capturedAtStart_ = 0;
  uint32_t transmittedFrames_ = 0;
  uint32_t transmittedBytes_ = 0;
  uint32_t totalWriteDurationMs_ = 0;
  uint32_t maximumWriteDurationMs_ = 0;
  uint32_t partialWriteCount_ = 0;
  uint32_t writeFailureCount_ = 0;
};

}  // namespace huh::transport
