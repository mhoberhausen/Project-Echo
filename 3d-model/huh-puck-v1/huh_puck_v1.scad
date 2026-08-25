// Huh XIAO ESP32S3 Sense puck — V1 fit-test revision
// Camera removed, USB-C powered, no battery.
// Units: millimetres. Set part to "base", "lid", or "both".

part = is_undef(part) ? "both" : part;
$fn = 160;

// Proven fit-test envelope. The magnet recess dimensions are intentionally
// unchanged from V0 because the physical magnet fit was reported as good.
puck_diameter = 36.00;
magnet_diameter = 29.21;
magnet_depth = 2.54;

base_height = 11.50;
floor_z = 3.05;                // leaves 0.51 mm above the magnet recess
inner_diameter = 32.80;

// True through-opening for a USB-C plug, with room for a typical cable shell.
usb_width = 12.00;
usb_height = 5.80;
usb_bottom_z = 3.20;

lid_plate = 2.00;
lid_skirt_height = 2.50;
lid_skirt_outer_diameter = 32.40;
lid_skirt_wall = 1.20;
fit_relief_width = 0.65;

logo_height = 18.00;
logo_y = 0.00;

epsilon = 0.05;

module rounded_slot_2d(width, height) {
    hull() {
        translate([-(width-height)/2, 0]) circle(d=height);
        translate([ (width-height)/2, 0]) circle(d=height);
    }
}

module usb_through_cutout() {
    // The 12 mm extrusion spans from outside the puck to well inside the
    // electronics cavity. This guarantees a true wall-to-cavity opening.
    translate([0, -puck_diameter/2, usb_bottom_z + usb_height/2])
        rotate([90, 0, 0])
            linear_extrude(height=12.0, center=true)
                rounded_slot_2d(usb_width, usb_height);
}

module base() {
    difference() {
        cylinder(d=puck_diameter, h=base_height);

        // Magnet pocket: preserved at 29.21 mm diameter x 2.54 mm depth.
        translate([0, 0, -epsilon])
            cylinder(d=magnet_diameter, h=magnet_depth + epsilon);

        // Main electronics cavity, open at the top.
        translate([0, 0, floor_z])
            cylinder(d=inner_diameter, h=base_height - floor_z + epsilon);

        usb_through_cutout();
    }
}

module lid_logo_cutout() {
    // The logo itself is the microphone grille: cut completely through the
    // lid plate instead of using separate vent slots.
    translate([0, logo_y, -epsilon])
        linear_extrude(height=lid_plate + 2*epsilon)
            resize([0, logo_height], auto=true)
                import("huh_logo_trace.svg", center=true);
}

module lid_skirt() {
    difference() {
        translate([0, 0, -lid_skirt_height])
            cylinder(d=lid_skirt_outer_diameter, h=lid_skirt_height);
        translate([0, 0, -lid_skirt_height-epsilon])
            cylinder(d=lid_skirt_outer_diameter - 2*lid_skirt_wall,
                     h=lid_skirt_height + 2*epsilon);

        // Four small axial reliefs make the friction fit more forgiving.
        for (angle = [45, 135, 225, 315])
            rotate([0, 0, angle])
                translate([lid_skirt_outer_diameter/2, 0, -lid_skirt_height/2])
                    cube([2.0, fit_relief_width, lid_skirt_height + 2*epsilon], center=true);
    }
}

module lid() {
    union() {
        difference() {
            cylinder(d=puck_diameter, h=lid_plate);
            lid_logo_cutout();
        }
        lid_skirt();
    }
}

module lid_print() {
    // Decorative face on the bed, skirt upward.
    translate([0, 0, lid_plate]) rotate([180, 0, 0]) lid();
}

if (part == "base") {
    base();
} else if (part == "lid") {
    lid_print();
} else {
    translate([-21, 0, 0]) base();
    // Exploded design preview with the decorated exterior facing upward.
    translate([21, 0, lid_skirt_height]) lid();
}
