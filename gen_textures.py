#!/usr/bin/env python3
"""Generates the Multi-Sided Blocks textures (pure Python, no PIL needed)."""
import struct
import zlib

def make_png(width, height, pixels):
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = b""
    for y in range(height):
        raw += b"\x00"  # filter type 0
        for x in range(width):
            r, g, b, a = pixels[y][x]
            raw += bytes((r, g, b, a))
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))

def solid(color):
    return color

def swatch_grid(size=16):
    """16x16: dark border, light-gray background, 6 color swatches (2x3)."""
    border = (58, 58, 62, 255)
    background = (216, 216, 216, 255)
    swatches = [
        (229, 57, 53, 255),    # red
        (251, 140, 0, 255),    # orange
        (253, 216, 53, 255),   # yellow
        (67, 160, 71, 255),    # green
        (30, 136, 229, 255),   # blue
        (142, 36, 170, 255),   # purple
    ]
    px = [[background for _ in range(size)] for _ in range(size)]
    for x in range(size):
        for y in range(size):
            if x < 1 or y < 1 or x >= size - 1 or y >= size - 1:
                px[y][x] = border
    # 3 columns x 4px, 1px gaps; 2 rows x 4px, 1px gap
    col_x = [1, 6, 11]
    row_y = [3, 8]
    for c, color in enumerate(swatches):
        x0 = col_x[c % 3]
        y0 = row_y[c // 3]
        for dx in range(4):
            for dy in range(4):
                px[y0 + dy][x0 + dx] = color
    return px

def icon_128():
    """128x128: dark background, swatch grid scaled up with a thick border."""
    size = 128
    bg = (30, 33, 38, 255)
    border = (58, 58, 62, 255)
    px = [[bg for _ in range(size)] for _ in range(size)]
    # outer 4px border
    for x in range(size):
        for y in range(size):
            if x < 4 or y < 4 or x >= size - 4 or y >= size - 4:
                px[y][x] = border
    # swatch area: 6 swatches, each 34x34 with 5px gaps, centered
    sw = 34
    gap = 5
    area_w = 3 * sw + 2 * gap
    area_h = 2 * sw + gap
    x0 = (size - area_w) // 2
    y0 = (size - area_h) // 2
    swatches = [
        (229, 57, 53, 255), (251, 140, 0, 255), (253, 216, 53, 255),
        (67, 160, 71, 255), (30, 136, 229, 255), (142, 36, 170, 255),
    ]
    for c, color in enumerate(swatches):
        cx = x0 + (c % 3) * (sw + gap)
        cy = y0 + (c // 3) * (sw + gap)
        for dx in range(sw):
            for dy in range(sw):
                px[cy + dy][cx + dx] = color
    return px

base = swatch_grid(16)
icon = icon_128()

with open("src/main/resources/assets/multi-sided-blocks/textures/block/multi_sided_base.png", "wb") as f:
    f.write(make_png(16, 16, base))
with open("src/main/resources/assets/multi-sided-blocks/icon.png", "wb") as f:
    f.write(make_png(128, 128, icon))

print("textures written")
