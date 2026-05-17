#!/usr/bin/env python3
"""Generate a Village Christmas Tree NBT structure template.

Seasonal easter egg — registered in the build pool during November and December.
A ~12-block-tall decorated spruce tree with glowstone ornaments, a gold block
star on top, and four loot chests as presents arranged around the base.

Loot tables on each chest:
  - End City Treasure
  - Bastion Treasure
  - Buried Treasure
  - Ancient City

The tree is 11x13x11 (WxHxD) — large enough to be a village centerpiece.
"""

import gzip, struct, io, os, math

# NBT tag type constants
TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10


def write_byte(f, val):
    f.write(struct.pack('>b', val))

def write_short(f, val):
    f.write(struct.pack('>h', val))

def write_int(f, val):
    f.write(struct.pack('>i', val))

def write_long(f, val):
    f.write(struct.pack('>q', val))

def write_string(f, val):
    encoded = val.encode('utf-8')
    write_short(f, len(encoded))
    f.write(encoded)

def write_named_tag(f, tag_type, name):
    write_byte(f, tag_type)
    write_string(f, name)

def write_compound_end(f):
    write_byte(f, TAG_END)


def write_structure_nbt(path, palette, blocks, size):
    """Write a Minecraft structure NBT file.

    blocks: list of tuples. Each is either:
      (x, y, z, state_index)                — simple block
      (x, y, z, state_index, nbt_dict)      — block with block entity NBT
    """
    f = io.BytesIO()
    write_named_tag(f, TAG_COMPOUND, "")

    # size
    write_named_tag(f, TAG_LIST, "size")
    write_byte(f, TAG_INT)
    write_int(f, 3)
    for s in size:
        write_int(f, s)

    # palette
    write_named_tag(f, TAG_LIST, "palette")
    write_byte(f, TAG_COMPOUND)
    write_int(f, len(palette))
    for entry in palette:
        write_named_tag(f, TAG_STRING, "Name")
        write_string(f, entry["Name"])
        if "Properties" in entry:
            write_named_tag(f, TAG_COMPOUND, "Properties")
            for k, v in entry["Properties"].items():
                write_named_tag(f, TAG_STRING, k)
                write_string(f, v)
            write_compound_end(f)
        write_compound_end(f)

    # blocks
    write_named_tag(f, TAG_LIST, "blocks")
    write_byte(f, TAG_COMPOUND)
    write_int(f, len(blocks))
    for block in blocks:
        x, y, z, state = block[0], block[1], block[2], block[3]
        nbt = block[4] if len(block) > 4 else None

        write_named_tag(f, TAG_LIST, "pos")
        write_byte(f, TAG_INT)
        write_int(f, 3)
        write_int(f, x)
        write_int(f, y)
        write_int(f, z)

        write_named_tag(f, TAG_INT, "state")
        write_int(f, state)

        if nbt is not None:
            write_named_tag(f, TAG_COMPOUND, "nbt")
            for key, (tag_type, value) in nbt.items():
                write_named_tag(f, tag_type, key)
                if tag_type == TAG_STRING:
                    write_string(f, value)
                elif tag_type == TAG_LONG:
                    write_long(f, value)
            write_compound_end(f)

        write_compound_end(f)

    # entities (empty)
    write_named_tag(f, TAG_LIST, "entities")
    write_byte(f, TAG_COMPOUND)
    write_int(f, 0)

    # DataVersion
    write_named_tag(f, TAG_INT, "DataVersion")
    write_int(f, 4189)

    write_compound_end(f)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as gz:
        gz.write(f.getvalue())
    print(f"  {path.split('/')[-1]}: {len(blocks)} blocks, {size[0]}x{size[1]}x{size[2]}")


def build_christmas_tree():
    """Build a large decorated spruce tree with loot chests underneath."""

    # Palette
    P = [
        {"Name": "minecraft:spruce_log", "Properties": {"axis": "y"}},        # 0 - trunk
        {"Name": "minecraft:spruce_leaves", "Properties": {"persistent": "true", "distance": "1"}},  # 1 - leaves
        {"Name": "minecraft:glowstone"},                                        # 2 - ornaments
        {"Name": "minecraft:gold_block"},                                       # 3 - star
        {"Name": "minecraft:chest", "Properties": {"facing": "south", "type": "single", "waterlogged": "false"}},  # 4 - present
    ]

    W, D = 11, 11  # footprint
    cx, cz = W // 2, D // 2  # center at (5, 5)
    B = []

    # Trunk — 10 blocks tall
    trunk_height = 10
    for y in range(trunk_height):
        B.append((cx, y, cz, 0))

    # Conical canopy — layers of spruce leaves narrowing toward the top
    # Each layer: (y_offset, radius)
    layers = [
        (2, 5), (3, 5), (4, 4), (5, 4), (6, 3), (7, 3), (8, 2), (9, 2), (10, 1)
    ]

    ornament_positions = set()
    # Deterministic ornament placement — pick positions on outer edges
    # using a simple hash-based pattern so every generated tree looks the same
    for y_off, radius in layers:
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, dz_max := radius + 1):
                if dx == 0 and dz == 0:
                    continue
                if dx * dx + dz * dz > radius * radius + 1:
                    continue
                # Ornaments on outer ring positions where (dx+dz+y_off) hits a pattern
                is_outer = abs(dx) >= radius - 1 or abs(dz) >= radius - 1
                if is_outer and (dx * 7 + dz * 13 + y_off * 3) % 17 == 0:
                    ornament_positions.add((cx + dx, y_off, cz + dz))

    for y_off, radius in layers:
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                if dx == 0 and dz == 0:
                    continue
                if dx * dx + dz * dz > radius * radius + 1:
                    continue
                bx, bz = cx + dx, cz + dz
                if (bx, y_off, bz) in ornament_positions:
                    B.append((bx, y_off, bz, 2))  # glowstone ornament
                else:
                    B.append((bx, y_off, bz, 1))  # spruce leaves

    # Star on top — gold block
    B.append((cx, trunk_height + 1, cz, 3))

    # Presents — four loot chests around the trunk base
    loot_tables = [
        "minecraft:chests/end_city_treasure",
        "minecraft:chests/bastion_treasure",
        "minecraft:chests/buried_treasure",
        "minecraft:chests/ancient_city",
    ]
    chest_positions = [
        (cx + 1, 0, cz + 1),
        (cx - 1, 0, cz + 1),
        (cx + 1, 0, cz - 1),
        (cx - 1, 0, cz - 1),
    ]
    for (bx, by, bz), loot_table in zip(chest_positions, loot_tables):
        nbt = {
            "id": (TAG_STRING, "minecraft:chest"),
            "LootTable": (TAG_STRING, loot_table),
            "LootTableSeed": (TAG_LONG, 0),
        }
        B.append((bx, by, bz, 4, nbt))

    height = trunk_height + 2 + 1  # trunk + star + 1 above
    return P, B, (W, height, D)


def main():
    base = os.path.dirname(os.path.abspath(__file__))
    struct_dir = os.path.join(base, "src", "main", "resources", "data", "village-builder", "structure")

    print("Generating Christmas tree:")
    palette, blocks, size = build_christmas_tree()
    path = os.path.join(struct_dir, "christmas_tree.nbt")
    write_structure_nbt(path, palette, blocks, size)


if __name__ == "__main__":
    main()
