import subprocess
import re

path = "/Users/muhakmal/Code/PembacaKUE/sound/diva-indonesia/Angka Dasar Satuan.mp3"
for d in [0.1, 0.15, 0.2]:
    cmd = [
        "ffmpeg", "-i", path,
        "-af", f"silencedetect=noise=-30dB:d={d}",
        "-f", "null", "-"
    ]
    res = subprocess.run(cmd, stderr=subprocess.PIPE, text=True)
    starts = re.findall(r'silence_start: ([\d\.]+)', res.stderr)
    print(f"d={d}, found {len(starts)} silences -> {len(starts)+1} segments roughly")
