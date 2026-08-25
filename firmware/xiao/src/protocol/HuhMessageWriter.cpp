#include "HuhMessageWriter.h"

namespace huh::protocol {

bool HuhMessageWriter::write(const std::vector<uint8_t>& message) {
  size_t offset = 0;
  while (offset < message.size()) {
    const size_t written = sink_.write(message.data() + offset,
                                       message.size() - offset);
    if (written == 0 || written > message.size() - offset) return false;
    offset += written;
  }
  return true;
}

}  // namespace huh::protocol

