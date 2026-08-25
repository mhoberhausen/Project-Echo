#pragma once

#include <Arduino.h>

#include "../protocol/HuhAudioProtocol.h"

namespace huh::device {

class DeviceIdentity {
 public:
  static protocol::HelloInfo helloInfo();
  static protocol::StreamUuid createStreamUuid();
};

}  // namespace huh::device
