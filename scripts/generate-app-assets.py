#!/usr/bin/env python3
"""
scripts/generate-app-assets.py
Deterministic generator for TabbyRush icon assets across Desktop and Android.
Sources: docs/assets/tabbyrush-source.png, docs/assets/tabbyrush-vector.svg
"""

import os
import re
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SOURCE_PNG = "docs/assets/tabbyrush-source.png"
SOURCE_SVG = "docs/assets/tabbyrush-vector.svg"
BRAND_PRIMARY = "#004646"
WHITE = "#FFFFFF"

def build_desktop_composite(size, cropped, margin_512=18, border_color=BRAND_PRIMARY, border_w_512=3.5, inner_scale=0.92):
    scale = size / 512.0
    img_comp = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    margin = int(margin_512 * scale)
    r = int(512 * 0.22 * scale)
    
    if size >= 32:
        shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        s_draw = ImageDraw.Draw(shadow)
        s_draw.rounded_rectangle(
            [margin, margin + int(3 * scale), size - margin, size - margin + int(3 * scale)],
            radius=r, fill=(0, 0, 0, 40)
        )
        shadow = shadow.filter(ImageFilter.GaussianBlur(radius=max(1.0, 3 * scale)))
        img_comp.alpha_composite(shadow)
    
    draw = ImageDraw.Draw(img_comp)
    draw.rounded_rectangle(
        [margin, margin, size - 1 - margin, size - 1 - margin],
        radius=r, fill=WHITE, outline=border_color, width=max(1, int(border_w_512 * scale))
    )
    inner_w = size - 2 * margin
    mw = int(inner_w * inner_scale)
    mh = int(cropped.size[1] * mw / cropped.size[0])
    scaled = cropped.resize((mw, mh), Image.Resampling.LANCZOS)
    img_comp.alpha_composite(scaled, ((size - mw) // 2, (size - mh) // 2))
    return img_comp

def build_round_launcher(d_size, cropped, border_color=BRAND_PRIMARY):
    img = Image.new("RGBA", (d_size, d_size), (0, 0, 0, 0))
    margin = max(1, int(d_size * 0.03))
    inner_d = d_size - 2 * margin
    border_w = max(1, int(d_size * 0.025))
    
    draw = ImageDraw.Draw(img)
    draw.ellipse(
        [margin, margin, d_size - 1 - margin, d_size - 1 - margin],
        fill=WHITE, outline=border_color, width=border_w
    )
    mw = int(inner_d * 0.86)
    mh = int(cropped.size[1] * mw / cropped.size[0])
    scaled = cropped.resize((mw, mh), Image.Resampling.LANCZOS)
    img.alpha_composite(scaled, ((d_size - mw) // 2, (d_size - mh) // 2))
    return img

def generate_all():
    assert os.path.exists(SOURCE_PNG), f"Missing source PNG at {SOURCE_PNG}"
    assert os.path.exists(SOURCE_SVG), f"Missing source SVG at {SOURCE_SVG}"
    
    img = Image.open(SOURCE_PNG).convert("RGBA")
    arr = np.array(img)
    y_idx, x_idx = np.where(arr[:, :, 3] > 0)
    cropped = img.crop((x_idx.min(), y_idx.min(), x_idx.max() + 1, y_idx.max() + 1))
    cw, ch = cropped.size
    
    # 1. Desktop runtime 256x256 PNG
    img_256 = build_desktop_composite(256, cropped)
    os.makedirs("desktopApp/src/main/resources", exist_ok=True)
    img_256.save("desktopApp/src/main/resources/tabbyrush-icon.png", format="PNG")
    
    # 2. Windows multi-size packaging ICO (16, 24, 32, 48, 64, 128, 256)
    img_512 = build_desktop_composite(512, cropped)
    ico_sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    os.makedirs("desktopApp/src/windowsMain/resources", exist_ok=True)
    # 3. System Tray 32x32 Icons (Linux & Windows)
    tray_w, tray_h = 30, round(ch * 30 / cw)
    tray_scaled = cropped.resize((tray_w, tray_h), Image.Resampling.LANCZOS)
    tray_32 = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    tray_32.alpha_composite(tray_scaled, ((32 - tray_w) // 2, (32 - tray_h) // 2))
    os.makedirs("desktopApp/src/linuxMain/resources", exist_ok=True)
    tray_32.save("desktopApp/src/linuxMain/resources/task-surface-tray.png", format="PNG")
    tray_32.save("desktopApp/src/windowsMain/resources/task-surface-tray.png", format="PNG")
    
    # 4. Master SVG with Vector Paths
    with open(SOURCE_SVG, "r", encoding="utf-8") as f:
        path_matches = re.findall(r'<path[^>]*/>', f.read())
    assert len(path_matches) > 0, "No path elements found in vector source SVG"
    
    squircle_margin = 18
    inner_w, r, border_w = 512 - 2 * squircle_margin, int(512 * 0.22), 3.5
    orig_bw, orig_bh = 1266.4, 882.75
    mw = inner_w * 0.92
    scale_factor = mw / orig_bw
    cx_orig, cy_orig = (66.6 + 1333.0) / 2.0, (162.33 + 1045.08) / 2.0
    tx, ty = (256.0) - cx_orig * scale_factor, (256.0) - cy_orig * scale_factor
    paths_str = "\n    ".join(path_matches)
    svg_content = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="100%" height="100%">
  <defs>
    <filter id="squircle-shadow" x="-10%" y="-10%" width="125%" height="125%">
      <feDropShadow dx="0" dy="6" stdDeviation="10" flood-color="#000000" flood-opacity="0.25"/>
    </filter>
  </defs>
  <g id="background-desktop">
    <rect x="{squircle_margin}" y="{squircle_margin}" width="{inner_w}" height="{inner_w}" rx="{r}" ry="{r}" fill="{WHITE}" filter="url(#squircle-shadow)"/>
    <rect x="{squircle_margin}" y="{squircle_margin}" width="{inner_w}" height="{inner_w}" rx="{r}" ry="{r}" fill="none" stroke="{BRAND_PRIMARY}" stroke-width="{border_w}"/>
  </g>
  <g id="mascot-tabbyrush" transform="translate({tx:.3f}, {ty:.3f}) scale({scale_factor:.6f})">
    {paths_str}
  </g>
</svg>
"""
    with open("docs/assets/tabbyrush-app-icon.svg", "w", encoding="utf-8") as f:
        f.write(svg_content)
    
    # 5. Android Adaptive Foreground PNG (432x432, strictly within 66dp safe circle)
    target_w, target_h = 238, int(ch * 238 / cw)
    android_scaled = cropped.resize((target_w, target_h), Image.Resampling.LANCZOS)
    fg_img = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    fg_img.alpha_composite(android_scaled, ((432 - target_w) // 2, (432 - target_h) // 2 + 1))
    
    arr_fg = np.array(fg_img)
    y_pts, x_pts = np.where(arr_fg[:, :, 3] > 0)
    dists = np.sqrt((x_pts - 216.0)**2 + (y_pts - 216.0)**2)
    max_dist = dists.max()
    outside_66dp = np.sum(dists > 132.0)
    safety_margin = 132.0 - max_dist
    assert outside_66dp == 0, f"Foreground clipped: {outside_66dp} pixels outside 66dp radius!"
    os.makedirs("androidApp/src/main/res/drawable", exist_ok=True)
    fg_img.save("androidApp/src/main/res/drawable/ic_launcher_foreground.png", format="PNG")
    print(f"Adaptive FG verified: max_dist={max_dist:.2f}px ({max_dist/4:.2f}dp), margin={safety_margin:.2f}px ({safety_margin/4:.2f}dp)")
    
    # 6. Android Legacy & Fallback Mipmaps
    densities = {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96, "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}
    for folder, d_size in densities.items():
        d_dir = os.path.join("androidApp/src/main/res", folder)
        os.makedirs(d_dir, exist_ok=True)
        build_desktop_composite(d_size, cropped).save(os.path.join(d_dir, "ic_launcher.png"), format="PNG")
        build_round_launcher(d_size, cropped).save(os.path.join(d_dir, "ic_launcher_round.png"), format="PNG")
    print("All TabbyRush assets generated successfully!")

if __name__ == "__main__":
    generate_all()
