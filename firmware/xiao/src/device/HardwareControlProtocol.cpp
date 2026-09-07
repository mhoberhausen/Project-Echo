#include "HardwareControlProtocol.h"

#include <string.h>

namespace huh::device {

bool parseHardwareControlRequest(const uint8_t* bytes, size_t length,
                                 HardwareControlRequest& request) {
  // HHC1 | request id (u32 LE) | ASCII verb, bounded to 16 bytes.
  if (bytes == nullptr || length < 8 || length > 24 || memcmp(bytes, "HHC1", 4) != 0) return false;
  request.version = 1;
  request.requestId = static_cast<uint32_t>(bytes[4]) |
                      (static_cast<uint32_t>(bytes[5]) << 8) |
                      (static_cast<uint32_t>(bytes[6]) << 16) |
                      (static_cast<uint32_t>(bytes[7]) << 24);
  const size_t verbLength = length - 8;
  if (verbLength == 6 && memcmp(bytes + 8, "STATUS", 6) == 0) request.command = HardwareControlCommand::kStatus;
  else if (verbLength == 5 && memcmp(bytes + 8, "START", 5) == 0) request.command = HardwareControlCommand::kStart;
  else if (verbLength == 5 && memcmp(bytes + 8, "PAUSE", 5) == 0) request.command = HardwareControlCommand::kPause;
  else if (verbLength == 6 && memcmp(bytes + 8, "RESUME", 6) == 0) request.command = HardwareControlCommand::kResume;
  else if (verbLength == 4 && memcmp(bytes + 8, "STOP", 4) == 0) request.command = HardwareControlCommand::kStop;
  else if (verbLength == 15 && memcmp(bytes + 8, "PLAY_TEST_SOUND", 15) == 0) request.command = HardwareControlCommand::kPlayTestSound;
  else return false;
  return true;
}

}  // namespace huh::device
