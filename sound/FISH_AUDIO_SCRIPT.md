# Naskah (Prompt) Text-to-Speech untuk AI (FishAudio / ElevenLabs)

Jika Anda menggunakan platform AI *Text-to-Speech* (TTS) seperti **FishAudio**, **ElevenLabs**, atau **Google Cloud TTS**, AI cenderung membaca daftar kata dengan sangat cepat seolah-olah itu adalah satu kalimat yang tersambung.

Agar *script* Python `process_voice.py` tetap bisa mendeteksi hening (silence) untuk memotong kata, kita harus "memaksa" AI untuk memberikan jeda minimal 1-2 detik antar kata. 

Berikut adalah trik penulisan *prompt* teksnya (pilih salah satu gaya yang paling cocok dengan AI yang Anda gunakan):

---

### OPSI 1: Format Tanda Baca Bertumpuk (Direkomendasikan untuk FishAudio & ElevenLabs)
Gunakan tanda titik dan spasi lebar (elipsis) agar AI mengambil nafas atau berhenti.

**File 1: Angka Dasar Satuan**
```text
Nol. ... ... Satu. ... ... Dua. ... ... Tiga. ... ... Empat. ... ... Lima. ... ... Enam. ... ... Tujuh. ... ... Delapan. ... ... Sembilan.
```

**File 2: Angka Khusus & Nominal**
```text
Sepuluh. ... ... Sebelas. ... ... Belas. ... ... Puluh. ... ... Seratus. ... ... Ratus. ... ... Seribu. ... ... Ribu. ... ... Sejuta. ... ... Juta.
```

**File 3: Kata Statis & Kartu**
```text
Saldo. ... ... Sisa. ... ... Rupiah. ... ... E-Money Mandiri. ... ... Flazz B C A. ... ... Brizzi B R I. ... ... Tapcash B N I. ... ... Jakcard. ... ... Ka Em Te.
```
*(Catatan: Untuk AI, singkatan seperti BCA atau KMT lebih baik dieja huruf demi huruf menjadi "B C A" atau "Ka Em Te" agar cara bacanya tepat).*

**File 4: Proses Baca & Error**
```text
Kartu terdeteksi. ... ... Mohon tunggu. ... ... Jangan angkat kartu Anda. ... ... Kartu tidak dikenal. ... ... Kartu tidak didukung. ... ... Gagal membaca kartu.
```

---

### OPSI 2: Format SSML (Jika menggunakan Google TTS / Azure)
Beberapa platform mendukung *Speech Synthesis Markup Language* (SSML). Anda tinggal melakukan *copy-paste* ini:

```xml
<!-- Contoh untuk Angka Dasar Satuan -->
<speak>
  Nol <break time="1.5s"/>
  Satu <break time="1.5s"/>
  Dua <break time="1.5s"/>
  Tiga <break time="1.5s"/>
  Empat <break time="1.5s"/>
  Lima <break time="1.5s"/>
  Enam <break time="1.5s"/>
  Tujuh <break time="1.5s"/>
  Delapan <break time="1.5s"/>
  Sembilan
</speak>
```

---

## Rekomendasi Platform AI Voice (TTS) Terbaik Saat Ini

1. **ElevenLabs (Sangat Direkomendasikan)**
   - **Kelebihan:** Suara berbahasa Indonesia (Multilingual v2) adalah yang paling realistis dan berekspresi di pasar saat ini. Sangat jernih dan bernafas layaknya manusia asli.
   - **Trik Jeda:** Cukup gunakan `-` atau `...` di antara kata.

2. **FishAudio (Fish Speech)**
   - **Kelebihan:** Sangat bagus untuk *Voice Cloning* (meniru suara orang tertentu hanya dengan sampel 10 detik). Lisensi *open-source* nya juga tersedia.
   - **Trik Jeda:** Gunakan tanda baca berlapis seperti opsi 1 di atas. Terkadang model ini mengabaikan titik, jadi pastikan di-_preview_ dulu.

3. **OpenAI TTS (Melalui API)**
   - **Kelebihan:** Sangat murah meriah dan suaranya stabil (ada *Alloy, Nova, Echo*).
   - **Trik Jeda:** Sulit diberi jeda panjang dalam satu teks. Cara terbaik jika menggunakan OpenAI adalah Anda memanggil API per kata, langsung menyimpannya menjadi file `budi_satu.mp3`, `budi_dua.mp3`, dst. (Melewati proses pemotongan script sepenuhnya).

4. **Google Cloud TTS (Neural2)**
   - **Kelebihan:** Sangat stabil, artikulasi untuk singkatan e-money lokal paling akurat.
   - **Trik Jeda:** Mendukung tag SSML `<break time="1s"/>` dengan sempurna.
