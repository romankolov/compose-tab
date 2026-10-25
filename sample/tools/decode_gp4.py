#!/usr/bin/env python3
"""Sample-only GP4 -> normalized UI JSON + standard MIDI. Requires requirements.txt.
Uses the real PyGuitarPro parser (LGPL-3.0), not a header/extension-based mock.
No third-party parser code is copied. File I/O/audio never enter score-model/score-ui.
"""
import base64
import io
import json
import sys
from pathlib import Path

import guitarpro
import mido

PPQ = 960
MAX_FILE = 16 * 1024 * 1024


def repeat_order(headers):
    """Expand bounded nested repeat blocks and GP alternate-ending bit masks."""
    order, stack = [], []
    index, last_pass = 0, 1
    visits = 0
    while index < len(headers):
        visits += 1
        if visits > 65536:
            raise ValueError('Repeat structure exceeds safety limit')
        header = headers[index]
        if header.isRepeatOpen and (not stack or stack[-1][0] != index):
            stack.append([index, 1])
        iteration = stack[-1][1] if stack else last_pass
        if not header.repeatAlternative or header.repeatAlternative & (1 << (iteration - 1)):
            order.append(index)
        if header.repeatClose >= 0:
            if not stack:
                stack.append([0, 1])
            if stack[-1][1] <= header.repeatClose:
                stack[-1][1] += 1
                index = stack[-1][0]
                continue
            last_pass = stack.pop()[1]
        index += 1
    return order


def decode(path):
    path = Path(path)
    if not 0 < path.stat().st_size <= MAX_FILE:
        raise ValueError('GP4 must be nonempty and at most 16 MiB')
    with path.open('rb') as source:
        signature = source.read(31)
    if b'FICHIER GUITAR PRO v4.' not in signature:
        raise ValueError('Expected a genuine Guitar Pro 4 file (v4.x)')
    song = guitarpro.parse(str(path))
    warnings = {'General MIDI preview: guitar articulations use simplified synthesis.'}
    tracks = []
    source_notes = {}
    source_beats = {}
    for ti, track in enumerate(song.tracks):
        tid = f't{ti}'
        measures = []
        for mi, measure in enumerate(track.measures):
            mid = f'{tid}:m{mi}'
            beats = []
            for vi, voice in enumerate(measure.voices):
                for bi, beat in enumerate(voice.beats):
                    if beat.status.name == 'empty':
                        continue
                    bid = f'{mid}:v{vi}:b{bi}'
                    duration = beat.duration.time / PPQ
                    onset = (beat.start - measure.start) / PPQ
                    notes = []
                    for ni, note in enumerate(beat.notes):
                        if not 1 <= note.string <= len(track.strings):
                            raise ValueError('Invalid string index')
                        nid = f'{bid}:n{ni}'
                        effects = []
                        e = note.effect
                        if e.bend and e.bend.points:
                            effects.append({'type': 'bend', 'semitones': max(0, max(p.value for p in e.bend.points))})
                        for enabled, label in [(e.hammer, 'hammer'), (bool(e.slides), 'slide'), (e.vibrato, 'vibrato'), (e.palmMute, 'palmMute')]:
                            if enabled:
                                effects.append({'type': label})
                        if e.grace or e.harmonic or e.trill or e.tremoloPicking:
                            warnings.add('Grace notes, harmonics, trills and tremolo picking are decoded but not fully engraved/synthesized.')
                        if note.type.name == 'tie':
                            warnings.add('Ties are sustained in MIDI; tie arcs are not engraved by this sample.')
                        if note.type.name == 'dead':
                            warnings.add('Dead-note engraving and synthesis are simplified.')
                        midi = note.realValue
                        if not 0 <= midi <= 127:
                            raise ValueError(f'Pitch outside MIDI range in track {ti + 1}, measure {mi + 1}')
                        notes.append({'id': nid, 'midi': midi, 'string': note.string, 'fret': max(0, note.value), 'effects': effects})
                        source_notes[nid] = note
                    value = beat.duration.value
                    # The visual base stays in the library vocabulary; the exact ratio is retained.
                    base = min(value, 32)
                    visual_duration = (4 / base) * (1.5 if beat.duration.isDotted else 1)
                    beats.append({'id': bid, 'duration': base, 'dotted': beat.duration.isDotted,
                                  'offset': onset, 'scale': duration / visual_duration, 'notes': notes})
                    source_beats[bid] = beat
            measures.append({'id': mid, 'number': mi + 1, 'numerator': measure.header.timeSignature.numerator,
                             'denominator': measure.header.timeSignature.denominator.value,
                             'length': measure.header.length / PPQ, 'repeatStart': measure.header.isRepeatOpen,
                             'repeatEnd': measure.header.repeatClose >= 0, 'beats': sorted(beats, key=lambda b: b['offset'])})
        tracks.append({'id': tid, 'name': track.name, 'tuning': [s.value for s in track.strings],
                       'percussion': track.isPercussionTrack, 'measures': measures})
    if len(song.measureHeaders) > 4096 or len(song.tracks) > 64:
        raise ValueError('Score exceeds sample limits (4096 bars / 64 tracks)')
    order = repeat_order(song.measureHeaders)
    midi_file = mido.MidiFile(ticks_per_beat=PPQ)
    conductor_events = [(0, mido.MetaMessage('set_tempo', tempo=mido.bpm2tempo(song.tempo)))]
    moments = []
    midi_tracks = []
    total_ticks = sum(song.measureHeaders[index].length for index in order)
    for ti, track in enumerate(tracks):
        raw = song.tracks[ti]
        channel = 9 if raw.isPercussionTrack else raw.channel.channel
        events = [(0, mido.Message('program_change', channel=channel, program=raw.channel.instrument))]
        note_spans = []
        previous = {}
        cursor = 0
        for mi in order:
            for beat in track['measures'][mi]['beats']:
                original = source_beats[beat['id']]
                start = cursor + round(beat['offset'] * PPQ)
                duration = original.duration.time
                moments.append({'trackId': track['id'], 'measureId': track['measures'][mi]['id'],
                                'beatId': beat['id'], 'start': start / PPQ, 'duration': duration / PPQ,
                                'noteIds': [n['id'] for n in beat['notes']]})
                change = original.effect.mixTableChange
                if change and change.tempo is not None and change.tempo.value > 0:
                    conductor_events.append((start, mido.MetaMessage('set_tempo', tempo=mido.bpm2tempo(change.tempo.value))))
                for note in beat['notes']:
                    n = source_notes[note['id']]
                    end = start + max(1, round(duration * n.durationPercent * (.55 if n.effect.staccato else 1)))
                    prev = previous.get(n.string)
                    if n.type.name == 'tie' and prev and prev['pitch'] == note['midi']:
                        prev['end'] = end
                    else:
                        span = {'start': start, 'end': end, 'pitch': note['midi'], 'velocity': n.velocity}
                        previous[n.string] = span
                        note_spans.append(span)
            cursor += song.measureHeaders[mi].length
        for span in note_spans:
            events.append((span['start'], mido.Message('note_on', channel=channel, note=span['pitch'], velocity=span['velocity'])))
            events.append((span['end'], mido.Message('note_off', channel=channel, note=span['pitch'], velocity=0)))
        midi_tracks.append(events)
    for events in [conductor_events] + midi_tracks:
        mt = mido.MidiTrack()
        previous_tick = 0
        # Note-offs precede attacks at the same tick, preserving rearticulation.
        for tick, message in sorted(events, key=lambda item: (item[0], 0 if item[1].type == 'note_off' else 1)):
            message.time = tick - previous_tick
            mt.append(message)
            previous_tick = tick
        mt.append(mido.MetaMessage('end_of_track', time=max(0, total_ticks - previous_tick)))
        midi_file.tracks.append(mt)
    binary = io.BytesIO()
    midi_file.save(file=binary)
    return {'schemaVersion': 1, 'title': song.title, 'composer': song.artist or song.music,
            'tempo': song.tempo, 'warnings': sorted(warnings), 'tracks': tracks, 'moments': moments,
            'midiBase64': base64.b64encode(binary.getvalue()).decode('ascii')}


if __name__ == '__main__':
    try:
        result = decode(sys.argv[1])
        json.dump(result, sys.stdout, separators=(',', ':'))
    except Exception as error:
        print(f'GP4 decode failed: {error}', file=sys.stderr)
        sys.exit(1)
