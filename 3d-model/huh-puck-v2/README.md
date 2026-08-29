# Huh puck V2

Camera-removed, USB-C-only, no-battery enclosure for the XIAO ESP32S3 Sense.

## V2 changes

- Enlarges the puck from 36 mm to 40 mm diameter.
- Enlarges the open electronics cavity from 32.8 mm to 36.8 mm diameter, adding
  2 mm of radial clearance for the microSD overhang and antenna end.
- Keeps the successful 29.21 mm x 2.54 mm magnet recess exactly unchanged.
- Cuts the 12.0 mm x 5.8 mm USB-C opening completely through the base wall and
  into the electronics cavity.
- Uses the traced Huh logo itself as the open-through microphone grille; the
  separate rounded vent slots have been removed.
- Mirrors the cutout in the model so the logo reads correctly from the outside
  when the lid is installed.

The lid STL is already oriented with the logo face on the print bed and the
retaining skirt upward. The logo openings print without support.

## Adjustable values

All key dimensions are named near the top of `huh_puck_v2.scad`. If the lid is
too tight or loose, adjust `lid_skirt_outer_diameter` in 0.10 mm steps. If a cable
shell needs more clearance, adjust `usb_width` or `usb_height` and re-export.
