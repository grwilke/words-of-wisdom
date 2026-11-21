#!/usr/bin/env python3
"""Packages generated artwork into Play Store assets and Android TV/adaptive drawables."""

from __future__ import annotations

import pathlib
from PIL import Image, ImageOps

ROOT = pathlib.Path(__file__).resolve().parent.parent


def main() -> int:
    store_dir = ROOT / "store"
    icon_src = store_dir / "icon_raw.jpg"
    banner_src = store_dir / "banner_raw.jpg"
    store_dir.mkdir(parents=True, exist_ok=True)

    icon = Image.open(icon_src).convert("RGBA")
    banner = Image.open(banner_src).convert("RGBA")

    # Play Store required dimensions
    icon.resize((512, 512), Image.Resampling.LANCZOS).save(store_dir / "play-icon-512.png")
    ImageOps.fit(banner, (1280, 720), Image.Resampling.LANCZOS).save(store_dir / "play-tv-banner-1280x720.png")
    ImageOps.fit(banner, (1024, 500), Image.Resampling.LANCZOS).save(store_dir / "play-feature-1024x500.png")

    # Android TV launcher banner (320x180 xhdpi -> 640x360 crisp)
    res = ROOT / "app" / "src" / "main" / "res"
    (res / "drawable-xhdpi").mkdir(parents=True, exist_ok=True)
    ImageOps.fit(banner, (640, 360), Image.Resampling.LANCZOS).save(res / "drawable-xhdpi" / "tv_banner.png")

    # Android app icons (all mipmap densities + adaptive foreground)
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        d = res / folder
        d.mkdir(parents=True, exist_ok=True)
        icon.resize((size, size), Image.Resampling.LANCZOS).save(d / "ic_launcher.png")

    # Also save crisp mascot artwork for Compose Lobby/Splash UI
    (res / "drawable-nodpi").mkdir(parents=True, exist_ok=True)
    icon.resize((384, 384), Image.Resampling.LANCZOS).save(res / "drawable-nodpi" / "owl_mascot.png")
    print("Generated all Play Store & Android TV drawable assets successfully!")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
