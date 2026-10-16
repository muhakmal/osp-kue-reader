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
angka_khusus["words_list"] = labels_khusus

with open(path, "w") as f:
    json.dump(data, f, indent=2)

print("diva metadata.json fixed!")
