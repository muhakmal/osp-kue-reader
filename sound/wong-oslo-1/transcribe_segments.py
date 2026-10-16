import os
import json
import speech_recognition as sr
from pydub import AudioSegment

directory = "/Users/muhakmal/Code/PembacaKUE/sound/wong-oslo-1"
with open(os.path.join(directory, "metadata.json")) as f:
    metadata = json.load(f)

r = sr.Recognizer()

for f, info in metadata.items():
    print(f"\n=== Transcribing {f} ===")
    path = os.path.join(directory, f)
    audio = AudioSegment.from_mp3(path)
    
    for i, seg in enumerate(info["segments"]):
        start_ms = seg["start"] * 1000
        end_ms = seg["end"] * 1000
        chunk = audio[start_ms:end_ms]
        
        # Pad with 1 second of silence to help Google SR recognize short words
        silence = AudioSegment.silent(duration=1000)
        padded_chunk = silence + chunk + silence
        
        wav_path = os.path.join(directory, f"temp_{i}.wav")
        padded_chunk.export(wav_path, format="wav")
        
        with sr.AudioFile(wav_path) as source:
            audio_data = r.record(source)
            try:
                text = r.recognize_google(audio_data, language="id-ID")
                print(f"Segment {i+1} ({seg['duration']}s): {text}")
            except sr.UnknownValueError:
                print(f"Segment {i+1} ({seg['duration']}s): [SILENCE/UNRECOGNIZED]")
            except sr.RequestError as e:
                print(f"Segment {i+1} ({seg['duration']}s): [API ERROR]")
        
        os.remove(wav_path)
