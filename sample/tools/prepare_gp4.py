#!/usr/bin/env python3
"""Create an original GP4 fixture, or fetch the user-specified sample for local/CI use.
The requested copyrighted song is deliberately not committed or bundled in binaries.
"""
import argparse
import hashlib
from pathlib import Path
import urllib.request

URL = 'https://gprotab.net/en/tabs/metallica/nothing-else-matters?download'
ROOT = Path(__file__).resolve().parents[2]


def fixture():
    import guitarpro
    from guitarpro import models as gp
    song = gp.Song()
    song.title = 'Open strings study'
    song.artist = 'Compose Tab · Original exercise'
    song.tempo = 100
    track = song.tracks[0]
    track.name = 'Acoustic guitar'
    track.channel.instrument = 24
    for index in range(12):
        if index:
            header = gp.MeasureHeader(number=index + 1, start=960 + index * 3840)
            song.measureHeaders.append(header)
            measure = gp.Measure(track, header)
            track.measures.append(measure)
        measure = track.measures[index]
        voice = measure.voices[0]
        for bi, string in enumerate([6, 4, 3, 2, 4, 3, 1, 2]):
            beat = gp.Beat(voice, start=measure.start + bi * 480, status=gp.BeatStatus.normal,
                           duration=gp.Duration(value=8))
            note = gp.Note(beat, string=string, value=(index % 3 if string < 4 else 0), type=gp.NoteType.normal)
            beat.notes.append(note)
            voice.beats.append(beat)
    path = ROOT / 'sample/src/commonMain/composeResources/files/original-study.gp4'
    guitarpro.write(song, str(path), version=(4, 0, 6))
    return path


def fetch():
    target = ROOT / 'sample/local/nothing-else-matters.gp4'
    target.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(URL, headers={'User-Agent': 'Mozilla/5.0 (compatible; ComposeTabSample/0.3)'})
    with urllib.request.urlopen(request, timeout=45) as response:
        content = response.read(16 * 1024 * 1024 + 1)
    if len(content) > 16 * 1024 * 1024 or b'FICHIER GUITAR PRO v4.' not in content[:31]:
        raise ValueError('Download is not a GP4 file; save the supplied link manually and import it')
    target.write_bytes(content)
    print(f'Retrieved user-requested GP4: {len(content)} bytes, SHA-256 {hashlib.sha256(content).hexdigest()}')
    return target


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--fetch', action='store_true')
    args = parser.parse_args()
    print(fetch() if args.fetch else fixture())
