# Changelog

## 0.2.0 — advanced preview

### Library
- Decoder-independent validated score hierarchy and guitar effects.
- Adaptive, duration-weighted measure columns aligned across tracks.
- Meter-aware simple/compound beam grouping and mixed-duration hooks.
- Shared chord stems, ledger lines, repeats and explicit accidentals.
- Bundled Bravura treble-clef/rest/accidental glyphs (SIL OFL).
- Tab-only rhythmic guide, optional via `ScoreStyle.showTabRhythm`.
- Same-string effect endpoints within a measure; conventional bend labels.
- Binary-search viewport culling and hit testing against shared geometry.
- Pan, pinch, wheel and Ctrl+wheel; keyboard selection and zoom shortcuts.
- Accessible score descriptions and next/previous/zoom actions.
- Custom styles, reactive selection and viewport events, additive effect hook.
- Maven publication configuration for model/UI modules, version 0.2.0.

### Sample and verification
- Mock GP5 decoder and explicit normalized-model adapter outside the library.
- Android, Desktop and iOS entry points, plus SwiftUI host source.
- Paper/night-paper styling, view controls and note inspector.
- Model/layout unit tests and actual Compose pointer/keyboard tests.
- GitHub Actions APK build, desktop packaging and application launch/captures,
  packaged-binary smoke test, and macOS iOS simulator framework link.
- Reproducible screenshots committed to the working branch by CI.

### Release boundaries
This is an advanced preview, not a notation-complete stable engraver. No real
GP5 decoding or playback is shipped. No key signatures, tuplets, voices, ties,
wrapping/printing, collision solver or per-note accessibility tree yet. Only
the Linux desktop app is executed in CI; Android is assembled and the iOS
simulator framework is linked, not device- or simulator-UI-tested. No Maven
Central upload or App Store / Play Store release is performed.
