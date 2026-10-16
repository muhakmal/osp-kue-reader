import json
import os

path = "/Users/muhakmal/Code/PembacaKUE/sound/wong-oslo-1/metadata.json"

with open(path, "r") as f:
    data = json.load(f)

transcripts = {
    "Angka Dasar Satuan.mp3": ["Nol", "Satu", "Dua", "Tiga", "Empat", "Lima", "Enam", "Tujuh", "Delapan", "Sembilan"],
    "Angka Khusus & Satuan Nominal.mp3": ["Sepuluh", "Sebelas", "Puluh", "Seratus", "Ratus", "Seribu", "Ribu", "Sejuta", "Juta", "Belas"],
    "Kata Statis & Jenis Kartu.mp3": ["saldo", "sisa", "rupiah", "emoney_mandiri", "flazz_bca", "brizzi_bri", "tapcash_bni", "jakcard", "kmt"],
    "Proses Baca Kartu & Informasi Error.mp3": [
        "kartu_terdeteksi",
        "mohon_tunggu",
        "jangan_angkat",
        "kartu_tidak_dikenal",
        "kartu_tidak_didukung",
        "gagal_membaca"
    ]
}

for filename, info in data.items():
    words = transcripts.get(filename, [])
    info["words_list"] = words
    
    segments = info.get("segments", [])
    
    # If perfect match, replace labels
    if len(segments) == len(words):
        for i, seg in enumerate(segments):
            seg["label"] = words[i]
    else:
        # Just leave as segment_x or guess based on order
        for i, seg in enumerate(segments):
            if i < len(words):
                # Try a best-effort mapping for labels
                seg["label"] = words[i]
            else:
                seg["label"] = f"segment_{i+1}"

with open(path, "w") as f:
    json.dump(data, f, indent=2)

print("Updated metadata.json")
