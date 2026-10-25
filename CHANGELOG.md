# Changelog

## 0.3.0

- Generic `ScoreAdapter<T>`, validated `ScoreDocument`, model ID lookup and precise
  `ScoreTimeline` index; asynchronous/uncontrolled state submission API.
- Multiple playing/selected notes and fractional playhead positions, controlled
  by a host player. Independent configurable highlight shapes and Canvas hook.
- Continuous horizontal and viewport-width wrapping vertical score systems,
  including all tracks, hit testing, resize/zoom reflow and playback follow-scroll.
- Real sample-only GP4 decoding with PyGuitarPro and a normalized mapper.
- Desktop General MIDI playback driven by the sequencer's real tick position,
  repeat/alternate-ending expansion, tempo changes, pause/stop/scrub and seeking.
- Requested Nothing Else Matters tab loaded on explicit download/import, not
  redistributed. A generated original GP4 fixture remains bundled for offline use.
- Android/iOS retain the UI-only predecoded exercise; desktop-only import/audio
  capabilities are explicit rather than fake mobile controls.
- GP4 parser/MIDI tests, cursor/layout tests and expanded real-window CI captures.

Engraving and device-testing boundaries are listed in README; this update does
not claim a complete publication engraver or a full Guitar Pro audio synthesizer.

## 0.2.0 — advanced preview

Initial adaptive Canvas renderer, model validation, Bravura glyphs, tab rhythm,
selection, navigation, app packaging and verified screenshots. Historical preview:
https://github.com/romankolov/compose-tab/releases/tag/v0.2.0-preview.1
