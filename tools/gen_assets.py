"""Draws the hand truck textures and writes its item models. Run from the repository root:
    python3 tools/gen_assets.py
All art is made here from code: one 16x16 texture per tier with the metal (rails, plate), the checker plate top, the
grip, the tire and the hub.
"""
import json
import pathlib
import random

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[1] / 'src/main/resources/assets/chest_transporter'
TIERS = {
    # base, light, dark
    'wooden': ((156, 122, 74), (190, 152, 97), (104, 80, 48)),
    'copper': ((196, 98, 62), (232, 135, 95), (138, 62, 38)),
    'iron': ((196, 196, 196), (232, 232, 232), (128, 128, 128)),
    'golden': ((236, 190, 48), (255, 232, 120), (178, 132, 20)),
    'diamond': ((74, 220, 205), (165, 250, 235), (30, 150, 138)),
    'netherite': ((78, 70, 75), (112, 102, 108), (44, 39, 42)),
}
TIRE = ((43, 43, 43), (70, 70, 70), (24, 24, 24))
GRIP = ((92, 52, 30), (122, 74, 44), (64, 34, 18))


def shade(color, amount):
    return tuple(max(0, min(255, c + amount)) for c in color) + (255,)


def texture(tier, palette):
    base, light, dark = palette
    rnd = random.Random(tier)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    # metal bars: u 0-8, v 0-8, lighter on the left and top edge
    for x in range(8):
        for y in range(8):
            c = light if x == 0 or y == 0 else dark if x == 7 or y == 7 else base
            px[x, y] = shade(c, rnd.randint(-6, 6))
    # checker plate top: u 8-16, v 8-16, raised diamonds every 2 pixels
    for x in range(8, 16):
        for y in range(8, 16):
            raised = (x + y) % 4 == 0 and x % 2 == 0
            px[x, y] = shade(light if raised else base, rnd.randint(-5, 5))
    for i in range(8, 16):
        px[i, 8] = shade(light, 0)
        px[i, 15] = shade(dark, 0)
    # grip: u 0-8, v 8-10, leather stripes
    for x in range(8):
        for y in range(8, 10):
            g = GRIP[1] if (x % 3 == 0) else GRIP[0] if y == 8 else GRIP[2]
            px[x, y] = shade(g, rnd.randint(-4, 4))
    # wheel side: u 8-12, v 0-4, a round tire with a hub in the tier color
    wheel = [
        '.TT.',
        'THHT',
        'THHT',
        '.TT.',
    ]
    for dy, row in enumerate(wheel):
        for dx, ch in enumerate(row):
            if ch == 'T':
                px[8 + dx, dy] = shade(TIRE[0], 0)
            elif ch == 'H':
                px[8 + dx, dy] = shade(light if dx == 1 and dy == 1 else base, 0)
    # tread: u 12-16, v 0-4
    for x in range(12, 16):
        for y in range(4):
            px[x, y] = shade(TIRE[2] if (x + y) % 2 else TIRE[1], 0)
    # axle: u 8-16, v 4-5
    for x in range(8, 16):
        px[x, 4] = shade(TIRE[1], 0)
        px[x, 5] = shade(TIRE[2], 0)
    return img


def box(frm, to, faces):
    return {'from': frm, 'to': to, 'faces': faces}


def metal_faces(u0=0, v0=0, size=(2, 8)):
    w, h = size
    uv = [u0, v0, u0 + w, v0 + h]
    return {d: {'uv': uv, 'texture': '#truck'} for d in ('north', 'south', 'east', 'west', 'up', 'down')}


def frame_model(tier):
    tex = f'chest_transporter:item/{tier}_chest_transporter'
    plate_top = {'uv': [8, 8, 16, 16], 'texture': '#truck'}
    elements = [
        # toe plate the load stands on
        box([2, 0, 1], [14, 1, 13], {**metal_faces(0, 0, (8, 1)), 'up': plate_top}),
        # rails and cross bars behind the load
        box([2, 0, 13], [3, 15, 14], metal_faces(0, 0, (1, 8))),
        box([13, 0, 13], [14, 15, 14], metal_faces(1, 0, (1, 8))),
        box([3, 5, 13], [13, 6, 14], metal_faces(0, 3, (8, 1))),
        box([3, 10, 13], [13, 11, 14], metal_faces(0, 5, (8, 1))),
        # handle with the grip
        box([2, 15, 13], [14, 16, 14], {d: {'uv': [0, 8, 8, 9], 'texture': '#truck'} for d in ('north', 'south', 'east', 'west', 'up', 'down')}),
        # axle and wheels
        box([1, 1, 14], [15, 2, 15], {d: {'uv': [8, 4, 16, 5], 'texture': '#truck'} for d in ('north', 'south', 'east', 'west', 'up', 'down')}),
    ]
    for x0 in (0, 14.5):
        side = {'uv': [8, 0, 12, 4], 'texture': '#truck'}
        tread = {'uv': [12, 0, 16, 4], 'texture': '#truck'}
        elements.append(box([x0, 0, 12.5], [x0 + 1.5, 3, 15.5],
                            {'east': side, 'west': side, 'north': tread, 'south': tread, 'up': tread, 'down': tread}))
    return {'textures': {'truck': tex, 'particle': tex}, 'elements': elements}


DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
}


def main():
    (ROOT / 'textures/item').mkdir(parents=True, exist_ok=True)
    (ROOT / 'models/item').mkdir(parents=True, exist_ok=True)
    for tier, palette in TIERS.items():
        name = f'{tier}_chest_transporter'
        texture(tier, palette).save(ROOT / f'textures/item/{name}.png')
        write(ROOT / f'models/item/{name}_frame.json', frame_model(tier))
        # the item itself is drawn by TransporterRenderer
        write(ROOT / f'models/item/{name}.json', {
            'parent': 'minecraft:builtin/entity',
            'gui_light': 'side',
            'textures': {'particle': f'chest_transporter:item/{name}'},
            'display': DISPLAY,
        })
    print('done')


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
