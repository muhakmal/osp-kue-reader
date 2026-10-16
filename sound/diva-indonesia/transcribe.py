import os
import speech_recognition as sr
from pydub import AudioSegment

directory = "/Users/muhakmal/Code/PembacaKUE/sound/diva-indonesia"
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
    
    # Convert to wav
    audio = AudioSegment.from_mp3(path)
    audio.export(wav_path, format="wav")
    
    with sr.AudioFile(wav_path) as source:
        audio_data = r.record(source)
        try:
            # recognize using google API (language Indonesian)
            text = r.recognize_google(audio_data, language="id-ID")
            print(f"Result: {text}")
        except sr.UnknownValueError:
            print("Google Speech Recognition could not understand audio")
        except sr.RequestError as e:
            print(f"Could not request results; {e}")
    
    # clean up wav file
    os.remove(wav_path)
    print("\n")
