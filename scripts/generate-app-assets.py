#!/usr/bin/env python3
"""
scripts/generate-app-assets.py

Deterministic generator for all TabbyRush application icon assets across platforms:
- Desktop runtime 256x256 PNG: desktopApp/src/main/resources/tabbyrush-icon.png
- Desktop packaging ICO (16, 24, 32, 48, 64, 128, 256): desktopApp/src/windowsMain/resources/tabbyrush.ico
- Desktop system tray 32x32 PNG (Linux & Windows): desktopApp/src/*/resources/task-surface-tray.png
- Master SVG: docs/assets/tabbyrush-app-icon.svg
- Android Adaptive Foreground PNG (432x432, strictly within 66dp safe circle):
  androidApp/src/main/res/drawable/ic_launcher_foreground.png

Source: docs/assets/tabbyrush-source.png (high-resolution illustration of TabbyRush cat mascot with task checkpoint trail and finish flag).
"""

import os
import collections
import base64
from io import BytesIO
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SOURCE_PATH = "docs/assets/tabbyrush-source.png"

def generate_all():
    assert os.path.exists(SOURCE_PATH), f"Missing source illustration at {SOURCE_PATH}"
    img = Image.open(SOURCE_PATH).convert("RGBA")
    w, h = img.size
    arr = np.array(img)

    # 1. Extract illustration via exterior flood-fill BFS
    # Preserves all internal white/cream pixels (chest, belly, checkmarks, flag highlights)
    bg_mask = np.all(arr[:, :, :3] >= 245, axis=-1)
    visited = np.zeros((h, w), dtype=bool)
    q = collections.deque()

    for x in range(w):
        if bg_mask[0, x]:
            visited[0, x] = True
            q.append((0, x))
        if bg_mask[h-1, x]:
            visited[h-1, x] = True
            q.append((h-1, x))
    for y in range(h):
        if bg_mask[y, 0]:
            visited[y, 0] = True
            q.append((y, 0))
        if bg_mask[y, w-1]:
            visited[y, w-1] = True
            q.append((y, w-1))

    while q:
        cy, cx = q.popleft()
        for dy, dx in ((-1,0), (1,0), (0,-1), (0,1)):
            ny, nx = cy + dy, cx + dx
            if 0 <= ny < h and 0 <= nx < w:
                if not visited[ny, nx] and bg_mask[ny, nx]:
                    visited[ny, nx] = True
                    q.append((ny, nx))

    # Anti-alias boundary alpha
    alpha_img = Image.fromarray(((~visited).astype(np.uint8) * 255), mode="L")
    alpha_smooth = alpha_img.filter(ImageFilter.GaussianBlur(radius=0.8))

    cutout = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    cutout.paste(img, (0, 0), alpha_smooth)

    # Crop to content bounding box
    y_idx, x_idx = np.where(np.array(alpha_smooth) > 10)
    crop_box = (x_idx.min(), y_idx.min(), x_idx.max() + 1, y_idx.max() + 1)
    cutout_cropped = cutout.crop(crop_box)
    cw, ch = cutout_cropped.size

    # 2. Desktop 512x512 Master Composite & 256x256 Runtime PNG
    def build_desktop_composite(size):
        scale = size / 512.0
        img_comp = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        r = int(116 * scale)

        # Soft drop shadow behind rounded container
        shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        s_draw = ImageDraw.Draw(shadow)
        s_draw.rounded_rectangle(
            [int(16*scale), int(20*scale), int(496*scale), int(500*scale)],
            radius=r,
            fill=(0, 0, 0, 40)
        )
        shadow = shadow.filter(ImageFilter.GaussianBlur(radius=4*scale))
        img_comp.alpha_composite(shadow)

        # White squircle with subtle border
        draw = ImageDraw.Draw(img_comp)
        draw.rounded_rectangle(
            [int(16*scale), int(16*scale), int(496*scale), int(496*scale)],
            radius=r,
            fill="#FFFFFF",
            outline="#D9E3E4",
            width=max(1, int(2*scale))
        )

        tw = int(430 * scale)
        th = int(ch * tw / cw)
        scaled = cutout_cropped.resize((tw, th), Image.Resampling.LANCZOS)
        px = (size - tw) // 2
        py = (size - th) // 2 - int(4 * scale)
        img_comp.alpha_composite(scaled, (px, py))
        return img_comp

    # Save desktop runtime icon
    img_256 = build_desktop_composite(256)
    os.makedirs("desktopApp/src/main/resources", exist_ok=True)
    img_256.save("desktopApp/src/main/resources/tabbyrush-icon.png", format="PNG")

    # Save Windows multi-size ICO
    img_512 = build_desktop_composite(512)
    ico_sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    os.makedirs("desktopApp/src/windowsMain/resources", exist_ok=True)
    img_512.save("desktopApp/src/windowsMain/resources/tabbyrush.ico", format="ICO", sizes=ico_sizes)

    # 3. System Tray 32x32 Icons (Linux & Windows)
    cat_crop = cutout.crop((200, 350, 1850, 1850))
    cat_w, cat_h = cat_crop.size
    tray_scale = 26.0 / max(cat_w, cat_h)
    tray_w, tray_h = int(cat_w * tray_scale), int(cat_h * tray_scale)
    cat_tray = cat_crop.resize((tray_w, tray_h), Image.Resampling.LANCZOS)

    tray32 = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    t_x = (32 - tray_w) // 2
    t_y = (32 - tray_h) // 2
    tray32.alpha_composite(cat_tray, (t_x, t_y))

    os.makedirs("desktopApp/src/linuxMain/resources", exist_ok=True)
    tray32.save("desktopApp/src/linuxMain/resources/task-surface-tray.png", format="PNG")
    tray32.save("desktopApp/src/windowsMain/resources/task-surface-tray.png", format="PNG")

    # 4. Master SVG with Embedded High-Res Lossless PNG
    cat_for_svg = cutout_cropped.resize((430, int(ch * 430 / cw)), Image.Resampling.LANCZOS)
    buf = BytesIO()
    cat_for_svg.save(buf, format="PNG")
    cat_b64 = base64.b64encode(buf.getvalue()).decode('ascii')

    svg_content = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="100%" height="100%">
  <defs>
    <filter id="squircle-shadow" x="-10%" y="-10%" width="125%" height="125%">
      <feDropShadow dx="0" dy="8" stdDeviation="12" flood-color="#000000" flood-opacity="0.2"/>
    </filter>
  </defs>
  <!-- Background Squircle Layer -->
  <g id="background-desktop">
    <rect x="16" y="16" width="480" height="480" rx="116" ry="116" fill="#FFFFFF" filter="url(#squircle-shadow)"/>
    <rect x="16" y="16" width="480" height="480" rx="116" ry="116" fill="none" stroke="#D9E3E4" stroke-width="2"/>
  </g>
  <!-- TabbyRush Mascot Layer -->
  <g id="mascot-tabbyrush">
    <image href="data:image/png;base64,{cat_b64}" x="41" y="70" width="430" height="{int(ch * 430 / cw)}" />
  </g>
</svg>"""
    os.makedirs("docs/assets", exist_ok=True)
    with open("docs/assets/tabbyrush-app-icon.svg", "w", encoding="utf-8") as f:
        f.write(svg_content.strip() + "\n")

    # 5. Android Adaptive Foreground PNG (432x432, verified strictly within 66dp safe circle)
    target_w = 200
    target_h = int(ch * target_w / cw)
    android_scaled = cutout_cropped.resize((target_w, target_h), Image.Resampling.LANCZOS)

    fg_img = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    pos_x = (432 - target_w) // 2 - 5
    pos_y = (432 - target_h) // 2 - 5
    fg_img.paste(android_scaled, (pos_x, pos_y), android_scaled)

    # Assert 100% within 66dp safe zone (radius 132 px from (216, 216))
    arr_fg = np.array(fg_img)
    y_pts, x_pts = np.where(arr_fg[:, :, 3] > 0)
    dists = np.sqrt((x_pts - 216.0)**2 + (y_pts - 216.0)**2)
    max_dist = dists.max()
    outside_66dp = np.sum(dists > 132.0)
    assert outside_66dp == 0, f"Foreground clipped: {outside_66dp} pixels outside 66dp radius! Max dist: {max_dist}"

    os.makedirs("androidApp/src/main/res/drawable", exist_ok=True)
    fg_img.save("androidApp/src/main/res/drawable/ic_launcher_foreground.png", format="PNG")
    print("All TabbyRush assets generated successfully!")

if __name__ == "__main__":
    generate_all()
