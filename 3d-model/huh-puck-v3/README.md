# Huh puck V3

Battery-equipped enclosure for the camera-removed XIAO ESP32S3 Sense. Piezo
hardware is intentionally omitted.

## Estimated hardware envelope

- XIAO/Sense assembly: 21 x 17.8 x 15 mm
- Protected 502030-class LiPo: 30 x 20 x 5 mm
- Adhesive FPC antenna: approximately 20 x 8 mm

## V3 changes

- Enlarges the puck to 44 mm diameter and approximately 29 mm closed height.
- Keeps the successful 29.21 x 2.54 mm magnet recess exactly unchanged.
- Leaves 1.0 mm of plastic between the magnet pocket and battery.
- Adds a rounded 21 x 31 mm battery cradle with a power-wire exit.
- Adds four fixed-height supports and a removable board carrier, preventing the
  board from bearing directly on the battery pouch.
- Adds a carrier wire pass-through and low, open-ended XIAO alignment rails.
- Raises the USB-C opening to the estimated stacked-board height and adds a
  wider exterior cable lead-in.
- Adds a shallow 20 x 8 mm antenna pocket beneath the lid, beside the logo so it
  does not cover the microphone grille.
- Uses the traced Huh logo itself as the open-through microphone grille; the
  separate rounded vent slots have been removed.
- Mirrors the cutout in the model so the logo reads correctly from the outside
  when the lid is installed.

The lid STL is already oriented with the logo face on the print bed and the
retaining skirt upward. The logo openings print without support.

## Adjustable values

Print the base upright, the carrier flat, and the supplied lid STL as oriented.
Install the battery first, then the carrier and XIAO, route the antenna coax,
and attach the antenna strip inside the lid pocket. Use compliant insulation or
foam retention rather than forcing the lid against the LiPo pouch.

The estimated vertical stack leaves about 0.8 mm above the battery, uses a
1.0 mm carrier plus 0.8 mm alignment rails, and leaves about 1.2 mm above the
15 mm XIAO/Sense assembly before the lid. For the first print, check the battery
pocket, carrier seating, USB-C height, antenna-pocket fit, and coax/wire routing
before leaving the battery installed unattended.

All key dimensions are named near the top of `huh_puck_v3.scad`. If the lid is
too tight or loose, adjust `lid_skirt_outer_diameter` in 0.10 mm steps. If a cable
shell needs more clearance, adjust `usb_width` or `usb_height` and re-export.
