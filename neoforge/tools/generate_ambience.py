#!/usr/bin/env python3
"""Original MIT-licensed ambience for the two sound events absent in Minecraft 1.21.1.

Requires ffmpeg. Uses deterministic procedural synthesis; no Minecraft audio is
copied or downloaded. Re-run to regenerate the six bundled mono Vorbis files.
"""
import array
import math
import random
import subprocess
import tempfile
import wave
from pathlib import Path

RATE = 24000
SECONDS = 12
ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/justaclient/sounds/ambient'
ROOT.mkdir(parents=True, exist_ok=True)
for kind in ('fireflies', 'dry_grass'):
    for variant in range(3):
        randomizer = random.Random(1977 + variant)
        samples = array.array('h')
        breeze = slow = 0.0
        for frame in range(RATE * SECONDS):
            time = frame / RATE
            noise = randomizer.uniform(-1, 1)
            slow += (noise - slow) * .004
            breeze += (noise - breeze) * .06
            fade = min(1, time / .8, (SECONDS - time) / .8)
            if kind == 'dry_grass':
                gust = .4 + .3 * math.sin(time * .7 + variant) + .2 * math.sin(time * 1.7)
                value = (breeze - slow * .5) * gust * .65
            else:
                cycle = (time * (1.8 + variant * .23)) % 1
                chirp = max(0, math.sin(math.pi * cycle)) ** 18
                modulation = .65 + .35 * math.sin(time * math.tau * 22)
                value = chirp * modulation * math.sin(time * math.tau * (3300 + variant * 180)) * .09 + breeze * .015
            samples.append(round(max(-1, min(1, value * fade)) * 32767))
        with tempfile.TemporaryDirectory() as temp:
            wav = Path(temp) / 'audio.wav'
            with wave.open(str(wav), 'wb') as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(RATE)
                output.writeframes(samples.tobytes())
            subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', str(wav), '-c:a', 'libvorbis', '-q:a', '3', str(ROOT / f'{kind}_{variant}.ogg')], check=True)
