#pragma once

namespace huh::device {

// Persistence is intentionally unavailable until the pairing/authentication contract is
// frozen with Android. This prevents an unauthenticated TCP placeholder becoming product
// behavior by accident.
class PairingStore {
 public:
  bool hasAssociation() const { return false; }
};

}  // namespace huh::device
