# Huh puck V4 Slim

Slim, battery-equipped enclosure for the camera-removed XIAO ESP32S3 Sense.
Piezo hardware is intentionally omitted.

## Design approach

- Retains V2's 11.5 mm base and 2 mm lid heights (13.5 mm closed).
- Widens the puck to 54 mm so the estimated 30 x 20 x 5 mm LiPo and XIAO can
  sit side-by-side rather than stacking vertically.
- Keeps the successful 29.21 x 2.54 mm magnet recess exactly unchanged.
- Adds a rounded battery cradle, open-ended XIAO guides, a small connector bay,
  and open routing space above the low guides for wire slack.
- Offsets the flared USB-C opening to the XIAO position.
- Adds a shallow antenna pocket beneath the lid, away from the logo grille.
- Uses the traced Huh logo as the open-through microphone grille, oriented to
  read correctly from the outside.

This revision assumes the actual XIAO/Sense assembly already fits V2's internal
height. A true 15 mm-tall assembly cannot fit inside a 13.5 mm closed enclosure;
if that handoff estimate is correct, use the stacked V3 or provide a measured
assembled height.

The lid STL is oriented with the logo face on the print bed and retaining skirt
upward. Install the battery with its wire end toward the connector bay, then
route the joined leads above the low cradle wall without pinching the pouch.

All key dimensions are named near the top of `huh_puck_v4_slim.scad`. If the lid
is too tight or loose, adjust `lid_skirt_outer_diameter` in 0.10 mm steps. If a
cable shell needs more clearance, adjust the USB parameters and re-export.
