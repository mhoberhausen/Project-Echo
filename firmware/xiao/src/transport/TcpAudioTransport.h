#pragma once

#include <Arduino.h>
#include <FS.h>
#include <NetworkClient.h>
#include <NetworkServer.h>

#include <memory>
#include <vector>

#include "../protocol/HuhAudioProtocol.h"
#include "../protocol/HuhMessageWriter.h"
#include "../runtime/StreamingController.h"
#include "../storage/SdWavRecorder.h"

namespace huh::transport {

class TcpAudioTransport : public protocol::ByteSink {
 public:
  TcpAudioTransport(uint16_t port, const protocol::HelloInfo& identity,
                    runtime::StreamingController& controller,
                    storage::SdWavRecorder& recorder);
  void setNetworkAvailable(bool available);
  void poll();
  void stop(protocol::StopReason reason);
  bool isListening() const { return listening_; }
  bool hasClient() const { return client_.fd() >= 0; }
  bool isCapturing() const { return phase_ == Phase::kCapturing; }
  uint32_t completedStreams() const { return completedStreams_; }
  uint32_t interruptedStreams() const { return interruptedStreams_; }
  bool shouldPollImmediately() const;
  size_t write(const uint8_t* bytes, size_t length) override;

 private:
  enum class Phase : uint8_t {
    kIdle,
    kCapturing,
    kAwaitingFetch,
    kTransferring,
    kAwaitingAck,
  };

  bool send(const std::vector<uint8_t>& message);
  bool flushPending();
  void clearPending();
  bool acceptClient();
  bool readCommands();
  bool handleCommand(uint8_t type, const uint8_t* payload, size_t length);
  bool startCapture(const protocol::StreamUuid& stream);
  void finalizeCapture(protocol::StopReason reason, bool interrupted);
  void pumpTransfer();
  void acknowledge();
  void fail(uint16_t errorCode, const char* safeMessage,
            protocol::StopReason reason);
  void configureClientSocket();
  void closeClient();
  void resetStream(bool removeFile);
  bool matchingStream(const uint8_t* payload, size_t length) const;
  bool beginFetch(uint32_t offset);
  bool sendCaptureList();
  bool selectRetainedCapture(const uint8_t* payload, size_t length);

  uint16_t port_;
  protocol::HelloInfo identity_;
  runtime::StreamingController& controller_;
  storage::SdWavRecorder& recorder_;
  NetworkServer server_;
  NetworkClient client_;
  Phase phase_ = Phase::kIdle;
  std::unique_ptr<protocol::AudioStreamSession> stream_;
  protocol::HuhMessageWriter writer_;
  File transferFile_;
  std::vector<uint8_t> message_;
  std::vector<uint8_t> pendingMessage_;
  std::vector<uint8_t> inbound_;
  size_t pendingOffset_ = 0;
  bool transferEndQueued_ = false;
  uint32_t transferOffset_ = 0;
  bool networkAvailable_ = false;
  bool listening_ = false;
  uint32_t leaseRenewedAt_ = 0;
  uint64_t lastLeaseCounter_ = 0;
  bool hasLeaseCounter_ = false;
  uint32_t lastConnectionCheckAt_ = 0;
  protocol::StopReason finalReason_ = protocol::StopReason::kUnknown;
  uint32_t completedStreams_ = 0;
  uint32_t interruptedStreams_ = 0;
};

}  // namespace huh::transport
