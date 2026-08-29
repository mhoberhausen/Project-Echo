"""Convert the supplied Huh raster logo to a clean, OpenSCAD-friendly SVG."""

from pathlib import Path
import sys

import cv2


def contour_path(contour, x0, y0, scale, height):
    points = contour[:, 0, :]
    commands = []
    for index, (x, y) in enumerate(points):
        px = (float(x) - x0) * scale
        py = height - (float(y) - y0) * scale
        commands.append(("M" if index == 0 else "L") + f" {px:.4f},{py:.4f}")
    commands.append("Z")
    return " ".join(commands)


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: trace_logo.py INPUT.png OUTPUT.svg")

    source = Path(sys.argv[1])
    target = Path(sys.argv[2])
    image = cv2.imread(str(source), cv2.IMREAD_COLOR)
    if image is None:
        raise SystemExit(f"could not read {source}")

    # The mark is bright cyan on black. A value threshold removes the black
    # background and the faint glow while retaining the intentional strokes.
    hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
    mask = cv2.inRange(hsv, (75, 90, 145), (115, 255, 255))
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))

    contours, hierarchy = cv2.findContours(mask, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_SIMPLE)
    if hierarchy is None:
        raise SystemExit("no logo contours found")

    x, y, width, height = cv2.boundingRect(mask)
    target_height = 18.0
    scale = target_height / height
    target_width = width * scale

    paths = []
    for contour in contours:
        if abs(cv2.contourArea(contour)) < 30:
            continue
        epsilon = 0.0008 * cv2.arcLength(contour, True)
        simplified = cv2.approxPolyDP(contour, epsilon, True)
        paths.append(contour_path(simplified, x, y, scale, target_height))

    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{target_width:.4f}mm" '
        f'height="{target_height:.4f}mm" viewBox="0 0 {target_width:.4f} {target_height:.4f}">\n'
        f'  <path d="{" ".join(paths)}" fill="#000000" fill-rule="evenodd"/>\n'
        '</svg>\n',
        encoding="utf-8",
    )
    print(f"wrote {target} ({target_width:.2f} x {target_height:.2f} mm)")


if __name__ == "__main__":
    main()
