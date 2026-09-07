#include "DiagnosticServer.h"

#include <WiFi.h>
#include <cerrno>
#include <lwip/sockets.h>

#include "../../include/DeviceConfig.h"

namespace huh::transport {

DiagnosticServer::DiagnosticServer(uint16_t port) : port_(port), server_(port) {}

void DiagnosticServer::setNetworkAvailable(bool available) {
  if (networkAvailable_ == available) return;
  networkAvailable_ = available;
  if (!available) {
    closeClient();
    server_.end();
    listening_ = false;
    return;
  }
  server_.begin();
  server_.setNoDelay(true);
  listening_ = true;
  Serial.printf("Diagnostics socket ready on %s:%u.\n",
                WiFi.localIP().toString().c_str(), port_);
}

void DiagnosticServer::closeClient() {
  if (client_.fd() >= 0) client_.stop();
  client_ = NetworkClient();
}

void DiagnosticServer::sendLine(const char* line) {
  if (!client_.connected()) return;
  const size_t length = strlen(line);
  const int sent = lwip_send(client_.fd(), line, length, MSG_DONTWAIT);
  if (sent < 0 && errno != EAGAIN && errno != EWOULDBLOCK) closeClient();
}

void DiagnosticServer::publishEvent(const char* category, const String& event) {
  if (!client_.connected()) return;
  char line[192];
  snprintf(line, sizeof(line), "uptime_ms=%lu event=%s %s\r\n",
           static_cast<unsigned long>(millis()), category, event.c_str());
  sendLine(line);
}

void DiagnosticServer::poll(const DiagnosticSnapshot& snapshot) {
  if (!networkAvailable_ || !listening_) return;

  if (client_.fd() >= 0 && !client_.connected()) closeClient();
  if (client_.fd() < 0) {
    NetworkClient candidate = server_.accept();
    if (candidate) {
      client_ = candidate;
      client_.setNoDelay(true);
      sendLine("Project Echo diagnostics connected (mDNS disabled)\r\n");
      lastReportAt_ = 0;
    }
  }

  if (!client_.connected()) return;
  const uint32_t now = millis();
  if (lastReportAt_ != 0 && now - lastReportAt_ < config::kDiagnosticIntervalMs) return;
  lastReportAt_ = now;

  char line[256];
  snprintf(line, sizeof(line),
           "uptime_ms=%lu state=%s ip=%s rssi=%ld sd=%s ota=%s completed=%lu interrupted=%lu heap=%lu\r\n",
           static_cast<unsigned long>(now), snapshot.state,
           WiFi.localIP().toString().c_str(), static_cast<long>(snapshot.wifiRssi),
           snapshot.sdMounted ? "mounted" : "unavailable",
           snapshot.otaReady ? "ready" : "off",
           static_cast<unsigned long>(snapshot.completedStreams),
           static_cast<unsigned long>(snapshot.interruptedStreams),
           static_cast<unsigned long>(ESP.getFreeHeap()));
  sendLine(line);
}

}  // namespace huh::transport
