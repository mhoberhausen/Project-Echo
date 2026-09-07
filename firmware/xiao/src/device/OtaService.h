#pragma once

#include <Arduino.h>

namespace huh::device {

class OtaService {
 public:
  void setNetworkAvailable(bool available);
  void poll(bool captureIdle);
  bool isReady() const { return ready_; }
  bool isUpdating() const { return updating_; }

 private:
  void begin();
  void end();

  bool networkAvailable_ = false;
  bool ready_ = false;
  bool updating_ = false;
};

}  // namespace huh::device
