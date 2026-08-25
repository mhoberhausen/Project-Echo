#pragma once

namespace huh::transport {

// UUIDs and authenticated command payloads are a shared Android/firmware decision.
class BleControlService {
 public:
  bool isConfigured() const { return false; }
};

}  // namespace huh::transport
