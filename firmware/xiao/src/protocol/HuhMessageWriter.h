#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace huh::protocol {

class ByteSink {
 public:
  virtual ~ByteSink() = default;
  virtual size_t write(const uint8_t* bytes, size_t length) = 0;
};

class HuhMessageWriter {
 public:
  explicit HuhMessageWriter(ByteSink& sink) : sink_(sink) {}

  // Handles short writes. A zero-byte write is treated as transport failure.
  bool write(const std::vector<uint8_t>& message);

 private:
  ByteSink& sink_;
};

}  // namespace huh::protocol
