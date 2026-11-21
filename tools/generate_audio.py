#!/usr/bin/env python3
"""Deterministic procedural audio synthesizer for Words of Wisdom (& Life Lessons).

Generates warm, musical looping BGM tracks and crisp, joyful party SFX WAV files
into app/src/main/res/raw/ using pure Python stdlib (zero third-party audio deps).
"""

from __future__ import annotations

import math
import pathlib
import random
import struct
import wave

SR = 22050  # Clean, compact 22.05 kHz 16-bit PCM WAV


def midi_to_hz(note: float) -> float:
    return 440.0 * (2.0 ** ((note - 69.0) / 12.0))


def write_wav(path: pathlib.Path, samples: list[float], peak: float = 0.85) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    max_abs = max((abs(s) for s in samples), default=1.0)
    scale = (peak / max_abs) if max_abs > 1e-6 else 1.0
    pcm = bytearray()
    for s in samples:
        v = max(-1.0, min(1.0, s * scale))
        pcm += struct.pack("<h", int(v * 32767.0))
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SR)
        wf.writeframes(bytes(pcm))


def add_note(
    buf: list[float],
    start_s: float,
    dur_s: float,
    midi: float,
    amp: float,
    timbre: str = "rhodes",
    wrap: bool = False,
) -> None:
    freq = midi_to_hz(midi)
    n_total = len(buf)
    start_i = int(start_s * SR)
    n_samples = int(dur_s * SR)
    if n_samples <= 0:
        return
    for i in range(n_samples):
        idx = start_i + i
        if wrap:
            idx %= n_total
        elif idx < 0 or idx >= n_total:
            continue
        t = i / SR
        rel = i / n_samples

        if timbre == "rhodes":
            # Warm electric piano: bell attack + soft mellow body
            attack = min(1.0, t / 0.012)
            decay = math.exp(-t * 2.1)
            release = min(1.0, (dur_s - t) / 0.04) if (dur_s - t) < 0.04 else 1.0
            env = attack * decay * max(0.0, release)
            val = (
                1.0 * math.sin(2 * math.pi * freq * t)
                + 0.25 * math.exp(-t * 7.0) * math.sin(2 * math.pi * 2 * freq * t)
                + 0.12 * math.exp(-t * 14.0) * math.sin(2 * math.pi * 4 * freq * t)
            )
        elif timbre == "marimba":
            # Woody warm party marimba pluck
            attack = min(1.0, t / 0.005)
            decay = math.exp(-t * 7.5)
            env = attack * decay
            val = (
                1.0 * math.sin(2 * math.pi * freq * t)
                + 0.35 * math.exp(-t * 18.0) * math.sin(2 * math.pi * 3.98 * freq * t)
            )
        elif timbre == "bass":
            # Upright plucked warm bass
            attack = min(1.0, t / 0.015)
            decay = math.exp(-t * 3.2)
            release = min(1.0, (dur_s - t) / 0.03) if (dur_s - t) < 0.03 else 1.0
            env = attack * decay * max(0.0, release)
            val = (
                1.0 * math.sin(2 * math.pi * freq * t)
                + 0.30 * math.sin(2 * math.pi * 2 * freq * t) * math.exp(-t * 5.0)
            )
        elif timbre == "harp":
            # Shimmering golden harp pluck
            attack = min(1.0, t / 0.004)
            decay = math.exp(-t * 3.8)
            env = attack * decay
            val = (
                1.0 * math.sin(2 * math.pi * freq * t)
                + 0.42 * math.sin(2 * math.pi * 2 * freq * t) * math.exp(-t * 4.5)
                + 0.18 * math.sin(2 * math.pi * 3 * freq * t) * math.exp(-t * 7.0)
            )
        elif timbre == "brass":
            # Bright joyful fanfare horn-chime
            attack = min(1.0, t / 0.025)
            release = min(1.0, (dur_s - t) / 0.06) if (dur_s - t) < 0.06 else 1.0
            env = attack * (0.75 + 0.25 * math.exp(-t * 3.0)) * max(0.0, release)
            val = (
                1.0 * math.sin(2 * math.pi * freq * t)
                + 0.55 * math.sin(2 * math.pi * 2 * freq * t)
                + 0.30 * math.sin(2 * math.pi * 3 * freq * t)
                + 0.12 * math.sin(2 * math.pi * 4 * freq * t)
            )
        else:
            env = math.sin(math.pi * rel)
            val = math.sin(2 * math.pi * freq * t)

        buf[idx] += amp * env * val


def add_shaker(buf: list[float], start_s: float, dur_s: float, amp: float, rng: random.Random, wrap: bool = False) -> None:
    n_total = len(buf)
    start_i = int(start_s * SR)
    n_samples = int(dur_s * SR)
    prev = 0.0
    for i in range(n_samples):
        idx = start_i + i
        if wrap:
            idx %= n_total
        elif idx < 0 or idx >= n_total:
            continue
        t = i / SR
        env = min(1.0, t / 0.004) * math.exp(-t * 32.0)
        # High-passed noise for crisp brush/shaker
        white = rng.uniform(-1.0, 1.0)
        hp = white - 0.85 * prev
        prev = white
        buf[idx] += amp * env * hp


def add_woodblock(buf: list[float], start_s: float, amp: float, pitch_hz: float = 880.0, wrap: bool = False) -> None:
    n_total = len(buf)
    start_i = int(start_s * SR)
    n_samples = int(0.08 * SR)
    for i in range(n_samples):
        idx = start_i + i
        if wrap:
            idx %= n_total
        elif idx < 0 or idx >= n_total:
            continue
        t = i / SR
        env = math.exp(-t * 58.0)
        val = math.sin(2 * math.pi * pitch_hz * t) + 0.45 * math.sin(2 * math.pi * (pitch_hz * 1.48) * t)
        buf[idx] += amp * env * val


def make_bgm_lobby() -> list[float]:
    # 100 BPM, 8 bars (19.2s seamless loop), warm parlor lounge Cmaj9 -> Am9 -> Dm9 -> G13
    beat = 60.0 / 100.0
    total_beats = 32
    dur = total_beats * beat
    buf = [0.0] * int(dur * SR)
    rng = random.Random(42)

    chords = [
        (48, [60, 64, 67, 71, 74]),  # Cmaj9
        (45, [57, 60, 64, 67, 71]),  # Am9
        (50, [62, 65, 69, 72, 76]),  # Dm9
        (43, [59, 62, 65, 69, 72]),  # G13
        (48, [60, 64, 67, 71, 74]),  # Cmaj9
        (52, [64, 67, 71, 74, 79]),  # Em9
        (50, [62, 65, 69, 72, 76]),  # Dm9
        (43, [59, 64, 67, 71, 74]),  # G6/9
    ]

    melody_patterns = [
        [(0.0, 76), (1.5, 74), (2.5, 71), (3.0, 74)],
        [(0.0, 72), (1.0, 71), (2.5, 69), (3.5, 72)],
        [(0.0, 76), (1.5, 74), (2.0, 72), (3.0, 69)],
        [(0.0, 71), (1.5, 74), (2.5, 76), (3.5, 74)],
        [(0.0, 79), (1.5, 76), (2.5, 74), (3.0, 76)],
        [(0.0, 74), (1.0, 71), (2.5, 74), (3.5, 76)],
        [(0.0, 72), (1.5, 74), (2.5, 76), (3.5, 72)],
        [(0.0, 74), (1.5, 71), (2.5, 67), (3.5, 71)],
    ]

    for bar, (bass_n, chord) in enumerate(chords):
        bar_t = bar * 4.0 * beat
        # Warm Rhodes pad on beat 1 & gentle syncopation on beat 2.5
        for n in chord:
            add_note(buf, bar_t, 2.3 * beat, n, 0.11, "rhodes", wrap=True)
            add_note(buf, bar_t + 2.5 * beat, 1.4 * beat, n, 0.08, "rhodes", wrap=True)
        # Bossa upright bass
        add_note(buf, bar_t, 1.4 * beat, bass_n, 0.28, "bass", wrap=True)
        add_note(buf, bar_t + 1.5 * beat, 0.9 * beat, bass_n + 7, 0.20, "bass", wrap=True)
        add_note(buf, bar_t + 2.5 * beat, 1.3 * beat, bass_n, 0.24, "bass", wrap=True)
        # Playful marimba motif
        for offset, m_note in melody_patterns[bar]:
            add_note(buf, bar_t + offset * beat, 0.45 * beat, m_note, 0.16, "marimba", wrap=True)
        # Soft brush rhythm
        for step in range(8):
            amp = 0.055 if step % 2 == 0 else 0.03
            add_shaker(buf, bar_t + step * 0.5 * beat, 0.09, amp, rng, wrap=True)
        add_woodblock(buf, bar_t + 1.5 * beat, 0.06, 760.0, wrap=True)
        add_woodblock(buf, bar_t + 3.0 * beat, 0.05, 920.0, wrap=True)

    return buf


def make_bgm_play() -> list[float]:
    # 114 BPM, 4 bars (8.42s seamless loop), playful curious game-show groove
    beat = 60.0 / 114.0
    total_beats = 16
    dur = total_beats * beat
    buf = [0.0] * int(dur * SR)
    rng = random.Random(99)

    chords = [
        (45, [60, 64, 67, 71]),  # Am7
        (50, [62, 65, 69, 72]),  # Dm7
        (43, [59, 62, 65, 69]),  # G9
        (48, [60, 64, 67, 71]),  # Cmaj7
    ]
    arp = [
        [72, 76, 79, 76, 71, 76, 74, 72],
        [74, 77, 81, 77, 72, 77, 76, 74],
        [71, 74, 79, 74, 69, 74, 76, 74],
        [72, 76, 79, 83, 79, 76, 74, 71],
    ]

    for bar, (bass_n, chord) in enumerate(chords):
        bar_t = bar * 4.0 * beat
        for n in chord:
            add_note(buf, bar_t, 1.5 * beat, n, 0.09, "rhodes", wrap=True)
            add_note(buf, bar_t + 2.0 * beat, 1.5 * beat, n, 0.08, "rhodes", wrap=True)
        add_note(buf, bar_t, 0.9 * beat, bass_n, 0.28, "bass", wrap=True)
        add_note(buf, bar_t + 1.0 * beat, 0.8 * beat, bass_n + 7, 0.22, "bass", wrap=True)
        add_note(buf, bar_t + 2.0 * beat, 0.9 * beat, bass_n, 0.26, "bass", wrap=True)
        add_note(buf, bar_t + 3.0 * beat, 0.8 * beat, bass_n + 5, 0.20, "bass", wrap=True)
        for step, note in enumerate(arp[bar]):
            add_note(buf, bar_t + step * 0.5 * beat, 0.32 * beat, note, 0.14, "marimba", wrap=True)
        for step in range(8):
            add_shaker(buf, bar_t + step * 0.5 * beat, 0.07, 0.05 if step % 2 == 0 else 0.025, rng, wrap=True)
        add_woodblock(buf, bar_t + 1.0 * beat, 0.09, 980.0, wrap=True)
        add_woodblock(buf, bar_t + 3.0 * beat, 0.09, 1180.0, wrap=True)

    return buf


def make_bgm_podium() -> list[float]:
    # 120 BPM, 4 bars (8.0s loop), joyful celebratory trophy lounge groove
    beat = 0.5
    total_beats = 16
    dur = total_beats * beat
    buf = [0.0] * int(dur * SR)
    rng = random.Random(7)

    chords = [
        (53, [65, 69, 72, 76]),  # Fmaj7
        (55, [67, 71, 74, 79]),  # G6
        (52, [64, 67, 71, 74]),  # Em7
        (48, [60, 64, 67, 72]),  # Cmaj
    ]
    melody = [
        [(0.0, 81), (0.5, 79), (1.0, 76), (2.0, 79), (3.0, 81)],
        [(0.0, 83), (0.5, 81), (1.0, 79), (2.0, 83), (3.0, 86)],
        [(0.0, 83), (1.0, 79), (2.0, 76), (3.0, 79)],
        [(0.0, 84), (1.0, 79), (2.0, 84), (3.0, 88)],
    ]
    for bar, (bass_n, chord) in enumerate(chords):
        bar_t = bar * 4.0 * beat
        for n in chord:
            add_note(buf, bar_t, 3.6 * beat, n, 0.11, "rhodes", wrap=True)
        add_note(buf, bar_t, 1.8 * beat, bass_n - 12, 0.28, "bass", wrap=True)
        add_note(buf, bar_t + 2.0 * beat, 1.8 * beat, bass_n - 5, 0.22, "bass", wrap=True)
        for off, mn in melody[bar]:
            add_note(buf, bar_t + off * beat, 0.6 * beat, mn, 0.20, "harp", wrap=True)
        for step in range(8):
            add_shaker(buf, bar_t + step * 0.5 * beat, 0.08, 0.055 if step % 2 == 0 else 0.03, rng, wrap=True)
    return buf


def make_sfx_join() -> list[float]:
    buf = [0.0] * int(0.45 * SR)
    for i, n in enumerate([72, 76, 79, 86]):
        add_note(buf, i * 0.055, 0.28, n, 0.35, "marimba")
    return buf


def make_sfx_submit() -> list[float]:
    buf = [0.0] * int(0.85 * SR)
    # Magical golden harp glissando
    for i, n in enumerate([64, 67, 71, 74, 76, 79, 83, 88]):
        add_note(buf, i * 0.045, 0.45, n, 0.24, "harp")
    return buf


def make_sfx_vote() -> list[float]:
    buf = [0.0] * int(0.22 * SR)
    add_woodblock(buf, 0.0, 0.55, 1050.0)
    add_note(buf, 0.02, 0.16, 79, 0.35, "marimba")
    return buf


def make_sfx_tick() -> list[float]:
    buf = [0.0] * int(0.14 * SR)
    add_woodblock(buf, 0.0, 0.70, 1240.0)
    return buf


def make_sfx_reveal() -> list[float]:
    buf = [0.0] * int(1.1 * SR)
    # Shimmer rise into warm bright chord hit
    for i, n in enumerate([60, 64, 67, 71, 74, 79]):
        add_note(buf, i * 0.035, 0.55, n, 0.20, "harp")
    for n in [60, 67, 72, 76, 79, 84]:
        add_note(buf, 0.24, 0.80, n, 0.20, "brass")
    return buf


def make_sfx_correct() -> list[float]:
    buf = [0.0] * int(0.75 * SR)
    for i, n in enumerate([72, 76, 79, 84, 88]):
        add_note(buf, i * 0.065, 0.40, n, 0.28, "harp")
    return buf


def make_sfx_wrong() -> list[float]:
    buf = [0.0] * int(0.45 * SR)
    add_note(buf, 0.0, 0.20, 67, 0.40, "marimba")
    add_note(buf, 0.14, 0.26, 62, 0.40, "marimba")
    return buf


def make_sfx_applause() -> list[float]:
    buf = [0.0] * int(0.9 * SR)
    rng = random.Random(123)
    for _ in range(28):
        t = rng.uniform(0.0, 0.65)
        add_shaker(buf, t, 0.07, rng.uniform(0.08, 0.16), rng)
    for i, n in enumerate([79, 83, 86, 91]):
        add_note(buf, 0.05 + i * 0.06, 0.45, n, 0.22, "harp")
    return buf


def make_sfx_fanfare() -> list[float]:
    buf = [0.0] * int(1.8 * SR)
    seq = [
        (0.00, 0.16, 72),
        (0.16, 0.16, 76),
        (0.32, 0.16, 79),
        (0.50, 0.22, 84),
        (0.76, 0.14, 79),
        (0.92, 0.85, 84),
    ]
    for st, d, n in seq:
        add_note(buf, st, d, n, 0.26, "brass")
        add_note(buf, st, d + 0.15, n - 12, 0.18, "harp")
    for n in [60, 64, 67, 72, 76, 79, 84]:
        add_note(buf, 0.92, 0.85, n, 0.12, "rhodes")
    return buf


def main() -> int:
    out_dir = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"
    tracks = {
        "bgm_lobby.wav": (make_bgm_lobby(), 0.72),
        "bgm_play.wav": (make_bgm_play(), 0.72),
        "bgm_podium.wav": (make_bgm_podium(), 0.75),
        "sfx_join.wav": (make_sfx_join(), 0.88),
        "sfx_submit.wav": (make_sfx_submit(), 0.88),
        "sfx_vote.wav": (make_sfx_vote(), 0.88),
        "sfx_tick.wav": (make_sfx_tick(), 0.85),
        "sfx_reveal.wav": (make_sfx_reveal(), 0.90),
        "sfx_correct.wav": (make_sfx_correct(), 0.88),
        "sfx_wrong.wav": (make_sfx_wrong(), 0.85),
        "sfx_applause.wav": (make_sfx_applause(), 0.88),
        "sfx_fanfare.wav": (make_sfx_fanfare(), 0.92),
    }
    for name, (samples, peak) in tracks.items():
        path = out_dir / name
        write_wav(path, samples, peak=peak)
        kb = path.stat().st_size / 1024.0
        print(f"Generated {name:18s} ({len(samples)/SR:4.2f}s, {kb:6.1f} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
