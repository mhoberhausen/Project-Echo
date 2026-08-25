#include <unity.h>

#include <algorithm>
#include <array>
#include <cstdint>
#include <vector>

#include "audio/AudioFrame.h"
#include "protocol/HuhAudioProtocol.h"
#include "protocol/HuhMessageWriter.h"

using huh::audio::AudioFrame;
using huh::protocol::MessageType;
using huh::protocol::StreamUuid;

namespace {

StreamUuid knownStream() {
  StreamUuid stream{};
  for (size_t index = 0; index < stream.size(); ++index) {
    stream[index] = static_cast<uint8_t>(index);
  }
  return stream;
}

void assertEnvelope(const std::vector<uint8_t>& bytes, MessageType type,
                    uint32_t payloadLength) {
  TEST_ASSERT_GREATER_OR_EQUAL(huh::protocol::kEnvelopeSize, bytes.size());
  TEST_ASSERT_EQUAL_UINT8('H', bytes[0]);
  TEST_ASSERT_EQUAL_UINT8('U', bytes[1]);
  TEST_ASSERT_EQUAL_UINT8('H', bytes[2]);
  TEST_ASSERT_EQUAL_UINT8('1', bytes[3]);
  TEST_ASSERT_EQUAL_UINT8(1, bytes[4]);
  TEST_ASSERT_EQUAL_UINT8(static_cast<uint8_t>(type), bytes[5]);
  TEST_ASSERT_EQUAL_UINT8(0, bytes[6]);
  TEST_ASSERT_EQUAL_UINT8(0, bytes[7]);
  const uint32_t actualLength = (static_cast<uint32_t>(bytes[8]) << 24) |
                                (static_cast<uint32_t>(bytes[9]) << 16) |
                                (static_cast<uint32_t>(bytes[10]) << 8) |
                                bytes[11];
  TEST_ASSERT_EQUAL_UINT32(payloadLength, actualLength);
  TEST_ASSERT_EQUAL_UINT32(huh::protocol::kEnvelopeSize + payloadLength,
                           bytes.size());
}

void testFrameAssemblerKeepsShortReadsIncomplete() {
  huh::audio::AudioFrameAssembler assembler;
  AudioFrame frame;
  std::array<uint8_t, huh::audio::kBytesPerFrame> source{};
  for (size_t index = 0; index < source.size(); ++index) {
    source[index] = static_cast<uint8_t>(index & 0xFF);
  }

  TEST_ASSERT_FALSE(assembler.append(source.data(), 13, frame));
  TEST_ASSERT_EQUAL_UINT32(13, assembler.filledBytes());
  TEST_ASSERT_FALSE(assembler.append(source.data() + 13, 101, frame));
  TEST_ASSERT_TRUE(assembler.append(source.data() + 114,
                                    source.size() - 114, frame));
  TEST_ASSERT_EQUAL_UINT8_ARRAY(source.data(), frame.pcmLittleEndian.data(),
                                source.size());
  TEST_ASSERT_EQUAL_UINT32(0, assembler.filledBytes());
}

void testHelloMatchesAndroidProtocolLayout() {
  huh::protocol::HelloInfo hello{
      "dev-1", "Huh?", "Seeed", "XIAO", "0.1.0",
  };
  std::vector<uint8_t> bytes;
  TEST_ASSERT_TRUE(huh::protocol::encodeHello(hello, bytes));
  assertEnvelope(bytes, MessageType::kHello, 41);

  // First UTF-8 field: unsigned big-endian length 5, then "dev-1".
  TEST_ASSERT_EQUAL_UINT8(0, bytes[12]);
  TEST_ASSERT_EQUAL_UINT8(5, bytes[13]);
  TEST_ASSERT_EQUAL_UINT8_ARRAY("dev-1", bytes.data() + 14, 5);
  // Final fixed fields: 16000 Hz, mono, 16-bit, 320 samples.
  const size_t fixed = bytes.size() - 8;
  const uint8_t expected[] = {0x00, 0x00, 0x3E, 0x80, 0x01, 0x10, 0x01, 0x40};
  TEST_ASSERT_EQUAL_UINT8_ARRAY(expected, bytes.data() + fixed, sizeof(expected));
}

void testAudioUsesBigEndianMetadataAndLittleEndianPcm() {
  AudioFrame frame;
  frame.pcmLittleEndian[0] = 0x34;
  frame.pcmLittleEndian[1] = 0x12;
  frame.pcmLittleEndian[638] = 0xCD;
  frame.pcmLittleEndian[639] = 0xAB;

  std::vector<uint8_t> bytes;
  TEST_ASSERT_TRUE(huh::protocol::encodeAudio(
      knownStream(), 0x0102030405060708ULL, 0x1112131415161718ULL,
      frame, bytes));
  assertEnvelope(bytes, MessageType::kAudio, 672);
  TEST_ASSERT_EQUAL_UINT8_ARRAY(knownStream().data(), bytes.data() + 12, 16);
  const uint8_t sequence[] = {1, 2, 3, 4, 5, 6, 7, 8};
  const uint8_t sampleIndex[] = {0x11, 0x12, 0x13, 0x14,
                                 0x15, 0x16, 0x17, 0x18};
  TEST_ASSERT_EQUAL_UINT8_ARRAY(sequence, bytes.data() + 28, 8);
  TEST_ASSERT_EQUAL_UINT8_ARRAY(sampleIndex, bytes.data() + 36, 8);
  TEST_ASSERT_EQUAL_HEX8(0x34, bytes[44]);
  TEST_ASSERT_EQUAL_HEX8(0x12, bytes[45]);
  TEST_ASSERT_EQUAL_HEX8(0xCD, bytes[682]);
  TEST_ASSERT_EQUAL_HEX8(0xAB, bytes[683]);
}

void testAllControlMessageLayouts() {
  std::vector<uint8_t> bytes;
  const StreamUuid stream = knownStream();

  TEST_ASSERT_TRUE(huh::protocol::encodeStart(stream, bytes));
  assertEnvelope(bytes, MessageType::kStart, 16);

  TEST_ASSERT_TRUE(huh::protocol::encodeHeartbeat(stream, 9, bytes));
  assertEnvelope(bytes, MessageType::kHeartbeat, 24);
  TEST_ASSERT_EQUAL_UINT8(9, bytes.back());

  TEST_ASSERT_TRUE(huh::protocol::encodeStop(
      stream, huh::protocol::StopReason::kPause, bytes));
  assertEnvelope(bytes, MessageType::kStop, 17);
  TEST_ASSERT_EQUAL_UINT8(2, bytes.back());

  TEST_ASSERT_TRUE(huh::protocol::encodeError(0x1234, "capture failed", bytes));
  assertEnvelope(bytes, MessageType::kError, 18);
  TEST_ASSERT_EQUAL_HEX8(0x12, bytes[12]);
  TEST_ASSERT_EQUAL_HEX8(0x34, bytes[13]);
  TEST_ASSERT_EQUAL_UINT8(0, bytes[14]);
  TEST_ASSERT_EQUAL_UINT8(14, bytes[15]);
}

void testOversizedPayloadIsRejected() {
  std::vector<uint8_t> payload(huh::protocol::kMaximumPayloadBytes + 1, 0xAA);
  std::vector<uint8_t> output{1, 2, 3};
  TEST_ASSERT_FALSE(huh::protocol::encodeMessage(
      MessageType::kError, payload.data(), payload.size(), output));
  TEST_ASSERT_TRUE(output.empty());
}

class ShortWriteSink final : public huh::protocol::ByteSink {
 public:
  size_t write(const uint8_t* bytes, size_t length) override {
    const size_t count = std::min<size_t>(3, length);
    received.insert(received.end(), bytes, bytes + count);
    return count;
  }
  std::vector<uint8_t> received;
};

void testWriterHandlesSplitTransportWrites() {
  std::vector<uint8_t> message;
  TEST_ASSERT_TRUE(huh::protocol::encodeStart(knownStream(), message));
  ShortWriteSink sink;
  huh::protocol::HuhMessageWriter writer(sink);
  TEST_ASSERT_TRUE(writer.write(message));
  TEST_ASSERT_EQUAL_UINT8_ARRAY(message.data(), sink.received.data(), message.size());
}

void testStreamSessionAdvancesExactlyOneFrame() {
  huh::protocol::AudioStreamSession session(knownStream());
  AudioFrame frame;
  std::vector<uint8_t> first;
  std::vector<uint8_t> second;
  TEST_ASSERT_TRUE(session.encodeNextAudio(frame, first));
  TEST_ASSERT_TRUE(session.encodeNextAudio(frame, second));
  TEST_ASSERT_EQUAL_UINT64(2, session.nextSequence());
  TEST_ASSERT_EQUAL_UINT64(640, session.nextSampleIndex());
  // Second sequence and first-sample index are both network-order metadata.
  TEST_ASSERT_EQUAL_UINT8(1, second[35]);
  TEST_ASSERT_EQUAL_UINT8(0x01, second[42]);
  TEST_ASSERT_EQUAL_UINT8(0x40, second[43]);
}

}  // namespace

void setUp() {}
void tearDown() {}

int main(int, char**) {
  UNITY_BEGIN();
  RUN_TEST(testFrameAssemblerKeepsShortReadsIncomplete);
  RUN_TEST(testHelloMatchesAndroidProtocolLayout);
  RUN_TEST(testAudioUsesBigEndianMetadataAndLittleEndianPcm);
  RUN_TEST(testAllControlMessageLayouts);
  RUN_TEST(testOversizedPayloadIsRejected);
  RUN_TEST(testWriterHandlesSplitTransportWrites);
  RUN_TEST(testStreamSessionAdvancesExactlyOneFrame);
  return UNITY_END();
}
