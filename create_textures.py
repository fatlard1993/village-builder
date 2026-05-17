#!/usr/bin/env python3
"""Create textures for the builder villager and table item.

UV regions verified against vanilla profession textures (armorer.png,
mason.png, farmer.png) from the 1.21.11 client JAR.

The jacket model is uv(0, 38), cuboid 8x20x6, Dilation(0.5F):
  - y=38-43:  Jacket TOP/BOTTOM faces — render at neck height, causes
              a visible floating collar. AVOID drawing here.
  - y=44-63:  Jacket VERTICAL faces — front (x=6-13), right (x=0-5),
              left (x=14-19), back (x=20-27). Safe for apron.

Arm cross piece is uv(40, 38), cuboid 8x4x4:
  - y=38-41:  Top/bottom faces. AVOID.
  - y=42-45:  Vertical faces (x=40-63).

Individual arms are uv(44, 22), cuboid 4x8x4:
  - y=22-25:  Top/bottom faces. AVOID.
  - y=26-33:  Vertical faces (x=44-59).

Head/hat overlay: y=0-15. Farmer uses this for straw hat.
"""

from PIL import Image, ImageDraw
import os


def create_builder_villager_texture():
    """Create a builder villager profession overlay.

    A work apron rendered only on jacket vertical faces (y=44+)
    to avoid the floating collar artifact from top-face pixels.
    """
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Palette
    leather       = (139, 90, 60, 255)
    leather_dark  = (100, 65, 43, 255)
    leather_light = (160, 110, 75, 255)
    accent        = (210, 150, 50, 255)   # Gold-orange buckle/trim
    accent_dark   = (170, 115, 30, 255)

    # --- JACKET VERTICAL FACES (y=44-63) ---
    # Only drawing on vertical faces avoids the floating collar.

    # Front face (x=6-13, y=44-63)
    draw.rectangle([6, 44, 13, 63], fill=leather)
    draw.rectangle([6, 44, 13, 45], fill=leather_light)   # Top highlight
    draw.rectangle([6, 62, 13, 63], fill=leather_dark)     # Bottom shadow
    # Belt buckle
    draw.rectangle([9, 52, 11, 54], fill=accent)
    draw.rectangle([10, 53, 10, 53], fill=accent_dark)

    # Right face (x=0-5, y=44-63)
    draw.rectangle([0, 44, 5, 63], fill=leather)
    draw.rectangle([0, 44, 5, 45], fill=leather_light)

    # Left face (x=14-19, y=44-63)
    draw.rectangle([14, 44, 19, 63], fill=leather)
    draw.rectangle([14, 44, 19, 45], fill=leather_light)

    # Back face (x=20-27, y=44-63)
    draw.rectangle([20, 44, 27, 63], fill=leather)
    draw.rectangle([20, 44, 27, 45], fill=leather_light)
    draw.rectangle([20, 62, 27, 63], fill=leather_dark)

    # Tool belt stripe across all faces at waist level
    draw.rectangle([0, 54, 27, 55], fill=accent_dark)

    return img


def create_builders_table_item_texture():
    """Create a 16x16 item texture for the Builder's Table."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Table top (oak planks color with blueprint/plans on top)
    plank_light = (188, 152, 98, 255)
    plank_dark = (160, 128, 80, 255)
    plank_edge = (120, 96, 64, 255)
    blueprint = (80, 140, 200, 255)
    blueprint_dark = (60, 110, 170, 255)
    paper_white = (230, 225, 210, 255)

    # Table body (sides)
    draw.rectangle([1, 6, 14, 15], fill=plank_dark)
    draw.rectangle([0, 6, 0, 15], fill=plank_edge)
    draw.rectangle([15, 6, 15, 15], fill=plank_edge)
    draw.rectangle([1, 15, 14, 15], fill=plank_edge)

    # Legs
    draw.rectangle([2, 13, 3, 15], fill=plank_edge)
    draw.rectangle([12, 13, 13, 15], fill=plank_edge)

    # Table top surface
    draw.rectangle([0, 4, 15, 7], fill=plank_light)
    draw.rectangle([0, 4, 15, 4], fill=(200, 165, 110, 255))  # Top highlight

    # Blueprint paper on the table
    draw.rectangle([3, 1, 12, 6], fill=blueprint)
    draw.rectangle([4, 2, 11, 5], fill=blueprint_dark)
    # Plan lines
    draw.line([5, 3, 10, 3], fill=paper_white)
    draw.line([5, 5, 8, 5], fill=paper_white)

    return img


def main():
    base = os.path.dirname(os.path.abspath(__file__))
    resources = os.path.join(base, 'src', 'main', 'resources', 'assets', 'village-builder')

    # Villager profession texture
    villager_dir = os.path.join(resources, 'textures', 'entity', 'villager', 'profession')
    os.makedirs(villager_dir, exist_ok=True)
    villager_path = os.path.join(villager_dir, 'builder.png')
    create_builder_villager_texture().save(villager_path)
    print(f"Saved villager texture to {villager_path}")

    # Item texture
    item_dir = os.path.join(resources, 'textures', 'item')
    os.makedirs(item_dir, exist_ok=True)
    item_path = os.path.join(item_dir, 'builders_table.png')
    create_builders_table_item_texture().save(item_path)
    print(f"Saved item texture to {item_path}")


if __name__ == '__main__':
    main()
