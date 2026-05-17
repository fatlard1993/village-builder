#!/usr/bin/env python3
"""Generate a Pumpkin Patch NBT structure template.

Seasonal easter egg — registered in the build pool during October.
A 9x3x9 farm with irrigated farmland, fully grown pumpkins, a
decorative pumpkin pile in one corner, and an armor stand scarecrow
with a carved pumpkin on its head.

Layout (9x9, y=0 is ground level):
  - Water channels in a cross pattern for irrigation
  - Farmland with pumpkin stems between channels
  - Fully grown pumpkins on dirt adjacent to stems
  - Corner pumpkin pile: stacked pumpkins + jack o'lanterns
  - Armor stand scarecrow in the field
  - Fence border on two sides for a rustic look
"""

import gzip, struct, io, os


# NBT tag types
TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
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

def write_float(f, val):
    f.write(struct.pack('>f', val))

def write_double(f, val):
    f.write(struct.pack('>d', val))

def write_string(f, val):
    encoded = val.encode('utf-8')
    write_short(f, len(encoded))
    f.write(encoded)

def write_named_tag(f, tag_type, name):
    write_byte(f, tag_type)
    write_string(f, name)

def write_compound_end(f):
    write_byte(f, TAG_END)


def write_item_compound(f, item_id):
    """Write an item compound: {id: "...", count: 1}"""
    write_named_tag(f, TAG_STRING, "id")
    write_string(f, item_id)
    write_named_tag(f, TAG_INT, "count")
    write_int(f, 1)
    write_compound_end(f)


def write_empty_item_compound(f):
    """Write an empty item compound: {}"""
    write_compound_end(f)


def write_armor_stand_entity(f, pos, block_pos):
    """Write an armor stand entity with a carved pumpkin on its head and arms out like a scarecrow."""
    # Entity wrapper compound
    # pos — double list
    write_named_tag(f, TAG_LIST, "pos")
    write_byte(f, TAG_DOUBLE)
    write_int(f, 3)
    write_double(f, pos[0])
    write_double(f, pos[1])
    write_double(f, pos[2])

    # blockPos — int list
    write_named_tag(f, TAG_LIST, "blockPos")
    write_byte(f, TAG_INT)
    write_int(f, 3)
    write_int(f, block_pos[0])
    write_int(f, block_pos[1])
    write_int(f, block_pos[2])

    # nbt — entity data compound
    write_named_tag(f, TAG_COMPOUND, "nbt")

    write_named_tag(f, TAG_STRING, "id")
    write_string(f, "minecraft:armor_stand")

    # ShowArms: 1 — so the scarecrow has arms out
    write_named_tag(f, TAG_BYTE, "ShowArms")
    write_byte(f, 1)

    # Pose — arms stretched out like a scarecrow
    write_named_tag(f, TAG_COMPOUND, "Pose")
    # LeftArm: [0f, 0f, -90f] — straight out to the side
    write_named_tag(f, TAG_LIST, "LeftArm")
    write_byte(f, TAG_FLOAT)
    write_int(f, 3)
    write_float(f, 0.0)
    write_float(f, 0.0)
    write_float(f, -90.0)
    # RightArm: [0f, 0f, 90f] — straight out to the other side
    write_named_tag(f, TAG_LIST, "RightArm")
    write_byte(f, TAG_FLOAT)
    write_int(f, 3)
    write_float(f, 0.0)
    write_float(f, 0.0)
    write_float(f, 90.0)
    write_compound_end(f)  # end Pose

    # ArmorItems: [{}, {}, {}, {id: "minecraft:carved_pumpkin", count: 1}]
    # Slots: feet, legs, chest, head
    write_named_tag(f, TAG_LIST, "ArmorItems")
    write_byte(f, TAG_COMPOUND)
    write_int(f, 4)
    write_empty_item_compound(f)  # feet
    write_empty_item_compound(f)  # legs
    write_empty_item_compound(f)  # chest
    write_item_compound(f, "minecraft:carved_pumpkin")  # head

    # HandItems: [{id: "minecraft:stick", count: 1}, {}] — stick in hand
    write_named_tag(f, TAG_LIST, "HandItems")
    write_byte(f, TAG_COMPOUND)
    write_int(f, 2)
    write_item_compound(f, "minecraft:stick")  # main hand
    write_empty_item_compound(f)  # off hand

    write_compound_end(f)  # end nbt
    write_compound_end(f)  # end entity wrapper


def write_structure_nbt(path, palette, blocks, size, entities=None):
    """Write a Minecraft structure NBT file with optional entities."""
    f = io.BytesIO()
    write_named_tag(f, TAG_COMPOUND, "")

    write_named_tag(f, TAG_LIST, "size")
    write_byte(f, TAG_INT)
    write_int(f, 3)
    for s in size:
        write_int(f, s)

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

    write_named_tag(f, TAG_LIST, "blocks")
    write_byte(f, TAG_COMPOUND)
    write_int(f, len(blocks))
    for block in blocks:
        x, y, z, state = block[0], block[1], block[2], block[3]
        write_named_tag(f, TAG_LIST, "pos")
        write_byte(f, TAG_INT)
        write_int(f, 3)
        write_int(f, x)
        write_int(f, y)
        write_int(f, z)
        write_named_tag(f, TAG_INT, "state")
        write_int(f, state)
        write_compound_end(f)

    # Entities
    write_named_tag(f, TAG_LIST, "entities")
    write_byte(f, TAG_COMPOUND)
    if entities:
        write_int(f, len(entities))
        for entity_writer in entities:
            entity_writer(f)
    else:
        write_int(f, 0)

    write_named_tag(f, TAG_INT, "DataVersion")
    write_int(f, 4189)

    write_compound_end(f)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as gz:
        gz.write(f.getvalue())
    entity_count = len(entities) if entities else 0
    print(f"  {path.split('/')[-1]}: {len(blocks)} blocks, {entity_count} entities, {size[0]}x{size[1]}x{size[2]}")


def build_pumpkin_farm():
    """Build a 9x9 pumpkin patch with water channels, farmland, a pumpkin pile, and a scarecrow."""

    P = [
        {"Name": "minecraft:dirt"},                                                           # 0
        {"Name": "minecraft:farmland", "Properties": {"moisture": "7"}},                      # 1 - wet farmland
        {"Name": "minecraft:water", "Properties": {"level": "0"}},                            # 2
        {"Name": "minecraft:pumpkin"},                                                        # 3
        {"Name": "minecraft:carved_pumpkin", "Properties": {"facing": "south"}},              # 4
        {"Name": "minecraft:jack_o_lantern", "Properties": {"facing": "south"}},              # 5
        {"Name": "minecraft:pumpkin_stem", "Properties": {"age": "7"}},                       # 6 - fully grown stem
        {"Name": "minecraft:oak_fence", "Properties": {
            "north": "false", "south": "false", "east": "false", "west": "false",
            "waterlogged": "false"}},                                                         # 7
        {"Name": "minecraft:hay_block", "Properties": {"axis": "y"}},                        # 8
        {"Name": "minecraft:cobblestone"},                                                    # 9
    ]

    W, D = 9, 9
    B = []
    a = lambda x, y, z, s: B.append((x, y, z, s))

    # Water channel positions — cross pattern through the center
    water_x = 4  # vertical channel
    water_z = 4  # horizontal channel

    # Y=0 (below ground): cobblestone foundation under water channels
    for x in range(W):
        a(x, 0, water_z, 9)  # under horizontal channel
    for z in range(D):
        a(water_x, 0, z, 9)  # under vertical channel

    # Y=1 (ground level): farmland, water channels, dirt borders
    for x in range(W):
        for z in range(D):
            if x == water_x or z == water_z:
                a(x, 1, z, 2)  # water channels
            else:
                a(x, 1, z, 1)  # farmland

    # Y=2 (crop level): pumpkin stems on farmland, pumpkins on some spots
    for x in range(W):
        for z in range(D):
            if x == water_x or z == water_z:
                continue  # skip water
            # Checkerboard: stems and pumpkins alternate
            if (x + z) % 2 == 0:
                a(x, 2, z, 6)  # pumpkin stem
            else:
                a(x, 2, z, 3)  # pumpkin

    # Pumpkin pile in corner (0,0) — replace some farmland blocks
    pile_positions = [
        # Base layer — dirt + pumpkins and hay
        (0, 1, 0, 0),   # dirt base (replace farmland)
        (1, 1, 0, 0),
        (0, 1, 1, 0),
        (1, 1, 1, 0),
        (0, 2, 0, 3),   # pumpkins
        (1, 2, 0, 3),
        (0, 2, 1, 8),   # hay bale
        (1, 2, 1, 3),
        # Second layer
        (0, 3, 0, 5),   # jack o'lantern on top
        (1, 3, 0, 4),   # carved pumpkin
    ]
    for px, py, pz, ps in pile_positions:
        a(px, py, pz, ps)

    # Fence along two edges (south and east) for rustic border
    for x in range(W):
        a(x, 2, D - 1, 7)
    for z in range(D - 1):
        a(W - 1, 2, z, 7)

    # Scarecrow armor stand — in the field near center, standing on farmland
    # Position at (6, 2, 6) — in the pumpkin rows, east of the water cross
    scarecrow_bx, scarecrow_by, scarecrow_bz = 6, 2, 6
    scarecrow_entity = lambda f: write_armor_stand_entity(
        f,
        pos=[scarecrow_bx + 0.5, scarecrow_by, scarecrow_bz + 0.5],  # center of block
        block_pos=[scarecrow_bx, scarecrow_by, scarecrow_bz],
    )

    height = 4  # foundation + ground + crops + pile top
    return P, B, (W, height, D), [scarecrow_entity]


def main():
    base = os.path.dirname(os.path.abspath(__file__))
    struct_dir = os.path.join(base, "src", "main", "resources", "data", "village-builder", "structure")

    print("Generating Pumpkin Patch:")
    palette, blocks, size, entities = build_pumpkin_farm()
    path = os.path.join(struct_dir, "pumpkin_farm.nbt")
    write_structure_nbt(path, palette, blocks, size, entities)


if __name__ == "__main__":
    main()
