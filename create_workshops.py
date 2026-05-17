#!/usr/bin/env python3
"""Generate Builder's Workshop NBT templates that match each biome's vanilla village style.

Architecture reference (from vanilla 1.21.11 structure analysis):
  Plains:  cobblestone base, oak_planks upper, oak_log corners, oak_stairs roof
  Taiga:   spruce_log heavy framing, cobblestone base, spruce_trapdoor decor
  Desert:  smooth_sandstone walls, cut_sandstone detail, flat slab roof, jungle_door
  Savanna: acacia_log frame, acacia_planks walls, acacia_stairs roof, terracotta accent
  Snowy:   stripped_spruce_wood frame, spruce_planks walls, snow on roof, diorite accent

Each workshop is 5 wide x 5 tall x 6 deep — small and humble, fitting for the
builders who construct everything else in the village.
"""

import gzip, struct, io, os


def write_nbt_byte(f, val):
    f.write(struct.pack('>b', val))

def write_nbt_short(f, val):
    f.write(struct.pack('>h', val))

def write_nbt_int(f, val):
    f.write(struct.pack('>i', val))

def write_nbt_string(f, val):
    encoded = val.encode('utf-8')
    write_nbt_short(f, len(encoded))
    f.write(encoded)

def write_named_tag(f, tag_type, name):
    write_nbt_byte(f, tag_type)
    write_nbt_string(f, name)

def write_compound_end(f):
    write_nbt_byte(f, 0)


def write_structure_nbt(path, palette, blocks, size):
    """Write a Minecraft structure NBT file."""
    f = io.BytesIO()
    write_named_tag(f, 10, "")

    write_named_tag(f, 9, "size")
    write_nbt_byte(f, 3)
    write_nbt_int(f, 3)
    for s in size:
        write_nbt_int(f, s)

    write_named_tag(f, 9, "palette")
    write_nbt_byte(f, 10)
    write_nbt_int(f, len(palette))
    for entry in palette:
        write_named_tag(f, 8, "Name")
        write_nbt_string(f, entry["Name"])
        if "Properties" in entry:
            write_named_tag(f, 10, "Properties")
            for k, v in entry["Properties"].items():
                write_named_tag(f, 8, k)
                write_nbt_string(f, v)
            write_compound_end(f)
        write_compound_end(f)

    write_named_tag(f, 9, "blocks")
    write_nbt_byte(f, 10)
    write_nbt_int(f, len(blocks))
    for x, y, z, state in blocks:
        write_named_tag(f, 9, "pos")
        write_nbt_byte(f, 3)
        write_nbt_int(f, 3)
        write_nbt_int(f, x)
        write_nbt_int(f, y)
        write_nbt_int(f, z)
        write_named_tag(f, 3, "state")
        write_nbt_int(f, state)
        write_compound_end(f)

    write_named_tag(f, 9, "entities")
    write_nbt_byte(f, 10)
    write_nbt_int(f, 0)

    write_named_tag(f, 3, "DataVersion")
    write_nbt_int(f, 4189)

    write_compound_end(f)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as gz:
        gz.write(f.getvalue())
    print(f"  {path.split('/')[-1]}: {len(blocks)} blocks, {size[0]}x{size[1]}x{size[2]}")


# ── Biome-specific builders ──────────────────────────────────────────

W, D = 5, 6  # All workshops share footprint: 5 wide, 6 deep

def is_wall(x, z):
    return x == 0 or x == W-1 or z == 0 or z == D-1

def is_corner(x, z):
    return (x == 0 or x == W-1) and (z == 0 or z == D-1)


def build_plains():
    """Plains: cobblestone base, oak plank upper walls, oak log corners, oak stairs roof."""
    P = [
        {"Name": "minecraft:cobblestone"},                          # 0
        {"Name": "minecraft:oak_planks"},                           # 1
        {"Name": "minecraft:oak_log", "Properties": {"axis": "y"}},# 2
        {"Name": "minecraft:oak_stairs", "Properties": {"facing": "east", "half": "bottom", "shape": "straight"}},  # 3
        {"Name": "minecraft:oak_stairs", "Properties": {"facing": "west", "half": "bottom", "shape": "straight"}},  # 4
        {"Name": "minecraft:oak_slab", "Properties": {"type": "bottom"}},  # 5
        {"Name": "minecraft:oak_door", "Properties": {"half": "lower", "facing": "south", "hinge": "left", "open": "false"}},  # 6
        {"Name": "minecraft:oak_door", "Properties": {"half": "upper", "facing": "south", "hinge": "left", "open": "false"}},  # 7
        {"Name": "minecraft:glass_pane", "Properties": {"north": "true", "south": "true", "east": "false", "west": "false"}},  # 8
        {"Name": "village-builder:builders_table"},                 # 9
        {"Name": "minecraft:barrel", "Properties": {"facing": "up", "open": "false"}},  # 10
        {"Name": "minecraft:wall_torch", "Properties": {"facing": "north"}},  # 11
    ]
    B = []
    a = lambda x,y,z,s: B.append((x,y,z,s))

    for x in range(W):
        for z in range(D):
            # Y0: cobblestone foundation
            a(x, 0, z, 0)
            # Y1: cobblestone walls, log corners, door
            if is_corner(x, z):       a(x, 1, z, 2)
            elif x == 2 and z == 0:   a(x, 1, z, 6)  # door lower
            elif is_wall(x, z):       a(x, 1, z, 0)
            else:                     a(x, 1, z, 1)  # plank floor
            # Y2: oak plank walls, log corners, door top, windows
            if is_corner(x, z):       a(x, 2, z, 2)
            elif x == 2 and z == 0:   a(x, 2, z, 7)  # door upper
            elif x == 2 and z == D-1: a(x, 2, z, 8)  # window back
            elif z == 3 and (x == 0 or x == W-1): a(x, 2, z, 8)  # side windows
            elif is_wall(x, z):       a(x, 2, z, 1)
            # Y3: plank walls top ring
            if is_wall(x, z):         a(x, 3, z, 1)
            # Y4: roof — stairs on east/west edges, slabs middle
            if x == 0:                a(x, 4, z, 3)  # east-facing stairs
            elif x == W-1:            a(x, 4, z, 4)  # west-facing stairs
            else:                     a(x, 4, z, 5)  # slabs

    # Interior
    a(2, 2, 4, 9)   # builder's table center-back
    a(1, 2, 4, 10)  # barrel
    a(2, 3, 3, 11)  # wall torch

    return P, B, (W, 5, D)


def build_taiga():
    """Taiga: heavy spruce log framing, cobblestone base, spruce trapdoor decor."""
    P = [
        {"Name": "minecraft:cobblestone"},                            # 0
        {"Name": "minecraft:spruce_planks"},                          # 1
        {"Name": "minecraft:spruce_log", "Properties": {"axis": "y"}},# 2
        {"Name": "minecraft:spruce_log", "Properties": {"axis": "x"}},# 3  horizontal beam
        {"Name": "minecraft:spruce_stairs", "Properties": {"facing": "east", "half": "bottom", "shape": "straight"}},  # 4
        {"Name": "minecraft:spruce_stairs", "Properties": {"facing": "west", "half": "bottom", "shape": "straight"}},  # 5
        {"Name": "minecraft:spruce_slab", "Properties": {"type": "bottom"}},  # 6
        {"Name": "minecraft:spruce_door", "Properties": {"half": "lower", "facing": "south", "hinge": "left", "open": "false"}},  # 7
        {"Name": "minecraft:spruce_door", "Properties": {"half": "upper", "facing": "south", "hinge": "left", "open": "false"}},  # 8
        {"Name": "minecraft:spruce_trapdoor", "Properties": {"facing": "north", "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"}},  # 9
        {"Name": "village-builder:builders_table"},                   # 10
        {"Name": "minecraft:barrel", "Properties": {"facing": "up", "open": "false"}},  # 11
        {"Name": "minecraft:wall_torch", "Properties": {"facing": "north"}},  # 12
    ]
    B = []
    a = lambda x,y,z,s: B.append((x,y,z,s))

    for x in range(W):
        for z in range(D):
            a(x, 0, z, 0)  # cobblestone foundation
            # Y1: log corners, cobblestone walls
            if is_corner(x, z):       a(x, 1, z, 2)
            elif x == 2 and z == 0:   a(x, 1, z, 7)  # door
            elif is_wall(x, z):       a(x, 1, z, 0)
            else:                     a(x, 1, z, 1)  # plank floor
            # Y2: log corners and frame, plank fill walls
            if is_corner(x, z):       a(x, 2, z, 2)
            elif x == 2 and z == 0:   a(x, 2, z, 8)  # door upper
            elif x == 2 and z == D-1: a(x, 2, z, 9)  # trapdoor window (taiga style)
            elif is_wall(x, z):       a(x, 2, z, 1)
            # Y3: horizontal log beam across top, plank fill
            if is_corner(x, z):       a(x, 3, z, 2)
            elif z == 0 or z == D-1:  a(x, 3, z, 3)  # horizontal log beam front/back
            elif is_wall(x, z):       a(x, 3, z, 1)
            # Y4: roof
            if x == 0:                a(x, 4, z, 4)
            elif x == W-1:            a(x, 4, z, 5)
            else:                     a(x, 4, z, 6)

    a(2, 2, 4, 10)  # builder's table
    a(1, 2, 4, 11)  # barrel
    a(2, 3, 3, 12)  # wall torch

    return P, B, (W, 5, D)


def build_desert():
    """Desert: smooth sandstone walls, cut sandstone detail, flat slab roof, jungle door."""
    P = [
        {"Name": "minecraft:smooth_sandstone"},                       # 0
        {"Name": "minecraft:cut_sandstone"},                          # 1
        {"Name": "minecraft:smooth_sandstone_slab", "Properties": {"type": "bottom"}},  # 2
        {"Name": "minecraft:smooth_sandstone_stairs", "Properties": {"facing": "east", "half": "bottom", "shape": "straight"}},  # 3 (decorative step)
        {"Name": "minecraft:jungle_door", "Properties": {"half": "lower", "facing": "south", "hinge": "left", "open": "false"}},  # 4
        {"Name": "minecraft:jungle_door", "Properties": {"half": "upper", "facing": "south", "hinge": "left", "open": "false"}},  # 5
        {"Name": "minecraft:sandstone_wall", "Properties": {"north": "none", "south": "none", "east": "none", "west": "none", "up": "true", "waterlogged": "false"}},  # 6
        {"Name": "village-builder:builders_table"},                   # 7
        {"Name": "minecraft:barrel", "Properties": {"facing": "up", "open": "false"}},  # 8
        {"Name": "minecraft:wall_torch", "Properties": {"facing": "north"}},  # 9
        {"Name": "minecraft:smooth_sandstone_slab", "Properties": {"type": "top"}},  # 10 (floor)
    ]
    B = []
    a = lambda x,y,z,s: B.append((x,y,z,s))

    for x in range(W):
        for z in range(D):
            a(x, 0, z, 0)  # sandstone foundation
            # Y1: smooth sandstone walls, cut sandstone detail at corners
            if is_corner(x, z):       a(x, 1, z, 1)  # cut sandstone corners
            elif x == 2 and z == 0:   a(x, 1, z, 4)  # door
            elif is_wall(x, z):       a(x, 1, z, 0)
            else:                     a(x, 1, z, 10)  # sandstone slab floor (top half)
            # Y2: walls continue, window openings
            if is_corner(x, z):       a(x, 2, z, 1)
            elif x == 2 and z == 0:   a(x, 2, z, 5)  # door upper
            elif is_wall(x, z):       a(x, 2, z, 0)
            # Y3: top wall ring with cut sandstone trim
            if is_corner(x, z):       a(x, 3, z, 1)
            elif is_wall(x, z):       a(x, 3, z, 1)  # cut sandstone top trim
            # Y4: flat slab roof (desert style — no pitched roof)
            a(x, 4, z, 2)

    a(2, 2, 4, 7)   # builder's table
    a(1, 2, 4, 8)   # barrel
    a(2, 3, 3, 9)   # wall torch

    return P, B, (W, 5, D)


def build_savanna():
    """Savanna: acacia log frame, acacia plank walls, acacia stairs roof, terracotta accent."""
    P = [
        {"Name": "minecraft:acacia_planks"},                          # 0
        {"Name": "minecraft:acacia_log", "Properties": {"axis": "y"}},# 1
        {"Name": "minecraft:acacia_stairs", "Properties": {"facing": "east", "half": "bottom", "shape": "straight"}},  # 2
        {"Name": "minecraft:acacia_stairs", "Properties": {"facing": "west", "half": "bottom", "shape": "straight"}},  # 3
        {"Name": "minecraft:acacia_slab", "Properties": {"type": "bottom"}},  # 4
        {"Name": "minecraft:acacia_door", "Properties": {"half": "lower", "facing": "south", "hinge": "left", "open": "false"}},  # 5
        {"Name": "minecraft:acacia_door", "Properties": {"half": "upper", "facing": "south", "hinge": "left", "open": "false"}},  # 6
        {"Name": "minecraft:orange_terracotta"},                      # 7
        {"Name": "minecraft:glass_pane", "Properties": {"north": "true", "south": "true", "east": "false", "west": "false"}},  # 8
        {"Name": "village-builder:builders_table"},                   # 9
        {"Name": "minecraft:barrel", "Properties": {"facing": "up", "open": "false"}},  # 10
        {"Name": "minecraft:torch", "Properties": {"lit": "true"}},   # 11  (freestanding, savanna is more open)
    ]
    B = []
    a = lambda x,y,z,s: B.append((x,y,z,s))

    for x in range(W):
        for z in range(D):
            # Y0: acacia plank foundation (savanna uses wood not stone)
            a(x, 0, z, 0)
            # Y1: log frame corners, plank walls
            if is_corner(x, z):       a(x, 1, z, 1)
            elif x == 2 and z == 0:   a(x, 1, z, 5)  # door
            elif is_wall(x, z):       a(x, 1, z, 0)
            else:                     a(x, 1, z, 0)  # plank floor
            # Y2: log corners, plank/terracotta walls, windows
            if is_corner(x, z):       a(x, 2, z, 1)
            elif x == 2 and z == 0:   a(x, 2, z, 6)  # door upper
            elif x == 2 and z == D-1: a(x, 2, z, 8)  # window
            elif z == 3 and x == 0:   a(x, 2, z, 7)  # terracotta accent
            elif z == 3 and x == W-1: a(x, 2, z, 7)  # terracotta accent
            elif is_wall(x, z):       a(x, 2, z, 0)
            # Y3: top ring
            if is_wall(x, z):         a(x, 3, z, 0)
            # Y4: acacia stairs roof
            if x == 0:                a(x, 4, z, 2)
            elif x == W-1:            a(x, 4, z, 3)
            else:                     a(x, 4, z, 4)

    a(2, 2, 4, 9)   # builder's table
    a(1, 2, 4, 10)  # barrel
    a(3, 2, 1, 11)  # torch on floor (open feel)

    return P, B, (W, 5, D)


def build_snowy():
    """Snowy: stripped spruce wood frame, spruce plank walls, snow on roof, diorite accent."""
    P = [
        {"Name": "minecraft:spruce_planks"},                                # 0
        {"Name": "minecraft:stripped_spruce_wood", "Properties": {"axis": "y"}},  # 1 (frame)
        {"Name": "minecraft:spruce_stairs", "Properties": {"facing": "east", "half": "bottom", "shape": "straight"}},  # 2
        {"Name": "minecraft:spruce_stairs", "Properties": {"facing": "west", "half": "bottom", "shape": "straight"}},  # 3
        {"Name": "minecraft:spruce_slab", "Properties": {"type": "bottom"}},  # 4
        {"Name": "minecraft:spruce_door", "Properties": {"half": "lower", "facing": "south", "hinge": "left", "open": "false"}},  # 5
        {"Name": "minecraft:spruce_door", "Properties": {"half": "upper", "facing": "south", "hinge": "left", "open": "false"}},  # 6
        {"Name": "minecraft:snow", "Properties": {"layers": "1"}},         # 7 (snow layer)
        {"Name": "minecraft:diorite"},                                      # 8 (accent)
        {"Name": "minecraft:glass_pane", "Properties": {"north": "true", "south": "true", "east": "false", "west": "false"}},  # 9
        {"Name": "village-builder:builders_table"},                         # 10
        {"Name": "minecraft:barrel", "Properties": {"facing": "up", "open": "false"}},  # 11
        {"Name": "minecraft:wall_torch", "Properties": {"facing": "north"}},  # 12
    ]
    B = []
    a = lambda x,y,z,s: B.append((x,y,z,s))

    for x in range(W):
        for z in range(D):
            # Y0: spruce plank foundation
            a(x, 0, z, 0)
            # Y1: stripped spruce frame, plank walls, diorite base
            if is_corner(x, z):       a(x, 1, z, 1)  # stripped spruce frame
            elif x == 2 and z == 0:   a(x, 1, z, 5)  # door
            elif z == 0 and (x == 1 or x == 3): a(x, 1, z, 8)  # diorite flanking door
            elif is_wall(x, z):       a(x, 1, z, 0)
            else:                     a(x, 1, z, 0)  # plank floor
            # Y2: frame + plank walls, windows
            if is_corner(x, z):       a(x, 2, z, 1)
            elif x == 2 and z == 0:   a(x, 2, z, 6)  # door upper
            elif x == 2 and z == D-1: a(x, 2, z, 9)  # window
            elif is_wall(x, z):       a(x, 2, z, 0)
            # Y3: top ring with stripped spruce
            if is_corner(x, z):       a(x, 3, z, 1)
            elif is_wall(x, z):       a(x, 3, z, 0)
            # Y4: spruce stairs/slab roof
            if x == 0:                a(x, 4, z, 2)
            elif x == W-1:            a(x, 4, z, 3)
            else:                     a(x, 4, z, 4)

    # Snow on roof (Y5)
    for x in range(1, W-1):
        for z in range(D):
            B.append((x, 5, z, 7))

    a(2, 2, 4, 10)  # builder's table
    a(1, 2, 4, 11)  # barrel
    a(2, 3, 3, 12)  # wall torch

    return P, B, (W, 6, D)  # height 6 because of snow layer


def main():
    base = os.path.dirname(os.path.abspath(__file__))
    struct_dir = os.path.join(base, "src", "main", "resources", "data", "village-builder", "structure")

    builders = {
        "plains": build_plains,
        "taiga": build_taiga,
        "desert": build_desert,
        "savanna": build_savanna,
        "snowy": build_snowy,
    }

    print("Generating workshops:")
    for biome, builder in builders.items():
        palette, blocks, size = builder()
        path = os.path.join(struct_dir, f"builders_workshop_{biome}.nbt")
        write_structure_nbt(path, palette, blocks, size)


if __name__ == "__main__":
    main()
