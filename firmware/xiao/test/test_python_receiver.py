import importlib.util
import io
import socket
import struct
import sys
import threading
import time
import unittest
import uuid
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "tools" / "huh_receiver.py"
SPEC = importlib.util.spec_from_file_location("huh_receiver", MODULE_PATH)
assert SPEC and SPEC.loader
receiver = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = receiver
SPEC.loader.exec_module(receiver)


def envelope(message_type, payload):
    return receiver.HEADER.pack(
        receiver.MAGIC,
        receiver.PROTOCOL_VERSION,
        int(message_type),
        0,
        len(payload),
    ) + payload


def string(value):
    encoded = value.encode("utf-8")
    return struct.pack("!H", len(encoded)) + encoded


class ReceiverProtocolTest(unittest.TestCase):
    def test_decodes_canonical_hello(self):
        payload = b"".join(
            (
                string("echo-123"),
                string("Huh? Puck"),
                string("Seeed Studio"),
                string("XIAO ESP32S3 Sense"),
                string("0.1.0"),
                struct.pack("!IBBH", 16_000, 1, 16, 320),
            )
        )
        message = receiver.read_message(
            io.BytesIO(envelope(receiver.MessageType.HELLO, payload))
        )
        self.assertIsNotNone(message)
        hello = receiver.decode_hello(message.payload)
        receiver.validate_hello(hello, "echo-123")
        self.assertEqual("Huh? Puck", hello.display_name)
        self.assertEqual(320, hello.samples_per_frame)

    def test_reads_audio_split_across_small_reads(self):
        stream_id = uuid.uuid4()
        payload = stream_id.bytes + struct.pack("!QQ", 7, 2240) + bytes(640)
        raw = envelope(receiver.MessageType.AUDIO, payload)

        class SmallReader(io.BytesIO):
            def read(self, count=-1):
                return super().read(min(count, 3))

        message = receiver.read_message(SmallReader(raw))
        self.assertEqual(receiver.MessageType.AUDIO, message.type)
        self.assertEqual(672, len(message.payload))

    def test_reports_truncated_payload(self):
        raw = receiver.HEADER.pack(
            receiver.MAGIC,
            receiver.PROTOCOL_VERSION,
            int(receiver.MessageType.AUDIO),
            0,
            672,
        ) + bytes(40)
        with self.assertRaisesRegex(receiver.TruncatedStreamError, "40 of 672"):
            receiver.read_message(io.BytesIO(raw))

    def test_rejects_invalid_magic(self):
        raw = receiver.HEADER.pack(b"NOPE", 1, 1, 0, 0)
        with self.assertRaisesRegex(receiver.ProtocolError, "Invalid protocol magic"):
            receiver.read_message(io.BytesIO(raw))

    def test_encodes_ack(self):
        stream_id = uuid.uuid4()
        message = receiver.encode_message(receiver.MessageType.ACK, stream_id.bytes)
        decoded = receiver.read_message(io.BytesIO(message))
        self.assertEqual(receiver.MessageType.ACK, decoded.type)
        self.assertEqual(stream_id.bytes, decoded.payload)

    def test_capture_lease_sends_start_heartbeats_and_stop(self):
        class FakeSocket:
            def __init__(self):
                self.data = bytearray()

            def sendall(self, value):
                self.data.extend(value)

        fake = FakeSocket()
        metrics = receiver.Metrics(connected_at=time.monotonic())
        stream_id = uuid.uuid4()
        lease = receiver.CaptureLease(fake, stream_id, 0.05, 0.01, None, metrics)
        lease.start()
        lease.join(timeout=1)
        self.assertFalse(lease.is_alive())

        stream = io.BytesIO(fake.data)
        types = []
        while message := receiver.read_message(stream):
            types.append(message.type)
        self.assertEqual(receiver.MessageType.START, types[0])
        self.assertIn(receiver.MessageType.HEARTBEAT, types)
        self.assertEqual(receiver.MessageType.STOP, types[-1])
        self.assertGreater(metrics.control_heartbeats_sent, 0)

    def test_capture_lease_can_drop_heartbeats_without_sending_stop(self):
        class FakeSocket:
            def __init__(self):
                self.data = bytearray()

            def sendall(self, value):
                self.data.extend(value)

        fake = FakeSocket()
        metrics = receiver.Metrics(connected_at=time.monotonic())
        lease = receiver.CaptureLease(
            fake, uuid.uuid4(), 5.0, 0.01, 0.025, metrics
        )
        lease.start()
        time.sleep(0.06)
        lease.finished.set()
        lease.join(timeout=1)
        stream = io.BytesIO(fake.data)
        types = []
        while message := receiver.read_message(stream):
            types.append(message.type)
        self.assertEqual(receiver.MessageType.START, types[0])
        self.assertIn(receiver.MessageType.HEARTBEAT, types)
        self.assertNotIn(receiver.MessageType.STOP, types)

    def test_reference_controller_completes_sd_file_transfer_contract(self):
        listener = socket.socket()
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)
        port = listener.getsockname()[1]
        captured = {}

        hello_payload = b"".join(
            (
                string("echo-test"),
                string("Huh? Puck"),
                string("Seeed Studio"),
                string("XIAO ESP32S3 Sense"),
                string("test"),
                struct.pack("!IBBH", 16_000, 1, 16, 320),
            )
        )
        pcm = bytes((index % 251 for index in range(1280)))

        def firmware():
            client, _ = listener.accept()
            with client, client.makefile("rb") as stream:
                client.sendall(envelope(receiver.MessageType.HELLO, hello_payload))
                start = receiver.read_message(stream)
                captured["stream"] = start.payload
                client.sendall(envelope(receiver.MessageType.START, start.payload))
                while True:
                    command = receiver.read_message(stream)
                    if command.type is receiver.MessageType.STOP:
                        break
                client.sendall(envelope(receiver.MessageType.STOP, start.payload + b"\x01"))
                fetch = receiver.read_message(stream)
                captured["fetch"] = fetch.type
                client.sendall(
                    envelope(
                        receiver.MessageType.FILE_CHUNK,
                        start.payload + struct.pack("!I", 0) + pcm,
                    )
                )
                client.sendall(
                    envelope(
                        receiver.MessageType.FILE_END,
                        start.payload + struct.pack("!I", len(pcm)),
                    )
                )
                captured["ack"] = receiver.read_message(stream).type

        server = threading.Thread(target=firmware, daemon=True)
        server.start()
        args = receiver.build_parser().parse_args(
            [
                "127.0.0.1",
                "--port",
                str(port),
                "--duration",
                "0.05",
                "--heartbeat-interval",
                "0.01",
                "--read-timeout",
                "2",
                "--json",
            ]
        )
        exit_code, summary = receiver.run_receiver(args)
        server.join(timeout=1)
        listener.close()

        self.assertEqual(0, exit_code, summary)
        self.assertEqual(len(pcm), summary["audio_bytes"])
        self.assertEqual(receiver.MessageType.FETCH, captured["fetch"])
        self.assertEqual(receiver.MessageType.ACK, captured["ack"])


if __name__ == "__main__":
    unittest.main()
