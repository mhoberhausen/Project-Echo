#!/usr/bin/env python3
"""Dependency-free HUH1 receiver and firmware reliability test client."""

from __future__ import annotations

import argparse
import dataclasses
import enum
import json
import os
import socket
import struct
import sys
import tempfile
import threading
import time
import uuid
import wave
from pathlib import Path
from typing import BinaryIO


MAGIC = b"HUH1"
PROTOCOL_VERSION = 1
HEADER = struct.Struct("!4sBBHI")
MAX_PAYLOAD_BYTES = 65_536
PCM_BYTES_PER_FRAME = 640
CANONICAL_SAMPLE_RATE = 16_000
CANONICAL_CHANNELS = 1
CANONICAL_SAMPLE_WIDTH_BITS = 16
CANONICAL_SAMPLES_PER_FRAME = 320


class MessageType(enum.IntEnum):
    HELLO = 1
    START = 2
    AUDIO = 3
    HEARTBEAT = 4
    STOP = 5
    ERROR = 6
    ACK = 7
    FETCH = 8
    FILE_CHUNK = 9
    FILE_END = 10
    LIST_CAPTURES = 11
    CAPTURE_INFO = 12
    CAPTURE_LIST_END = 13


class ProtocolError(RuntimeError):
    """The peer sent data that does not conform to HUH1 v1."""


class TruncatedStreamError(ProtocolError):
    """The TCP stream ended in the middle of a HUH1 message."""


@dataclasses.dataclass(frozen=True)
class Message:
    type: MessageType
    payload: bytes
    wire_bytes: int


@dataclasses.dataclass(frozen=True)
class Hello:
    device_id: str
    display_name: str
    manufacturer: str
    model: str
    firmware_version: str
    sample_rate_hz: int
    channels: int
    sample_width_bits: int
    samples_per_frame: int


@dataclasses.dataclass
class Metrics:
    connected_at: float
    capture_id: str | None = None
    first_audio_at: float | None = None
    last_audio_at: float | None = None
    messages: int = 0
    wire_bytes: int = 0
    audio_frames: int = 0
    audio_bytes: int = 0
    duplicate_frames: int = 0
    gap_frames: int = 0
    sample_index_errors: int = 0
    heartbeats: int = 0
    control_heartbeats_sent: int = 0
    remote_errors: list[str] = dataclasses.field(default_factory=list)
    stop_reason: int | None = None
    maximum_inter_frame_ms: float = 0.0

    def summary(self, now: float, hello: Hello | None, target_reached: bool) -> dict[str, object]:
        connection_seconds = max(0.0, now - self.connected_at)
        if self.first_audio_at is None:
            audio_seconds = 0.0
        else:
            audio_seconds = max(0.001, now - self.first_audio_at)
        return {
            "capture_id": self.capture_id,
            "device_id": hello.device_id if hello else None,
            "firmware_version": hello.firmware_version if hello else None,
            "connection_seconds": round(connection_seconds, 3),
            "target_reached": target_reached,
            "messages": self.messages,
            "wire_bytes": self.wire_bytes,
            "wire_bytes_per_second": round(self.wire_bytes / max(connection_seconds, 0.001), 1),
            "audio_frames": self.audio_frames,
            "audio_frames_per_second": round(self.audio_frames / max(audio_seconds, 0.001), 2),
            "audio_bytes": self.audio_bytes,
            "duplicate_frames": self.duplicate_frames,
            "gap_frames": self.gap_frames,
            "sample_index_errors": self.sample_index_errors,
            "heartbeats": self.heartbeats,
            "control_heartbeats_sent": self.control_heartbeats_sent,
            "maximum_inter_frame_ms": round(self.maximum_inter_frame_ms, 2),
            "remote_errors": self.remote_errors,
            "stop_reason": self.stop_reason,
        }


class PayloadReader:
    def __init__(self, payload: bytes) -> None:
        self.payload = payload
        self.offset = 0

    def take(self, count: int) -> bytes:
        end = self.offset + count
        if end > len(self.payload):
            raise ProtocolError(
                f"Payload ended early at byte {self.offset}; needed {count} more bytes."
            )
        value = self.payload[self.offset:end]
        self.offset = end
        return value

    def u8(self) -> int:
        return self.take(1)[0]

    def u16(self) -> int:
        return struct.unpack("!H", self.take(2))[0]

    def u32(self) -> int:
        return struct.unpack("!I", self.take(4))[0]

    def u64(self) -> int:
        return struct.unpack("!Q", self.take(8))[0]

    def string(self) -> str:
        length = self.u16()
        try:
            return self.take(length).decode("utf-8")
        except UnicodeDecodeError as error:
            raise ProtocolError("String payload is not valid UTF-8.") from error

    def stream_id(self) -> uuid.UUID:
        return uuid.UUID(bytes=self.take(16))

    def finish(self) -> None:
        if self.offset != len(self.payload):
            raise ProtocolError(f"Payload has {len(self.payload) - self.offset} trailing bytes.")


def read_exact(stream: BinaryIO, count: int, context: str) -> bytes:
    chunks = bytearray()
    while len(chunks) < count:
        chunk = stream.read(count - len(chunks))
        if not chunk:
            raise TruncatedStreamError(
                f"{context} ended early: received {len(chunks)} of {count} bytes."
            )
        chunks.extend(chunk)
    return bytes(chunks)


def read_message(stream: BinaryIO) -> Message | None:
    first = stream.read(1)
    if not first:
        return None
    header_bytes = first + read_exact(stream, HEADER.size - 1, "Message header")
    magic, version, raw_type, flags, payload_length = HEADER.unpack(header_bytes)
    if magic != MAGIC:
        raise ProtocolError(f"Invalid protocol magic {magic!r}.")
    if version != PROTOCOL_VERSION:
        raise ProtocolError(f"Unsupported protocol version {version}.")
    try:
        message_type = MessageType(raw_type)
    except ValueError as error:
        raise ProtocolError(f"Unknown message type {raw_type}.") from error
    if flags != 0:
        raise ProtocolError(f"Unsupported protocol flags {flags}.")
    if payload_length > MAX_PAYLOAD_BYTES:
        raise ProtocolError(f"Payload length {payload_length} exceeds the protocol maximum.")
    payload = read_exact(stream, payload_length, f"{message_type.name} payload")
    return Message(message_type, payload, HEADER.size + payload_length)


def decode_hello(payload: bytes) -> Hello:
    reader = PayloadReader(payload)
    hello = Hello(
        device_id=reader.string(),
        display_name=reader.string(),
        manufacturer=reader.string(),
        model=reader.string(),
        firmware_version=reader.string(),
        sample_rate_hz=reader.u32(),
        channels=reader.u8(),
        sample_width_bits=reader.u8(),
        samples_per_frame=reader.u16(),
    )
    reader.finish()
    return hello


def validate_hello(hello: Hello, expected_device_id: str | None) -> None:
    if not hello.device_id:
        raise ProtocolError("HELLO device ID is empty.")
    if expected_device_id and hello.device_id != expected_device_id:
        raise ProtocolError(
            f"Expected device {expected_device_id!r}, received {hello.device_id!r}."
        )
    actual = (
        hello.sample_rate_hz,
        hello.channels,
        hello.sample_width_bits,
        hello.samples_per_frame,
    )
    expected = (
        CANONICAL_SAMPLE_RATE,
        CANONICAL_CHANNELS,
        CANONICAL_SAMPLE_WIDTH_BITS,
        CANONICAL_SAMPLES_PER_FRAME,
    )
    if actual != expected:
        raise ProtocolError(f"Unsupported audio format {actual}; expected {expected}.")


def parse_uuid_payload(payload: bytes, message_name: str) -> uuid.UUID:
    reader = PayloadReader(payload)
    stream_id = reader.stream_id()
    reader.finish()
    return stream_id


def encode_message(message_type: MessageType, payload: bytes) -> bytes:
    if len(payload) > MAX_PAYLOAD_BYTES:
        raise ProtocolError("Outbound payload exceeds the protocol maximum.")
    return HEADER.pack(MAGIC, PROTOCOL_VERSION, int(message_type), 0, len(payload)) + payload


def list_retained_captures(args: argparse.Namespace) -> tuple[int, dict[str, object]]:
    captures: list[dict[str, object]] = []
    with socket.create_connection((args.host, args.port), args.connect_timeout) as client:
        client.settimeout(args.read_timeout)
        with client.makefile("rb", buffering=0) as stream:
            hello_message = read_message(stream)
            if hello_message is None or hello_message.type is not MessageType.HELLO:
                raise ProtocolError("Device did not begin with HELLO.")
            hello = decode_hello(hello_message.payload)
            validate_hello(hello, args.expected_device_id)
            client.sendall(encode_message(MessageType.LIST_CAPTURES, b""))
            while True:
                message = read_message(stream)
                if message is None:
                    raise ProtocolError("Connection ended before CAPTURE_LIST_END.")
                if message.type is MessageType.CAPTURE_INFO:
                    if len(message.payload) != 20:
                        raise ProtocolError("CAPTURE_INFO must contain a UUID and byte count.")
                    captures.append({
                        "capture_id": str(uuid.UUID(bytes=message.payload[:16])),
                        "audio_bytes": struct.unpack("!I", message.payload[16:])[0],
                    })
                elif message.type is MessageType.CAPTURE_LIST_END:
                    if len(message.payload) != 2:
                        raise ProtocolError("CAPTURE_LIST_END must contain a count.")
                    declared = struct.unpack("!H", message.payload)[0]
                    if declared != len(captures):
                        raise ProtocolError(
                            f"Capture list declared {declared} entries; received {len(captures)}."
                        )
                    return 0, {
                        "device_id": hello.device_id,
                        "firmware_version": hello.firmware_version,
                        "retained_captures": captures,
                    }
                elif message.type is MessageType.ERROR:
                    raise ProtocolError("Device rejected LIST_CAPTURES.")
                else:
                    raise ProtocolError(f"Unexpected message while listing captures: {message.type.name}.")


class CaptureLease(threading.Thread):
    """Reference TCP controller for START, renewable HEARTBEAT, and STOP."""

    def __init__(
        self,
        client: socket.socket,
        stream_id: uuid.UUID,
        capture_seconds: float,
        heartbeat_interval: float,
        drop_heartbeats_after: float | None,
        metrics: Metrics,
    ) -> None:
        super().__init__(name="huh-capture-lease", daemon=True)
        self.client = client
        self.stream_id = stream_id
        self.capture_seconds = capture_seconds
        self.heartbeat_interval = heartbeat_interval
        self.drop_heartbeats_after = drop_heartbeats_after
        self.metrics = metrics
        self.finished = threading.Event()
        self.failure: str | None = None

    def send(self, message_type: MessageType, payload: bytes) -> None:
        self.client.sendall(encode_message(message_type, payload))

    def run(self) -> None:
        started = time.monotonic()
        counter = 0
        try:
            self.send(MessageType.START, self.stream_id.bytes)
            next_heartbeat = started
            while not self.finished.is_set():
                now = time.monotonic()
                elapsed = now - started
                if self.drop_heartbeats_after is None and elapsed >= self.capture_seconds:
                    self.send(MessageType.STOP, self.stream_id.bytes + bytes((1,)))
                    return
                if (
                    self.drop_heartbeats_after is None
                    or elapsed < self.drop_heartbeats_after
                ) and now >= next_heartbeat:
                    self.send(
                        MessageType.HEARTBEAT,
                        self.stream_id.bytes + struct.pack("!Q", counter),
                    )
                    self.metrics.control_heartbeats_sent += 1
                    counter += 1
                    next_heartbeat = now + self.heartbeat_interval
                self.finished.wait(0.05)
        except OSError as error:
            if not self.finished.is_set():
                self.failure = str(error)


def human_report(summary: dict[str, object], prefix: str = "Metrics") -> str:
    return (
        f"{prefix}: elapsed={summary['connection_seconds']}s "
        f"frames={summary['audio_frames']} fps={summary['audio_frames_per_second']} "
        f"wire_Bps={summary['wire_bytes_per_second']} gaps={summary['gap_frames']} "
        f"duplicates={summary['duplicate_frames']} "
        f"max_frame_gap_ms={summary['maximum_inter_frame_ms']}"
    )


def write_wav_from_pcm(pcm_path: Path, wav_path: Path) -> None:
    wav_path.parent.mkdir(parents=True, exist_ok=True)
    with pcm_path.open("rb") as pcm, wave.open(str(wav_path), "wb") as output:
        output.setnchannels(CANONICAL_CHANNELS)
        output.setsampwidth(CANONICAL_SAMPLE_WIDTH_BITS // 8)
        output.setframerate(CANONICAL_SAMPLE_RATE)
        while chunk := pcm.read(64 * 1024):
            output.writeframesraw(chunk)


def run_receiver(args: argparse.Namespace) -> tuple[int, dict[str, object]]:
    connected_at = time.monotonic()
    metrics = Metrics(connected_at=connected_at)
    hello: Hello | None = None
    active_stream: uuid.UUID | None = None
    expected_sequence = 0
    expected_sample_index = 0
    target_reached = False
    clean_stop = bool(args.resume_stream_id)
    failure: str | None = None
    requested_stream = (
        uuid.UUID(args.resume_stream_id) if args.resume_stream_id else uuid.uuid4()
    )
    metrics.capture_id = str(requested_stream)
    metrics.audio_bytes = args.resume_offset
    metrics.audio_frames = args.resume_offset // PCM_BYTES_PER_FRAME
    transfer_directory = args.transfer_directory or Path(tempfile.gettempdir()) / "huh-receiver"
    transfer_directory.mkdir(parents=True, exist_ok=True)
    partial_path = args.partial_pcm or transfer_directory / f"{requested_stream}.partial"
    if args.resume_stream_id:
        if args.resume_offset == 0 and not partial_path.exists():
            partial_path.write_bytes(b"")
        if not partial_path.is_file() or partial_path.stat().st_size != args.resume_offset:
            return 2, {
                **metrics.summary(time.monotonic(), None, False),
                "failure": "Resume requires a partial PCM file whose size matches --resume-offset.",
                "partial_pcm": str(partial_path),
            }
    else:
        partial_path.write_bytes(b"")

    phase = "fetching" if args.resume_stream_id else "new"
    reconnects = 0
    injected_disconnect = False
    next_report = connected_at + args.report_interval

    while reconnects <= args.max_reconnect_attempts and not target_reached:
        lease: CaptureLease | None = None
        attempt_hello: Hello | None = None
        try:
            client = socket.create_connection((args.host, args.port), args.connect_timeout)
            client.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            client.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, args.receive_buffer_bytes)
            client.settimeout(args.connect_timeout)
            if not args.json:
                print(f"Connected to {args.host}:{args.port}; waiting for HUH1 HELLO...")
            # Android's DataInputStream reads each exact header/payload request directly
            # from the socket. A large Python BufferedReader can wait for its entire
            # user-space buffer, withholding TCP window progress and creating stalls that
            # the Android client would never produce.
            with client, client.makefile("rb", buffering=0) as stream:
              while True:
                now = time.monotonic()
                message = read_message(stream)
                if message is None:
                    if active_stream is not None:
                        raise ProtocolError("TCP connection closed before STOP.")
                    break
                metrics.messages += 1
                metrics.wire_bytes += message.wire_bytes

                if message.type is MessageType.HELLO:
                    if attempt_hello is not None:
                        raise ProtocolError("Unexpected duplicate HELLO.")
                    attempt_hello = decode_hello(message.payload)
                    validate_hello(attempt_hello, args.expected_device_id)
                    hello = hello or attempt_hello
                    if not args.json:
                        print(
                            f"HELLO: {attempt_hello.display_name} id={attempt_hello.device_id} "
                            f"firmware={attempt_hello.firmware_version}"
                        )
                    if phase == "fetching":
                        active_stream = requested_stream
                        client.settimeout(args.read_timeout)
                        client.sendall(
                            encode_message(
                                MessageType.FETCH,
                                requested_stream.bytes + struct.pack("!I", metrics.audio_bytes),
                            )
                        )
                    elif phase == "new":
                        lease = CaptureLease(
                            client,
                            requested_stream,
                            args.duration,
                            args.heartbeat_interval,
                            args.drop_heartbeats_after,
                            metrics,
                        )
                        lease.start()
                        phase = "starting"
                        client.settimeout(None)
                    else:
                        raise ProtocolError(f"Cannot continue capture from phase {phase}.")
                elif message.type is MessageType.START:
                    if attempt_hello is None or phase != "starting":
                        raise ProtocolError("Unexpected START.")
                    active_stream = parse_uuid_payload(message.payload, "START")
                    if active_stream != requested_stream:
                        raise ProtocolError("START did not acknowledge the requested capture UUID.")
                    expected_sequence = 0
                    expected_sample_index = 0
                    phase = "capturing"
                    if not args.json:
                        print(f"START: stream={active_stream}")
                elif message.type is MessageType.AUDIO:
                    reader = PayloadReader(message.payload)
                    stream_id = reader.stream_id()
                    sequence = reader.u64()
                    sample_index = reader.u64()
                    pcm = reader.take(PCM_BYTES_PER_FRAME)
                    reader.finish()
                    if stream_id != active_stream:
                        raise ProtocolError("AUDIO belongs to an inactive stream.")
                    if sequence < expected_sequence:
                        metrics.duplicate_frames += 1
                        continue
                    if sequence > expected_sequence:
                        metrics.gap_frames += sequence - expected_sequence
                    if sample_index != sequence * CANONICAL_SAMPLES_PER_FRAME:
                        metrics.sample_index_errors += 1
                    if sample_index != expected_sample_index and sequence == expected_sequence:
                        metrics.sample_index_errors += 1
                    frame_at = time.monotonic()
                    if metrics.last_audio_at is not None:
                        gap_ms = (frame_at - metrics.last_audio_at) * 1000.0
                        metrics.maximum_inter_frame_ms = max(metrics.maximum_inter_frame_ms, gap_ms)
                    metrics.first_audio_at = metrics.first_audio_at or frame_at
                    metrics.last_audio_at = frame_at
                    metrics.audio_frames += 1
                    metrics.audio_bytes += len(pcm)
                    expected_sequence = sequence + 1
                    expected_sample_index = sample_index + CANONICAL_SAMPLES_PER_FRAME
                    with partial_path.open("ab") as output:
                        output.write(pcm)
                        output.flush()
                        os.fsync(output.fileno())
                elif message.type is MessageType.HEARTBEAT:
                    reader = PayloadReader(message.payload)
                    stream_id = reader.stream_id()
                    reader.u64()
                    reader.finish()
                    if stream_id != active_stream:
                        raise ProtocolError("HEARTBEAT belongs to an inactive stream.")
                    metrics.heartbeats += 1
                elif message.type is MessageType.STOP:
                    reader = PayloadReader(message.payload)
                    stream_id = reader.stream_id()
                    reason = reader.u8()
                    reader.finish()
                    if stream_id != active_stream:
                        raise ProtocolError("STOP belongs to an inactive stream.")
                    metrics.stop_reason = reason
                    clean_stop = reason in (1, 2, 3, 7)
                    phase = "fetching"
                    if lease:
                        lease.finished.set()
                    client.settimeout(args.read_timeout)
                    client.sendall(
                        encode_message(
                            MessageType.FETCH,
                            stream_id.bytes + struct.pack("!I", metrics.audio_bytes),
                        )
                    )
                    if not args.json:
                        print(f"STOP: reason={reason}; fetching finalized recording")
                elif message.type is MessageType.FILE_CHUNK:
                    reader = PayloadReader(message.payload)
                    stream_id = reader.stream_id()
                    offset = reader.u32()
                    pcm = reader.take(len(message.payload) - 20)
                    reader.finish()
                    if stream_id != active_stream:
                        raise ProtocolError("FILE_CHUNK belongs to an inactive stream.")
                    if offset != metrics.audio_bytes:
                        raise ProtocolError(
                            f"FILE_CHUNK offset {offset} does not match {metrics.audio_bytes}."
                        )
                    chunk_at = time.monotonic()
                    metrics.first_audio_at = metrics.first_audio_at or chunk_at
                    metrics.last_audio_at = chunk_at
                    metrics.audio_bytes += len(pcm)
                    metrics.audio_frames = metrics.audio_bytes // PCM_BYTES_PER_FRAME
                    with partial_path.open("ab") as output:
                        output.write(pcm)
                        output.flush()
                        os.fsync(output.fileno())
                    if (
                        args.disconnect_after_bytes is not None
                        and not injected_disconnect
                        and metrics.audio_bytes >= args.disconnect_after_bytes
                    ):
                        injected_disconnect = True
                        raise OSError("Injected transfer disconnect for resume validation.")
                elif message.type is MessageType.FILE_END:
                    reader = PayloadReader(message.payload)
                    stream_id = reader.stream_id()
                    total_bytes = reader.u32()
                    reader.finish()
                    if stream_id != active_stream:
                        raise ProtocolError("FILE_END belongs to an inactive stream.")
                    if total_bytes != metrics.audio_bytes:
                        raise ProtocolError(
                            f"FILE_END reports {total_bytes} bytes; received {metrics.audio_bytes}."
                        )
                    if total_bytes % PCM_BYTES_PER_FRAME:
                        raise ProtocolError("FILE_END total is not aligned to a 20 ms PCM frame.")
                    if partial_path.stat().st_size != total_bytes:
                        raise ProtocolError("Local partial PCM size does not match FILE_END.")
                    completed_pcm = partial_path.with_suffix(".pcm")
                    partial_path.replace(completed_pcm)
                    if args.output_wav:
                        write_wav_from_pcm(completed_pcm, args.output_wav)
                    if not args.retain_on_device:
                        client.sendall(encode_message(MessageType.ACK, stream_id.bytes))
                    completed_pcm.unlink(missing_ok=True)
                    active_stream = None
                    target_reached = True
                    phase = "complete"
                    break
                elif message.type is MessageType.ERROR:
                    reader = PayloadReader(message.payload)
                    code = reader.u16()
                    text = reader.string()
                    reader.finish()
                    metrics.remote_errors.append(f"{code}: {text}")
                    if not args.json:
                        print(f"REMOTE ERROR {code}: {text}", file=sys.stderr)

                now = time.monotonic()
                if args.report_interval > 0 and now >= next_report:
                    if not args.json:
                        print(human_report(metrics.summary(now, hello, target_reached)))
                    next_report = now + args.report_interval
        except (OSError, ProtocolError) as error:
            failure = str(error)
            reconnects += 1
            if phase in ("starting", "capturing"):
                phase = "fetching"
                time.sleep(args.lease_expiry_wait)
            elif reconnects <= args.max_reconnect_attempts:
                time.sleep(min(args.reconnect_max_delay, args.reconnect_base_delay * (2 ** (reconnects - 1))))
        finally:
            if lease:
                lease.finished.set()
                lease.join(timeout=1.0)

    if target_reached:
        failure = None

    ended_at = time.monotonic()
    if lease and lease.failure and not failure:
        failure = f"Control lease failed: {lease.failure}"
    summary = metrics.summary(ended_at, hello, target_reached)
    summary["reconnects"] = reconnects
    summary["partial_pcm"] = None if target_reached else str(partial_path)
    summary["retained_on_device"] = bool(target_reached and args.retain_on_device)
    summary["failure"] = failure
    if not failure and metrics.remote_errors:
        summary["failure"] = "Remote device error(s): " + "; ".join(metrics.remote_errors)

    exit_code = 0
    if failure or metrics.remote_errors:
        exit_code = 2
    elif not target_reached:
        summary["failure"] = "Connection ended before the capture was transferred."
        exit_code = 3
    elif not clean_stop:
        summary["failure"] = "Connection ended without a normal STOP."
        exit_code = 3
    elif metrics.gap_frames > args.max_gap_frames:
        summary["failure"] = (
            f"Observed {metrics.gap_frames} missing frames; maximum is {args.max_gap_frames}."
        )
        exit_code = 4
    elif metrics.sample_index_errors:
        summary["failure"] = f"Observed {metrics.sample_index_errors} sample-index errors."
        exit_code = 4
    elif args.min_fps > 0 and float(summary["audio_frames_per_second"]) < args.min_fps:
        summary["failure"] = (
            f"Audio rate {summary['audio_frames_per_second']} fps is below {args.min_fps} fps."
        )
        exit_code = 4
    return exit_code, summary


def positive_float(value: str) -> float:
    parsed = float(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return parsed


def nonnegative_float(value: str) -> float:
    parsed = float(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("must be zero or greater")
    return parsed


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Receive and validate a XIAO HUH1 audio stream.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("host", help="XIAO local IP address or hostname")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument(
        "--duration",
        type=nonnegative_float,
        default=5.0,
        help="seconds to record before sending an explicit STOP",
    )
    parser.add_argument(
        "--heartbeat-interval",
        type=positive_float,
        default=5.0,
        help="seconds between inbound capture-lease renewals",
    )
    parser.add_argument(
        "--drop-heartbeats-after",
        type=nonnegative_float,
        help="stop renewing the lease after this many seconds and expect device timeout",
    )
    parser.add_argument("--output-wav", type=Path, help="optional PCM WAV output path")
    parser.add_argument(
        "--resume-stream-id",
        help="resume FETCH for an already finalized capture UUID instead of sending START",
    )
    parser.add_argument(
        "--resume-offset",
        type=int,
        default=0,
        help="existing verified PCM byte count used with --resume-stream-id",
    )
    parser.add_argument(
        "--partial-pcm",
        type=Path,
        help="Android-style local partial PCM file to create or resume",
    )
    parser.add_argument(
        "--transfer-directory",
        type=Path,
        help="directory for app-private-style temporary PCM transfer files",
    )
    parser.add_argument("--max-reconnect-attempts", type=int, default=8)
    parser.add_argument(
        "--disconnect-after-bytes",
        type=int,
        help="test-only: drop the TCP connection once after this many transferred PCM bytes",
    )
    parser.add_argument("--reconnect-base-delay", type=positive_float, default=1.0)
    parser.add_argument("--reconnect-max-delay", type=positive_float, default=30.0)
    parser.add_argument(
        "--lease-expiry-wait",
        type=positive_float,
        default=16.0,
        help="wait before FETCH after losing a capture connection",
    )
    parser.add_argument("--expected-device-id")
    parser.add_argument(
        "--retain-on-device",
        action="store_true",
        help="validate a completed transfer without ACKing SD deletion",
    )
    parser.add_argument(
        "--list-captures",
        action="store_true",
        help="list finalized SD captures without starting a recording",
    )
    parser.add_argument("--report-interval", type=nonnegative_float, default=5.0)
    parser.add_argument("--connect-timeout", type=positive_float, default=10.0)
    parser.add_argument("--read-timeout", type=positive_float, default=30.0)
    parser.add_argument("--receive-buffer-bytes", type=int, default=262_144)
    parser.add_argument("--min-fps", type=nonnegative_float, default=0.0)
    parser.add_argument("--max-gap-frames", type=int, default=0)
    parser.add_argument("--json", action="store_true", help="print only the final JSON summary")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.port < 1 or args.port > 65_535:
        raise SystemExit("--port must be between 1 and 65535")
    if args.receive_buffer_bytes < HEADER.size:
        raise SystemExit("--receive-buffer-bytes is too small")
    if args.max_gap_frames < 0:
        raise SystemExit("--max-gap-frames cannot be negative")
    if args.max_reconnect_attempts < 0:
        raise SystemExit("--max-reconnect-attempts cannot be negative")
    if args.disconnect_after_bytes is not None and args.disconnect_after_bytes < 1:
        raise SystemExit("--disconnect-after-bytes must be positive")
    if args.resume_offset < 0 or args.resume_offset > 0xFFFF_FFFF:
        raise SystemExit("--resume-offset must fit an unsigned 32-bit value")
    if args.resume_offset and not args.resume_stream_id:
        raise SystemExit("--resume-offset requires --resume-stream-id")
    if args.resume_stream_id:
        try:
            uuid.UUID(args.resume_stream_id)
        except ValueError as error:
            raise SystemExit("--resume-stream-id must be a UUID") from error
    try:
        exit_code, summary = (
            list_retained_captures(args) if args.list_captures else run_receiver(args)
        )
    except KeyboardInterrupt:
        print("Interrupted.", file=sys.stderr)
        return 130
    if args.json:
        print(json.dumps(summary, sort_keys=True))
    else:
        print(human_report(summary, "Final"))
        if summary.get("failure"):
            print(f"FAILED: {summary['failure']}", file=sys.stderr)
        else:
            print("PASS")
        if args.output_wav:
            print(f"WAV: {args.output_wav.resolve()}")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
