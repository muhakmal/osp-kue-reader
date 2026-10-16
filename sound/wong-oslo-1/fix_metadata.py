import json

path = "metadata.json"
with open(path, "r") as f:
    data = json.load(f)

# 1. Fix Angka Khusus
angka_khusus = data["Angka Khusus & Satuan Nominal.mp3"]
labels_khusus = ["sepuluh", "sebelas", "belas", "puluh", "seratus", "ratus", "seribu", "ribu", "sejuta", "juta"]
for i, seg in enumerate(angka_khusus["segments"]):
    if i < len(labels_khusus):
        seg["label"] = labels_khusus[i]

# 2. Fix Kata Statis
kata_statis = data["Kata Statis & Jenis Kartu.mp3"]
labels_statis = ["saldo", "sisa", "rupiah", "emoney_mandiri", "flazz_bca", "brizzi_bri", "tapcash_bni", "jakcard", "kmt", "kartu_multi_trip"]
for i, seg in enumerate(kata_statis["segments"]):
    if i < len(labels_statis):
        seg["label"] = labels_statis[i]

# 3. Fix Proses Baca Kartu
proses = data["Proses Baca Kartu & Informasi Error.mp3"]
segs = proses["segments"]

new_proses_segs = [
    {
        "label": "kartu_terdeteksi",
        "start": segs[0]["start"],
        "end": segs[1]["end"],
        "duration": segs[1]["end"] - segs[0]["start"]
    },
    {
        "label": "mohon_tunggu",
        "start": segs[2]["start"],
        "end": segs[3]["end"],
        "duration": segs[3]["end"] - segs[2]["start"]
    },
    {
        "label": "jangan_angkat",
        "start": segs[4]["start"],
        "end": segs[4]["end"],
        "duration": segs[4]["duration"]
    },
    {
        "label": "kartu_tidak_dikenal",
        "start": segs[9]["start"],
        "end": segs[9]["end"],
        "duration": segs[9]["duration"]
    },
    {
        "label": "kartu_tidak_didukung",
        "start": segs[10]["start"],
        "end": segs[10]["end"],
        "duration": segs[10]["duration"]
    },
    {
        "label": "gagal_membaca",
        "start": segs[11]["start"],
        "end": segs[12]["end"],
        "duration": segs[12]["end"] - segs[11]["start"]
    }
]
proses["segments"] = new_proses_segs

with open(path, "w") as f:
    json.dump(data, f, indent=2)

print("metadata.json fixed!")
