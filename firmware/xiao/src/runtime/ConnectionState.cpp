#include "ConnectionState.h"

namespace huh::runtime {

const char* connectionStateName(ConnectionState state) {
  switch (state) {
    case ConnectionState::kUnpaired: return "UNPAIRED";
    case ConnectionState::kDisconnected: return "DISCONNECTED";
    case ConnectionState::kConnecting: return "CONNECTING";
    case ConnectionState::kReady: return "READY";
    case ConnectionState::kStreaming: return "STREAMING";
    case ConnectionState::kReconnecting: return "RECONNECTING";
    case ConnectionState::kPaused: return "PAUSED";
    case ConnectionState::kError: return "ERROR";
  }
  return "UNKNOWN";
}

}  // namespace huh::runtime
