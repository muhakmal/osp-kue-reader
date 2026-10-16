"""
export_to_res_raw.py
One-time script: potong segmen audio dari metadata.json dan ekspor ke res/raw/
Jalankan dari direktori sound/diva-indonesia/:
    python3 export_to_res_raw.py
"""

import json
import os
import subprocess

BASE_DIR    = os.path.dirname(os.path.abspath(__file__))
METADATA    = os.path.join(BASE_DIR, "metadata.json")
OUTPUT_DIR  = os.path.join(BASE_DIR, "../../app/src/main/res/raw")

def sanitize(label: str) -> str:
    """Ubah label menjadi nama file Android-safe (lowercase, underscore)."""
    return label.lower().replace(" ", "_").replace("-", "_").replace("&", "and")

def export_segment(src_path: str, start: float, end: float, out_path: str):
    """Gunakan ffmpeg untuk memotong segmen dan ekspor ke MP3.

    PENTING: Gunakan output seeking (-ss setelah -i) agar akurat untuk MP3.
    Input seeking (-ss sebelum -i) tidak frame-accurate untuk MP3 dan
    dapat menyebabkan bleed-in dari segmen sebelumnya.
    """
    safe_start = start
    duration   = end - start

    cmd = [
        "ffmpeg", "-y",
        "-i",  src_path,          # input dulu
        "-ss", str(safe_start),   # output seek (akurat untuk MP3)
        "-t",  str(duration),
        "-ar", "22050",           # sample rate 22 kHz
        "-ac", "1",               # mono
        "-b:a", "96k",            # bitrate lebih tinggi untuk clarity
        out_path
    ]
    result = subprocess.run(cmd, stderr=subprocess.PIPE, text=True)
    if result.returncode != 0:
        print(f"  [ERROR] ffmpeg: {result.stderr[-200:]}")
        return False
    return True

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    with open(METADATA, "r", encoding="utf-8") as f:
        metadata = json.load(f)

    total_ok   = 0
    total_fail = 0

    for filename, info in metadata.items():
        src_path = os.path.join(BASE_DIR, filename)
        if not os.path.exists(src_path):
            print(f"[SKIP] File not found: {src_path}")
            continue

        print(f"\n=== {filename} ===")
        for seg in info["segments"]:
            label    = sanitize(seg["label"])
            out_name = f"diva_{label}.mp3"
            out_path = os.path.join(OUTPUT_DIR, out_name)

            ok = export_segment(src_path, seg["start"], seg["end"], out_path)
            status = "OK  " if ok else "FAIL"
            print(f"  [{status}] {seg['label']} ({seg['start']}s–{seg['end']}s) → {out_name}")

            if ok:
                total_ok += 1
            else:
                total_fail += 1

    print(f"\n{'='*40}")
    print(f"Selesai: {total_ok} berhasil, {total_fail} gagal")
    print(f"Output: {OUTPUT_DIR}")

if __name__ == "__main__":
    main()
