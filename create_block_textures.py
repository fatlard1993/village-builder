#!/usr/bin/env python3
"""Redraw the builder's table's block faces on top of vanilla's own oak planks.

The faces this replaces were drawn freehand, and it showed: a lattice of perfectly even
planks and flat fills next to vanilla wood, which has no two pixels quite alike and no
straight run longer than it needs. Nothing here is picked by eye. The plank field is
oak_planks.png itself, the paper ramp is the fletching table's parchment, the shelf is the
cartography table's drawer in oak tones, and the noise is seeded so the output is stable.

The other lesson taken from vanilla: it does not draw pictures at sixteen pixels. The
cartography table's chart is a pale sheet with four red dots on it. So the top is gridded
paper with one outline set out on it, which reads as a plan without owing anybody a
building.

Uses ImageMagick rather than Pillow, which is not installed on the machine this suite is
built on - create_textures.py needs Pillow and can no longer be run here.

    python3 create_block_textures.py
"""
import glob, os, random, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
BLOCKS = os.path.join(HERE, "src/main/resources/assets/village-builder/textures/block")

def vanilla_planks(scratch):
    """oak_planks.png, straight out of whichever client jar Loom has cached."""
    jars = sorted(glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/*/minecraft-client.jar")), key=os.path.getmtime)
    if not jars:
        sys.exit("no Loom client jar cached - run a build first")
    subprocess.run(["unzip", "-o", "-j", "-q", jars[-1],
                    "assets/minecraft/textures/block/oak_planks.png", "-d", scratch], check=True)
    raw = os.path.join(scratch, "oak_planks.raw")
    subprocess.run(["magick", os.path.join(scratch, "oak_planks.png"),
                    "-depth", "8", f"RGBA:{raw}"], check=True)
    return raw

S = tempfile.mkdtemp(prefix="builders-table-")
PLANKS_RAW = vanilla_planks(S)
W = H = 16
def load(path):
    raw = open(path, "rb").read()
    return [list(raw[i:i + 4]) for i in range(0, len(raw), 4)]

def save(px, path):
    open(f"{S}/out.raw", "wb").write(bytes(b for p in px for b in p))
    subprocess.run(["magick", "-size", f"{W}x{H}", "-depth", "8",
                    f"RGBA:{S}/out.raw", f"PNG32:{path}"], check=True)

def rgb(hexstr):
    return [int(hexstr[i:i + 2], 16) for i in (0, 2, 4)] + [255]

# ── vanilla-sampled palettes ────────────────────────────────────────────────
PAPER_LIGHT, PAPER, PAPER_MID = rgb("E4CD8E"), rgb("D7C185"), rgb("C8B77A")
PAPER_SHADE, PAPER_EDGE      = rgb("B8A875"), rgb("9E8B61")
INK, INK_SOFT                = rgb("5E4A28"), rgb("8A7248")
WOOD_MID, WOOD_DARK          = rgb("967441"), rgb("67502C")
RECESS, RECESS_DEEP          = rgb("53401F"), rgb("3A2C16")

planks = load(PLANKS_RAW)
at = lambda px, x, y: px[y * W + x]
def put(px, x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y * W + x] = list(c)

def darken(px, x, y, amount=0.72):
    c = at(px, x, y)
    put(px, x, y, [int(c[0] * amount), int(c[1] * amount), int(c[2] * amount), 255])

# ══ TOP ═════════════════════════════════════════════════════════════════════
# A sheet of plans held down by a straight-edge, with the plank field framing it.
rnd = random.Random(1147)
top = [list(p) for p in planks]

PX0, PX1, PY0, PY1 = 2, 13, 2, 12          # the sheet
for y in range(PY0, PY1 + 1):
    for x in range(PX0, PX1 + 1):
        put(top, x, y, rnd.choice([PAPER, PAPER, PAPER, PAPER_LIGHT, PAPER_MID]))
for x in range(PX0, PX1 + 1):              # lit along the top, in shade at the foot
    put(top, x, PY0, PAPER_LIGHT)
    put(top, x, PY1, PAPER_SHADE)
for y in range(PY0, PY1 + 1):
    put(top, PX0, y, PAPER_LIGHT if y < PY1 else PAPER_SHADE)
    put(top, PX1, y, PAPER_SHADE)
put(top, PX1, PY0, PAPER_MID)
for x in range(PX0 + 1, PX1 + 2):          # the sheet lifts off the wood
    darken(top, x, PY1 + 1)
for y in range(PY0 + 1, PY1 + 2):
    darken(top, PX1 + 1, y)

# Ruled squares with something set out on them. Vanilla does not draw pictures at this
# size - the cartography table's chart is a pale sheet with four red dots - so this is
# gridded paper with one bold outline on it, which reads as a plan without having to be
# legible as any particular building.
for x in range(PX0 + 1, PX1):              # the grid, a shade under the paper
    for y in range(PY0 + 1, PY1):
        if x % 3 == 1 or y % 3 == 1:
            put(top, x, y, PAPER_SHADE)

RX0, RX1, RY0, RY1 = 5, 11, 5, 10          # the outline set out on it
for x in range(RX0, RX1 + 1):
    put(top, x, RY0, INK)
    put(top, x, RY1, INK)
for y in range(RY0, RY1 + 1):
    put(top, RX0, y, INK)
    put(top, RX1, y, INK)
for y in range(RY0 + 1, RY1):              # one wall through it
    put(top, 8, y, INK_SOFT)
put(top, 8, RY1, PAPER_MID)                # left open, the way through

put(top, 3, 3, INK_SOFT)                   # a weight holding the corner down
put(top, 4, 3, INK)
put(top, 3, 4, INK)

save(top, os.path.join(BLOCKS, "builders_table_top.png"))

# ══ SIDE ════════════════════════════════════════════════════════════════════
# An open shelf of rolled plans, in the same idiom as the cartography table's drawer.
rnd = random.Random(6021)
side = [list(p) for p in planks]

SX0, SX1, SY0, SY1 = 2, 13, 8, 13
for y in range(SY0, SY1 + 1):              # the recess, cut back into the planks
    for x in range(SX0, SX1 + 1):
        put(side, x, y, RECESS)
for x in range(SX0, SX1 + 1):              # deepest where the lip overhangs it
    put(side, x, SY0, RECESS_DEEP)
for y in range(SY0, SY1 + 1):
    put(side, SX0, y, RECESS_DEEP)
for x in range(SX0, SX1 + 1):              # the shelf's own edge catches the light
    put(side, x, SY1, WOOD_MID)
put(side, SX1, SY1, WOOD_DARK)

# Rolls, ends out. Each is lit top-left and shaded bottom-right so it reads round rather
# than square, and one sits lower than its neighbours so the row is not a set of teeth.
for ox, drop in ((3, 0), (6, 1), (9, 0), (12, 1)):
    ty = SY0 + 2 + drop
    put(side, ox, ty, PAPER_LIGHT)
    put(side, ox + 1, ty, PAPER)
    put(side, ox, ty + 1, PAPER_MID)
    put(side, ox + 1, ty + 1, PAPER_EDGE)

save(side, os.path.join(BLOCKS, "builders_table_side.png"))
print("  wrote builders_table_top.png and builders_table_side.png")
