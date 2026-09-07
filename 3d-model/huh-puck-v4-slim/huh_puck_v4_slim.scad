// Huh XIAO ESP32S3 Sense puck — V4 slim/wide battery revision
// Camera and piezo removed; LiPo powered with USB-C charging.
// Units: millimetres. Set part to "base", "lid", or "both".

part = is_undef(part) ? "both" : part;
$fn = 160;

// V2 height is retained. Battery and XIAO sit side-by-side instead of stacked.
// The proven magnet recess remains exactly unchanged.
puck_diameter = 54.00;
magnet_diameter = 29.21;
magnet_depth = 2.54;

base_height = 11.50;
floor_z = 3.05;                // leaves 0.51 mm above the magnet recess
inner_diameter = 50.80;

battery_center = [-8.8, 0];
battery_pocket = [21.0, 31.0];
battery_cradle_outer = [22.4, 32.4];
battery_corner_radius = 3.0;
battery_cradle_height = 1.0;
battery_wire_exit_width = 8.0;

board_center = [12.5, 0];
board_rail_gap = 19.2;
board_rail_width = 0.8;
board_rail_height = 0.8;
board_rail_length = 22.4;

connector_bay_center = [0, -20.0];
connector_bay_inner = [7.5, 5.5];
connector_bay_outer = [9.0, 7.0];
connector_bay_wall_height = 0.8;

// True through-opening for a USB-C plug, with room for a typical cable shell.
usb_width = 13.00;
usb_height = 6.00;
usb_bottom_z = 3.20;
usb_x = board_center[0];
usb_flare_width = 15.0;
usb_flare_height = 7.5;

lid_plate = 2.00;
lid_skirt_height = 2.50;
lid_skirt_outer_diameter = 50.40;
lid_skirt_wall = 1.20;
fit_relief_width = 0.65;

logo_height = 18.00;
logo_y = 0.00;

antenna_pocket_size = [20.0, 8.0];
antenna_pocket_y = 17.5;
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
    translate([usb_x, -puck_diameter/2, usb_bottom_z + usb_height/2])
        rotate([90, 0, 0])
            linear_extrude(height=16.0, center=true)
                rounded_slot_2d(usb_width, usb_height);

    translate([usb_x, -puck_diameter/2 - 0.5,
               usb_bottom_z + usb_height/2])
        rotate([90, 0, 0])
            linear_extrude(height=4.0, center=true)
                rounded_slot_2d(usb_flare_width, usb_flare_height);
}

module battery_cradle() {
    translate([battery_center[0], battery_center[1], floor_z])
        linear_extrude(height=battery_cradle_height)
            difference() {
                rounded_rect_2d(battery_cradle_outer, battery_corner_radius);
                rounded_rect_2d(battery_pocket, battery_corner_radius - 0.5);
                translate([0, -battery_cradle_outer[1]/2])
                    square([battery_wire_exit_width, 4.0], center=true);
            }
}

module board_guides() {
    for (x = [board_center[0] - (board_rail_gap + board_rail_width)/2,
              board_center[0] + (board_rail_gap + board_rail_width)/2])
        translate([x, board_center[1], floor_z + board_rail_height/2])
            cube([board_rail_width, board_rail_length,
                  board_rail_height], center=true);
}

module connector_bay() {
    translate([connector_bay_center[0], connector_bay_center[1], floor_z])
        linear_extrude(height=connector_bay_wall_height)
            difference() {
                rounded_rect_2d(connector_bay_outer, 1.5);
                rounded_rect_2d(connector_bay_inner, 1.0);
                square([connector_bay_outer[0] + 1.0, 2.0], center=true);
            }
}

module base() {
    union() {
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
        battery_cradle();
        board_guides();
        connector_bay();
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
    translate([0, antenna_pocket_y, -epsilon])
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
} else {
    translate([-30, 0, 0]) base();
    // Exploded design preview with the decorated exterior facing upward.
    translate([30, 0, lid_skirt_height]) lid();
}
