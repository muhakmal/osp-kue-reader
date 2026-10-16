#!/usr/bin/env python3
"""
process_voice.py
Script terpadu untuk memproses (ekstrak, mapping label, potong, ekspor) file audio Voice Pack.

Penggunaan:
    python3 process_voice.py <nama_folder> <prefix>

Contoh:
    python3 process_voice.py my-voice myvoice
"""

import sys
import os
import json
import subprocess
try:
    from pydub import AudioSegment, silence
except ImportError:
    print("Error: Module pydub belum terinstal. Jalankan: pip install pydub")
    sys.exit(1)

# Daftar label berurutan sesuai VOICE_ACTOR_SCRIPT.md
LABELS_MAP = {
    "Angka Dasar Satuan.mp3": [
        "nol", "satu", "dua", "tiga", "empat", "lima", "enam", "tujuh", "delapan", "sembilan"
    ],
    "Angka Khusus & Satuan Nominal.mp3": [
        "sepuluh", "sebelas", "belas", "puluh", "seratus", "ratus", "seribu", "ribu", "sejuta", "juta"
    ],
    "Kata Statis & Jenis Kartu.mp3": [
        "saldo", "sisa", "rupiah", "emoney_mandiri", "flazz_bca", "brizzi_bri", "tapcash_bni", "jakcard", "kmt"
    ],
    "Proses Baca Kartu & Informasi Error.mp3": [
        "kartu_terdeteksi", "mohon_tunggu", "jangan_angkat", "kartu_tidak_dikenal", "kartu_tidak_didukung", "gagal_membaca"
    ]
}

def extract_and_export(folder_path, prefix, output_dir):
    os.makedirs(output_dir, exist_ok=True)
    total_ok = 0
    total_fail = 0

    for filename, labels in LABELS_MAP.items():
        src_path = os.path.join(folder_path, filename)
        if not os.path.exists(src_path):
            print(f"[SKIP] File tidak ditemukan: {src_path}")
            continue

        print(f"\n=== Memproses {filename} ===")
        audio = AudioSegment.from_mp3(src_path)
        
        # Ekstrak timestamp berdasarkan silence
        # Threshold -30 dBFS, min silence 150ms-200ms
        min_silence = 200
        if "Proses Baca Kartu" in filename:
            min_silence = 300
        
        chunks = silence.split_on_silence(
            audio,
            min_silence_len=min_silence,
            silence_thresh=-35,
            keep_silence=False
        )

        # Cari posisi timestamp tanpa keep_silence agar dapat waktu akurat
        non_silent_ranges = silence.detect_nonsilent(audio, min_silence_len=min_silence, silence_thresh=-35)

        if len(non_silent_ranges) < len(labels):
            print(f"[WARNING] Hanya {len(non_silent_ranges)} segmen terdeteksi, tetapi {len(labels)} diharapkan.")
            print("Anda mungkin perlu menyesuaikan tingkat threshold atau memisahkan kata dengan lebih jelas.")
        
        for i, (start_ms, end_ms) in enumerate(non_silent_ranges):
            if i >= len(labels):
                break # Abaikan segmen ekstra jika ada suara bocor di akhir
            
            label = labels[i]
            out_name = f"{prefix}_{label}.mp3"
            out_path = os.path.join(output_dir, out_name)
            
            start_s = start_ms / 1000.0
            end_s = end_ms / 1000.0
            
            # Tambahkan natural padding (20ms awal, 80ms akhir)
            safe_start = max(0.0, start_s - 0.02)
            safe_end = end_s + 0.08
            duration = safe_end - safe_start
            
            cmd = [
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-i", src_path,
                "-ss", str(safe_start),
                "-t", str(duration),
                "-ar", "22050",
                "-ac", "1",
                "-b:a", "96k",
                out_path
            ]
            result = subprocess.run(cmd)
            if result.returncode == 0:
                print(f"  [OK] {label} → {out_name}")
                total_ok += 1
            else:
                print(f"  [FAIL] {label}")
                total_fail += 1

    print(f"\nSelesai: {total_ok} berhasil diekspor ke {output_dir}")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Penggunaan: python3 process_voice.py <nama_folder> <prefix>")
        sys.exit(1)
        
    folder_name = sys.argv[1]
    prefix = sys.argv[2]
    
    base_dir = os.path.dirname(os.path.abspath(__file__))
    folder_path = os.path.join(base_dir, folder_name)
    output_dir = os.path.join(base_dir, "../app/src/main/res/raw")
    
    extract_and_export(folder_path, prefix, output_dir)
