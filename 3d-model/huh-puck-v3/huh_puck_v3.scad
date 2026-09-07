// Huh XIAO ESP32S3 Sense puck — V3 battery stack revision
// Camera and piezo removed; LiPo powered with USB-C charging.
// Units: millimetres. Set part to "base", "lid", "carrier", or "both".

part = is_undef(part) ? "both" : part;
$fn = 160;

// Estimated from the photographed 502030-class LiPo and hardware handoff.
// The proven magnet recess remains exactly unchanged from V1/V2.
puck_diameter = 44.00;
magnet_diameter = 29.21;
magnet_depth = 2.54;

base_height = 27.00;
floor_z = 3.54;                // 1.00 mm plastic above the magnet recess
inner_diameter = 40.80;

// Battery cradle: estimated 30 x 20 x 5 mm protected LiPo.
battery_pocket = [21.0, 31.0];
battery_cradle_outer = [22.4, 32.4];
battery_corner_radius = 3.0;
battery_cradle_height = 1.20;
battery_height_allowance = 5.50;
battery_top_clearance = 0.80;
battery_wire_exit_width = 8.00;

// Removable board carrier above the battery.
carrier_size = [32.0, 24.0];
carrier_corner_radius = 4.0;
carrier_thickness = 1.00;
carrier_under_z = floor_z + battery_height_allowance + battery_top_clearance;
carrier_post_diameter = 2.80;
board_rail_gap = 20.00;
board_rail_width = 0.80;
board_rail_height = 0.80;
board_rail_length = 23.00;
carrier_wire_slot = [4.0, 8.0];

// True through-opening for a USB-C plug, with room for a typical cable shell.
usb_width = 13.00;
usb_height = 6.00;
usb_bottom_z = 11.00;
usb_flare_width = 15.00;
usb_flare_height = 7.50;

lid_plate = 2.00;
lid_skirt_height = 2.50;
lid_skirt_outer_diameter = 40.40;
lid_skirt_wall = 1.20;
fit_relief_width = 0.65;

logo_height = 18.00;
logo_y = 0.00;

// Shallow underside landing pocket for the adhesive FPC Wi-Fi/BLE antenna.
// It sits beside, rather than behind, the logo microphone grille.
antenna_pocket_size = [8.0, 20.0];
antenna_pocket_x = 13.2;
antenna_pocket_depth = 0.55;
antenna_corner_radius = 2.0;

epsilon = 0.05;

module rounded_slot_2d(width, height) {
    hull() {
        translate([-(width-height)/2, 0]) circle(d=height);
        translate([ (width-height)/2, 0]) circle(d=height);
    }
}

module rounded_rect_2d(size, radius) {
    hull()
        for (x = [-size[0]/2 + radius, size[0]/2 - radius])
            for (y = [-size[1]/2 + radius, size[1]/2 - radius])
                translate([x, y]) circle(r=radius);
}

module usb_through_cutout() {
    // The through tunnel spans from outside the puck to well inside the
    // electronics cavity. This guarantees a true wall-to-cavity opening.
    translate([0, -puck_diameter/2, usb_bottom_z + usb_height/2])
        rotate([90, 0, 0])
            linear_extrude(height=14.0, center=true)
                rounded_slot_2d(usb_width, usb_height);

    // Exterior lead-in admits a typical molded USB-C cable shell.
    translate([0, -puck_diameter/2 - 0.5,
               usb_bottom_z + usb_height/2])
        rotate([90, 0, 0])
            linear_extrude(height=4.0, center=true)
                rounded_slot_2d(usb_flare_width, usb_flare_height);
}

module battery_cradle() {
    translate([0, 0, floor_z])
        linear_extrude(height=battery_cradle_height)
            difference() {
                rounded_rect_2d(battery_cradle_outer, battery_corner_radius);
                rounded_rect_2d(battery_pocket, battery_corner_radius - 0.5);
                // Open the USB-side end for battery leads.
                translate([0, -battery_cradle_outer[1]/2])
                    square([battery_wire_exit_width, 4.0], center=true);
            }
}

module carrier_supports() {
    post_height = carrier_under_z - floor_z;
    for (x = [-14.4, 14.4])
        for (y = [-8.5, 8.5])
            translate([x, y, floor_z])
                cylinder(d=carrier_post_diameter, h=post_height);
}

module base() {
    union() {
        difference() {
            cylinder(d=puck_diameter, h=base_height);

            // Magnet pocket: preserved at 29.21 mm x 2.54 mm.
            translate([0, 0, -epsilon])
                cylinder(d=magnet_diameter, h=magnet_depth + epsilon);

            // Main electronics cavity, open at the top.
            translate([0, 0, floor_z])
                cylinder(d=inner_diameter, h=base_height - floor_z + epsilon);

            usb_through_cutout();
        }
        battery_cradle();
        carrier_supports();
    }
}

module carrier() {
    union() {
        difference() {
            linear_extrude(height=carrier_thickness)
                rounded_rect_2d(carrier_size, carrier_corner_radius);

            // Power-wire pass-through beside the XIAO footprint.
            translate([12.5, -4.0, -epsilon])
                linear_extrude(height=carrier_thickness + 2*epsilon)
                    rounded_rect_2d(carrier_wire_slot, 1.5);
        }

        // Low rails locate the board without blocking its ends or microSD.
        for (x = [-(board_rail_gap + board_rail_width)/2,
                   (board_rail_gap + board_rail_width)/2])
            translate([x, 0, carrier_thickness + board_rail_height/2])
                cube([board_rail_width, board_rail_length,
                      board_rail_height], center=true);
    }
}

module lid_logo_cutout() {
    // The logo itself is the microphone grille: cut completely through the
    // lid plate instead of using separate vent slots.
    translate([0, logo_y, -epsilon])
        linear_extrude(height=lid_plate + 2*epsilon)
            mirror([1, 0, 0])
                resize([0, logo_height], auto=true)
                    import("huh_logo_trace.svg", center=true);
}

module lid_antenna_pocket() {
    translate([antenna_pocket_x, 0, -epsilon])
        linear_extrude(height=antenna_pocket_depth + epsilon)
            rounded_rect_2d(antenna_pocket_size, antenna_corner_radius);
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
            lid_antenna_pocket();
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
} else if (part == "carrier") {
    carrier();
} else {
    translate([-50, 0, 0]) base();
    translate([0, 0, 0]) carrier();
    // Exploded design preview with the decorated exterior facing upward.
    translate([50, 0, lid_skirt_height]) lid();
}
