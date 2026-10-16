import os
import speech_recognition as sr
from pydub import AudioSegment

directory = "/Users/muhakmal/Code/PembacaKUE/sound/wong-oslo-1"
files = [
    "Angka Dasar Satuan.mp3",
    "Angka Khusus & Satuan Nominal.mp3",
    "Kata Statis & Jenis Kartu.mp3",
    "Proses Baca Kartu & Informasi Error.mp3"
]

r = sr.Recognizer()

for f in files:
    print(f"=== Transcribing {f} ===")
    path = os.path.join(directory, f)
    wav_path = path.replace(".mp3", ".wav")
    
    audio = AudioSegment.from_mp3(path)
    audio.export(wav_path, format="wav")
    
    with sr.AudioFile(wav_path) as source:
        audio_data = r.record(source)
        try:
            text = r.recognize_google(audio_data, language="id-ID")
            print(f"Result: {text}")
        except Exception as e:
            print(f"Error: {e}")
    
    os.remove(wav_path)
    print("\n")
