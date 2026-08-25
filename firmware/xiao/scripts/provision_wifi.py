"""PlatformIO target that provisions Wi-Fi after a normal firmware upload."""

from configparser import ConfigParser
from pathlib import Path
import time

Import("env")


def _load_credentials(project_dir):
    config_path = Path(project_dir) / "wifi.local.ini"
    if not config_path.is_file():
        raise RuntimeError(
            "wifi.local.ini was not found. Copy wifi.local.example.ini and fill it in."
        )

    parser = ConfigParser(interpolation=None)
    parser.read(config_path, encoding="utf-8")
    if not parser.has_section("wifi"):
        raise RuntimeError("wifi.local.ini must contain a [wifi] section.")

    ssid = _remove_optional_quotes(parser.get("wifi", "ssid", fallback=""))
    password = _remove_optional_quotes(parser.get("wifi", "password", fallback=""))
    ssid_bytes = ssid.encode("utf-8")
    password_bytes = password.encode("utf-8")
    if not 1 <= len(ssid_bytes) <= 32:
        raise RuntimeError("Wi-Fi SSID must contain 1 to 32 UTF-8 bytes.")
    if len(password_bytes) > 63:
        raise RuntimeError("Wi-Fi password must contain at most 63 UTF-8 bytes.")
    if b"\r" in ssid_bytes or b"\n" in ssid_bytes:
        raise RuntimeError("Wi-Fi SSID cannot contain a newline.")
    if b"\r" in password_bytes or b"\n" in password_bytes:
        raise RuntimeError("Wi-Fi password cannot contain a newline.")
    return ssid_bytes, password_bytes


def _remove_optional_quotes(value):
    if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
        return value[1:-1]
    return value


def _find_port(configured_port):
    from serial.tools import list_ports

    if configured_port and "$" not in configured_port:
        return configured_port

    xiao_ports = [
        port.device
        for port in list_ports.comports()
        if port.vid == 0x303A
    ]
    if len(xiao_ports) == 1:
        return xiao_ports[0]
    if not xiao_ports:
        raise RuntimeError(
            "No ESP32 USB serial port found. Connect the XIAO or set upload_port."
        )
    raise RuntimeError(
        "Multiple ESP32 USB serial ports found. Set upload_port in platformio.ini."
    )


def _read_until(port, expected, timeout_seconds):
    deadline = time.monotonic() + timeout_seconds
    received = bytearray()
    while time.monotonic() < deadline:
        waiting = port.in_waiting
        if waiting:
            received.extend(port.read(waiting))
            if expected in received:
                return bytes(received)
        else:
            time.sleep(0.05)
    return bytes(received)


def _provision(source, target, env):
    import serial

    ssid, password = _load_credentials(env.subst("$PROJECT_DIR"))
    port_name = _find_port(env.subst("$UPLOAD_PORT"))
    print("Provisioning Wi-Fi over USB serial (password will not be displayed)...")

    port = serial.Serial(port_name, 115200, timeout=0.2, write_timeout=2)
    try:
        # Opening USB CDC commonly resets the XIAO. Wait for either the fresh-device
        # prompt or ordinary command mode without displaying device output.
        initial = _read_until(port, b"SSID: ", 5)
        if b"SSID: " not in initial:
            port.write(b"wifi setup\n")
            port.flush()
            if b"SSID: " not in _read_until(port, b"SSID: ", 3):
                raise RuntimeError("The device did not enter Wi-Fi setup mode.")

        port.write(ssid + b"\n")
        port.flush()
        if b"Password (input hidden): " not in _read_until(
            port, b"Password (input hidden): ", 3
        ):
            raise RuntimeError("The device did not request the Wi-Fi password.")

        port.write(password + b"\n")
        port.flush()
        if b"Saving credentials... done." not in _read_until(
            port, b"Saving credentials... done.", 5
        ):
            raise RuntimeError("The device did not confirm NVS credential storage.")
    finally:
        port.close()
        password = b""

    print("Wi-Fi credentials saved to device NVS successfully.")


if env.subst("$PIOENV") == "xiao_esp32s3_sense":
    env.AddCustomTarget(
        name="provision-wifi",
        dependencies=["upload"],
        actions=[_provision],
        title="Upload and provision Wi-Fi",
        description="Upload firmware, then save wifi.local.ini credentials to device NVS",
    )
