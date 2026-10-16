import json
import os

path = "/Users/muhakmal/Code/PembacaKUE/sound/diva-indonesia/metadata.json"

with open(path, "r") as f:
    data = json.load(f)

transcripts = {
    "Angka Dasar Satuan.mp3": ["Nol", "Satu", "Dua", "Tiga", "Empat", "Lima", "Enam", "Tujuh", "Delapan", "Sembilan"],
    "Angka Khusus & Satuan Nominal.mp3": ["Sepuluh", "Sebelas", "Puluh", "Seratus", "Ratus", "Seribu", "Ribu", "Sejuta", "Juta"],
    "Kata Statis & Jenis Kartu.mp3": ["Saldo", "Sisa", "Rupiah", "E-Money Mandiri", "Flazz BCA", "Brizzi BRI", "Tapcash BNI", "Jakcard", "KMT"],
    "Proses Baca Kartu & Informasi Error.mp3": [
        "Kartu terdeteksi",
        "Mohon tunggu sedang membaca kartu",
        "Jangan angkat kartu Anda",
        "Kartu tidak dikenal",
        "Kartu tidak didukung",
        "Gagal membaca kartu silakan coba lagi"
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
                seg["label"] = f"{words[i]} (Estimated)"
            else:
                seg["label"] = f"segment_{i+1}"

with open(path, "w") as f:
    json.dump(data, f, indent=2)

print("Updated metadata.json")
