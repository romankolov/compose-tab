# Changelog

## 0.3.0

- Compact, adjustable tab string/system spacing with headless downward rhythm
  beams by default; optional legacy note heads and rhythm visibility controls.
- Connected legato runs, taller bend arrows with hold/release, zigzag vibrato and
  harmonic diamonds. Dead notes/percussive slaps render as x, including GP mapping.

- Note-local tab hammer/pull/bend visuals, ghost fret parentheses, harmonic fret
  brackets/types, chord strum arrows, and independent tab string spacing.
- Animated playback-follow scrolling, cancellable by manual navigation.
- Score-first sample: full viewport canvas, compact bottom transport, import and
  track/layout/style/zoom controls moved into More / Settings.

- Generic `ScoreAdapter<T>`, validated `ScoreDocument`, model ID lookup and precise
  `ScoreTimeline` index; asynchronous/uncontrolled state submission API.
- Multiple playing/selected notes and fractional playhead positions, controlled
  by a host player. Independent configurable highlight shapes and Canvas hook.
- Continuous horizontal and viewport-width wrapping vertical score systems,
  including all tracks, hit testing, resize/zoom reflow and playback follow-scroll.
- Sample-only TuxGuitar Java GP1–GP5 readers/MIDI export and a shared Kotlin
  adapter, replacing the Python runtime bridge on Android/Desktop.
- Desktop General MIDI playback driven by the sequencer's real tick position,
  repeat/alternate-ending expansion, tempo changes, pause/stop/scrub and seeking.
- Requested Nothing Else Matters tab loaded on explicit download/import, not
  redistributed. A generated original GP4 fixture remains bundled for offline use.
- Android SAF Load GP file button and native General MIDI playback with
  tempo-map/player-clock cursor, audio focus handling, pause/stop/seek and cleanup.
  iOS retains the UI-only predecoded exercise.
- Java GP3/GP4/GP5 parser and repeat/tempo/MIDI tests, cursor/layout tests and expanded real-window CI captures.

Engraving and device-testing boundaries are listed in README; this update does
not claim a complete publication engraver or a full Guitar Pro audio synthesizer.

## 0.2.0 — advanced preview

Initial adaptive Canvas renderer, model validation, Bravura glyphs, tab rhythm,
selection, navigation, app packaging and verified screenshots. Historical preview:
https://github.com/romankolov/compose-tab/releases/tag/v0.2.0-preview.1
