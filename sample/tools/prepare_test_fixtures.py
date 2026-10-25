#!/usr/bin/env python3
"""Developer-only regeneration of original GP3/GP5 decoder fixtures.
Install sample/tools/requirements.txt first. Apps and CI decoding do not use Python.
"""
from pathlib import Path
import guitarpro
from guitarpro import models

root = Path(__file__).resolve().parents[2]
folder = root / 'sample/src/desktopTest/resources/fixtures'
folder.mkdir(parents=True, exist_ok=True)
song = guitarpro.parse(str(root / 'sample/src/commonMain/composeResources/files/original-study.gp4'))
for version in [(3, 0, 0), (5, 1, 0)]:
    guitarpro.write(song, str(folder / f'original-study.gp{version[0]}'), version=version)
song.measureHeaders[0].isRepeatOpen = True
song.measureHeaders[1].repeatClose = 1
song.tracks[0].measures[3].voices[0].beats[0].effect.mixTableChange = models.MixTableChange(tempo=models.MixTableItem(60))
guitarpro.write(song, str(folder / 'repeat-tempo.gp5'), version=(5, 1, 0))
