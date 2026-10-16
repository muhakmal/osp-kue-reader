import subprocess
import json
import os
import re

directory = "/Users/muhakmal/Code/PembacaKUE/sound/wong-oslo-1"
files = [
    "Angka Dasar Satuan.mp3",
    "Angka Khusus & Satuan Nominal.mp3",
    "Kata Statis & Jenis Kartu.mp3",
    "Proses Baca Kartu & Informasi Error.mp3"
]

output_data = {}

for f in files:
    path = os.path.join(directory, f)
    if not os.path.exists(path):
        continue
    # Get duration using ffprobe
    duration_cmd = [
        "ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", path
    ]
    duration_out = subprocess.check_output(duration_cmd)
    duration_json = json.loads(duration_out)
    duration = float(duration_json["format"]["duration"])

    # Detect silences using ffmpeg
    if "Statis" in f or "Proses" in f:
        af_param = "silencedetect=noise=-30dB:d=0.3"
    else:
        af_param = "silencedetect=noise=-30dB:d=0.2"
        
    cmd = [
        "ffmpeg",
        "-i", path,
        "-af", af_param,
        "-f", "null",
        "-"
    ]
    result = subprocess.run(cmd, stderr=subprocess.PIPE, text=True)
    stderr = result.stderr

    silence_starts = []
    silence_ends = []

    for line in stderr.split('\n'):
        if "silence_start:" in line:
            match = re.search(r'silence_start: ([\d\.]+)', line)
            if match:
                silence_starts.append(float(match.group(1)))
        elif "silence_end:" in line:
            match = re.search(r'silence_end: ([\d\.]+)', line)
            if match:
                silence_ends.append(float(match.group(1)))

    segments = []
    current_start = 0.0

    # Match starts and ends. 
    # Usually it's [start, end, start, end] for silences.
    for i in range(len(silence_starts)):
        end = silence_starts[i]
        if end > current_start + 0.1: # if segment is longer than 100ms
            segments.append({
                "label": f"segment_{len(segments)+1}",
                "start": round(current_start, 3),
                "end": round(end, 3),
                "duration": round(end - current_start, 3)
            })
        if i < len(silence_ends):
            current_start = silence_ends[i]
    
    # Check the last segment
    if current_start < duration - 0.1:
        segments.append({
            "label": f"segment_{len(segments)+1}",
            "start": round(current_start, 3),
            "end": round(duration, 3),
            "duration": round(duration - current_start, 3)
        })

    output_data[f] = {
        "file": f,
        "duration": duration,
        "segments": segments
    }

metadata_path = os.path.join(directory, "metadata.json")
with open(metadata_path, "w") as f:
    json.dump(output_data, f, indent=2)
print("Saved to", metadata_path)
