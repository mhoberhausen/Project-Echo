#pragma once

#include <stddef.h>
#include <stdint.h>

namespace huh::device {

// Transport-neutral control verbs shared by future BLE and other links.
// Authentication and pairing are intentionally enforced by the transport
// adapter; this parser only validates bounded, versioned messages.
enum class HardwareControlCommand : uint8_t {
  kUnknown, kStatus, kStart, kPause, kResume, kStop, kPlayTestSound
};

struct HardwareControlRequest {
  uint8_t version = 0;
  uint32_t requestId = 0;
  HardwareControlCommand command = HardwareControlCommand::kUnknown;
};

bool parseHardwareControlRequest(const uint8_t* bytes, size_t length,
                                 HardwareControlRequest& request);

}  // namespace huh::device
