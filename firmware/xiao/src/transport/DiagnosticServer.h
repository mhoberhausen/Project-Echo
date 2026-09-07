#pragma once

#include <Arduino.h>
#include <NetworkClient.h>
#include <NetworkServer.h>

namespace huh::transport {

struct DiagnosticSnapshot {
  const char* state = "UNKNOWN";
  int32_t wifiRssi = 0;
  bool sdMounted = false;
  bool otaReady = false;
  uint32_t completedStreams = 0;
  uint32_t interruptedStreams = 0;
};

class DiagnosticServer {
 public:
  explicit DiagnosticServer(uint16_t port);
  void setNetworkAvailable(bool available);
  void poll(const DiagnosticSnapshot& snapshot);
  void publishEvent(const char* category, const String& event);
  bool hasClient() const { return client_.fd() >= 0; }

 private:
  void closeClient();
  void sendLine(const char* line);

  uint16_t port_;
  NetworkServer server_;
  NetworkClient client_;
  bool networkAvailable_ = false;
  bool listening_ = false;
  uint32_t lastReportAt_ = 0;
};

}  // namespace huh::transport
