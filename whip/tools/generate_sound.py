#!/usr/bin/env python3
"""Synthesises the whip crack: a short rising whoosh followed by a sharp supersonic snap.

Run: python3 whip/tools/generate_sound.py  (needs numpy and ffmpeg with libvorbis).
Writes assets/whip/sounds/crack.ogg; commit it.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "whip", "sounds", "crack.ogg")


def main():
    rng = np.random.default_rng(7)
    # Whoosh: band-limited noise swelling over 140 ms.
    n = int(RATE * 0.14)
    noise = rng.standard_normal(n)
    smooth = np.convolve(noise, np.ones(12) / 12, mode="same")
    whoosh = smooth * np.linspace(0, 1, n) ** 2 * 0.35
    # Snap: a 2 ms broadband click with a fast decay and a little body.
    m = int(RATE * 0.09)
    t = np.arange(m) / RATE
    snap = rng.standard_normal(m) * np.exp(-t / 0.006)
    snap += np.sin(2 * np.pi * 2300 * t) * np.exp(-t / 0.004) * 0.8
    snap[: int(RATE * 0.002)] *= 2.5
    # Short room tail.
    k = int(RATE * 0.12)
    tail = rng.standard_normal(k) * np.exp(-np.arange(k) / RATE / 0.03) * 0.08
    sound = np.concatenate([whoosh, snap, tail])
    sound /= np.max(np.abs(sound))
    pcm = (sound * 0.95 * 32767).astype(np.int16)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "crack.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "6", OUT], check=True)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
