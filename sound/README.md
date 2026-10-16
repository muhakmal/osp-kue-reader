# Panduan Pembuatan dan Pemotongan Suara (Voice Pack)

Direktori `sound/` ini digunakan untuk menyimpan rekaman suara mentah (raw voice) sebelum dipotong-potong dan diekspor ke dalam aplikasi (`app/src/main/res/raw/`).

Untuk memastikan penyebutan nominal (seperti belas, puluh, ratus, ribu) bekerja dengan sempurna sesuai logika `BalanceAnnouncer.kt`, **WAJIB** mengikuti panduan dan urutan rekaman berikut ini.

## 1. Persiapan Rekaman (Script)

Siapkan 4 file `.mp3` (bebas dari *noise*) untuk setiap karakter suara baru:
1. `Angka Dasar Satuan.mp3`
2. `Angka Khusus & Satuan Nominal.mp3`
3. `Kata Statis & Jenis Kartu.mp3`
4. `Proses Baca Kartu & Informasi Error.mp3`

### URUTAN PENGUCAPAN (Sangat Krusial!)
Saat merekam, pastikan *voice actor* memberi jeda yang jelas (sekitar 0.5 - 1 detik) antar frasa/kata, dan urutannya **HARUS PERSIS** seperti ini:

**A. Angka Dasar Satuan.mp3 (10 kata)**
1. Nol
2. Satu
3. Dua
4. Tiga
5. Empat
6. Lima
7. Enam
8. Tujuh
9. Delapan
10. Sembilan

**B. Angka Khusus & Satuan Nominal.mp3 (10 kata)**
1. Sepuluh
2. Sebelas
3. Belas
4. Puluh
5. Seratus
6. Ratus
7. Seribu
8. Ribu
9. Sejuta
10. Juta

*(Catatan: Jangan mengubah urutan ini karena skrip otomatisasi mengandalkan urutan untuk memberi label nama file yang benar)*

**C. Kata Statis & Jenis Kartu.mp3 (9 kata/frasa)**
1. Saldo
2. Sisa
3. Rupiah
4. E-Money Mandiri
5. Flazz BCA
6. Brizzi BRI
7. Tapcash BNI
8. Jakcard
9. KMT

**D. Proses Baca Kartu & Informasi Error.mp3 (6 frasa)**
1. Kartu terdeteksi
2. Mohon tunggu
3. Jangan angkat kartu Anda
4. Kartu tidak dikenal
5. Kartu tidak didukung
6. Gagal membaca kartu

---

## 2. Struktur Direktori Voice Pack

Buat folder baru untuk suara tersebut (contoh: `suara-baru-1`) di dalam direktori `sound/`.
Letakkan keempat file `.mp3` di atas ke dalamnya.
Copy file script Python dari folder lain (misalnya dari `wong-oslo-1`):
- `extract_timestamps.py`
- `update_metadata.py` (Pastikan label array di file ini sesuai dengan urutan di atas!)
- `export_to_res_raw.py`

## 3. Langkah-langkah Eksekusi (Pemotongan)

1. **Jalankan `extract_timestamps.py`**
   Script ini akan membaca jeda (silence) menggunakan *pydub* dan *ffmpeg*, kemudian menghasilkan file `metadata.json`. Jika ada kata yang menempel, Anda bisa menyesuaikan parameter `min_silence_len` atau mengedit `metadata.json` secara manual.

2. **Jalankan `update_metadata.py`**
   Script ini akan memberi label/nama urutan kata ke dalam `metadata.json`. Jika ada perbedaan jumlah segment yang terdeteksi, periksa `metadata.json` Anda.

3. **Jalankan `export_to_res_raw.py`**
   Script ini akan memotong file MP3 berdasarkan `metadata.json` dan mengekspornya ke dalam `app/src/main/res/raw/`. 
   **Penting:** Ubah variabel `PREFIX` di dalam script ini menjadi awalan suara Anda (contoh: `"suara_"`) sehingga akan menghasilkan file seperti `suara_satu.mp3`, `suara_saldo.mp3`, dst.

## 4. Mendaftarkan Suara ke Aplikasi

Setelah file berhasil diekspor, buka kode aplikasi Anda di Android Studio:
- Buka `app/src/main/java/com/baskom/pembacakue/VoicePack.kt`
- Tambahkan entry Enum baru dengan `prefix` yang cocok (misalnya `"suara"`).
- Suara Anda secara otomatis akan muncul di pengaturan (SettingsScreen) dan siap digunakan!
