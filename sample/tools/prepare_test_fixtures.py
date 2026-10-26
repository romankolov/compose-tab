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

# Original, freely redistributable articulation exercise; the sample's Java decoder
# (not this optional generator) is exercised by tests and CI screenshot capture.
song = guitarpro.parse(str(root / 'sample/src/commonMain/composeResources/files/original-study.gp4'))
song.title = 'Tab articulation study'
voice = song.tracks[0].measures[0].voices[0]
voice.beats.clear()
for i, fret in enumerate([5, 7, 5, 12, 0, 0, 3, 5]):
    beat = models.Beat(voice, start=960 + i * 480, status=models.BeatStatus.normal, duration=models.Duration(value=8))
    note = models.Note(beat, string=2, value=fret, type=models.NoteType.normal)
    if i in (0, 1): note.effect.hammer = True
    if i == 2: note.effect.bend = models.BendEffect(type=models.BendType.bend, value=50, points=[models.BendPoint(0, 0), models.BendPoint(60, 2)])
    if i == 3: note.effect.harmonic = models.NaturalHarmonic()
    if i == 6: note.effect.ghostNote = True
    if i == 7: note.effect.harmonic = models.PinchHarmonic()
    beat.notes.append(note)
    if i in (4, 5):
        beat.notes.extend([models.Note(beat, string=s, value=0, type=models.NoteType.normal) for s in [1, 3, 4]])
        beat.effect.stroke = models.BeatStroke(direction=models.BeatStrokeDirection.down if i == 4 else models.BeatStrokeDirection.up, value=8)
    voice.beats.append(beat)
# Include sustained bend + vibrato, a dead-string rake and a percussive slap.
voice.beats[2].notes[0].effect.vibrato = True
voice.beats[2].notes[0].effect.bend.points = [models.BendPoint(0, 0), models.BendPoint(30, 2), models.BendPoint(60, 2)]
second = song.tracks[0].measures[1].voices[0].beats
second[0].notes[0].type = models.NoteType.dead
second[1].effect.slapEffect = models.SlapEffect.slapping
second[2].notes[0].effect.bend = models.BendEffect(type=models.BendType.bendRelease, value=50,
    points=[models.BendPoint(0, 0), models.BendPoint(30, 2), models.BendPoint(60, 0)])
guitarpro.write(song, str(folder / 'effects.gp5'), version=(5, 1, 0))
