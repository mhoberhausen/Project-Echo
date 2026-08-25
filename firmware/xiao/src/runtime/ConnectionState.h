#pragma once

#include <cstdint>

namespace huh::runtime {

enum class ConnectionState : uint8_t {
  kUnpaired,
  kDisconnected,
  kConnecting,
  kReady,
  kStreaming,
  kReconnecting,
  kPaused,
  kError,
};

const char* connectionStateName(ConnectionState state);

}  // namespace huh::runtime
