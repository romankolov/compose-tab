# Compose Tab · 0.3.0

A UI-only Compose Multiplatform library for interactive music scores and guitar
 tablature on **Android, iOS and Desktop**. The library has **no decoder, player,
 audio device, file I/O or playback clock**. The separate sample demonstrates
TuxGuitar Java GP1–GP5 decoding and Android/Desktop General MIDI players.

## Start with your decoder

```kotlin
// Your decoder produces lightweight values. Keep your own IDs stable across edits.
val document = ScoreDocument(
    ScoreData("Study", tracks = listOf(
        Track("guitar", "Acoustic guitar", measures = listOf(
            Measure("bar-1", 1, beats = listOf(
                Beat("beat-1", Duration.Quarter, notes = listOf(
                    Note("note-1", midi = 64, string = 1, fret = 0, staffStep = 0)
                ))
            ))
        ))
    ))
)
val state = rememberScoreState(document)
VerticalScore(state, Modifier.fillMaxSize())
```

`staffStep` is diatonic distance above the bottom E4 (treble) or G2 (bass) line; MIDI alone does
not determine enharmonic spelling. Tuning lists use one-based string order
(high E first for standard guitar). An empty note list is a rest; a beat's notes
sound together. Values/lists must be treated as immutable.

For an existing decoder, implement the tiny generic boundary:

```kotlin
val adapter = ScoreAdapter<MyDecodedSong> { decoded ->
    ScoreDocument(
        data = decoded.toScoreData(),  // Your mapping; no inheritance required
        timeline = decoded.toScoreTimeline(), // Optional: exact offsets / repeat passes
    )
}
val state = rememberScoreState() // Uncontrolled state for asynchronous loading
LaunchedEffect(decodedSong) {
    state.submit(decodedSong, adapter, resetViewport = true)
}
```

`rememberScoreState(data)` and `rememberScoreState(document)` are **controlled**
by the supplied value; use the no-argument variant when calling `submit` yourself.
Do decoding/I/O off the UI thread, then submit on the UI thread. No Compose
classes are needed in the decoder. `sample/Gp4DocumentAdapter.kt` is a real,
independent mapping example, not a dependency required by consumers.

## Host-controlled playback pointers

There are no timers hidden inside `ScoreComposable`. Feed the view from the
**same clock used by your audio engine**, rather than maintaining a second timer:

```kotlin
// Quarters are musical time, not seconds. Use your decoder/player's tempo map.
state.setPlaybackQuarter(playerQuarterPosition)

// Or provide exact positions/active notes directly, including chords or several tracks:
state.setPlaybackPosition(ScorePlaybackPosition(
    positions = listOf(ScorePosition("beat-1", fraction = 0.45f)),
    noteIds = setOf("note-1", "note-2"),
))
state.setPlayingNotes("note-1", "note-2")
state.setPlayhead("beat-1", fraction = 0.6f)
state.clearPlayback() // Does not clear selection
```

`ScoreTimeline` is a pure timing index. Its intervals are start-inclusive,
end-exclusive; `at(quarter)` returns simultaneous active notes and beat fractions.
The default index uses measures/time signatures and sequential beats.
`Beat.offsetQuarters`, `Beat.durationScale`, and `Measure.lengthQuarters` retain
precise decoder timings, tuplets and pickups. For repeat-aware playback, supply
`ScoreMoment` entries with expanded start times but the **same notated IDs** on
each repeat pass. `ScoreDocument` validates timeline references against the model.

## Two layout modes

```kotlin
HorizontalScore(state) // Continuous strip, pans horizontally to the end
VerticalScore(state)   // Fits whole measures into viewport-width systems, stacks systems
// Equivalent lower-level configuration:
ScoreComposable(state, style = ScoreStyle(layoutMode = ScoreLayoutMode.Vertical))
```

Vertical layout reflows on width and zoom changes. Measures share columns across
tracks; every system stacks the tracks in score order. Hit testing, selection,
playback pointers and follow-scroll use the same wrapped geometry. Minimum
logical system width is 204 dp; at extreme zoom on a narrow device, zoom out or
pan. Very dense bars are compressed to fit a system and may need a wider viewport
or tab-only mode; a full engraving collision solver is not provided.

## Customize selection, active notes and the cursor independently

```kotlin
val style = ScoreStyle(
    selectedHighlight = ScoreHighlightStyle(HighlightShape.Rectangle, Color(0x55347B60)),
    playingHighlight = ScoreHighlightStyle(HighlightShape.BeatColumn, Color(0x443E9DD0)),
    playhead = ScoreHighlightStyle(HighlightShape.VerticalLine, Color.Blue, lineWidth = 2f),
)
ScoreComposable(state, style = style)

state.selectNotes("note-1", "note-2")
state.selectedNoteId = "note-1" // Single-selection shorthand, independent of playback
state.followPlayback = true
state.revealNote("note-1")
state.revealBeat("beat-1")
```

Shapes: `None`, `NoteHead`, `Rectangle`, `VerticalLine`, `BeatColumn`.
For completely custom drawing, `onDrawHighlight` receives a `ScoreHighlight`
(kind, score-space bounds, appearance, stable IDs). This **replaces** built-in
highlight drawing; call `drawScoreHighlight(highlight)` for default rendering
of kinds you do not customize. `onDrawEffect` is an additive articulation hook.
Style also controls paper/ink/colors, line/track spacing, measure width,
notation/tab visibility and tab-only rhythmic notation.

The view supports drag/pinch, wheel/trackpad pan, Ctrl+wheel zoom, arrow-key note
navigation, Escape to clear selection, and +/− zoom. Click to focus the score.
Manual pan disables follow mode; re-enable `state.followPlayback` to resume it.
Next/previous/zoom accessibility actions and score descriptions are supplied;
a full per-note screen-reader tree remains future work.

## Selection events and seeking

```kotlin
ScoreComposable(state, onEvent = { event ->
    when (event) {
        is ScoreEvent.NoteSelected -> {
            val model = state.document.index.notes[event.noteId] // Note + beat/measure/track
            // Seek your player using event.beatId and your own timing/repeat policy.
        }
        ScoreEvent.SelectionCleared -> Unit
        is ScoreEvent.ViewportChanged -> Unit
    }
})
```

Staff heads, fret numbers and tab-only rhythm heads are selectable. IDs are
globally unique. Selection state can hold multiple note IDs, while default pointer
clicks select one. Replacement documents prune removed selections and reset the
playback snapshot; the host should resend its current position after an edit.

## Run the Guitar Pro / playback sample

Prerequisites: JDK 17, Android SDK 35 for Android targets; macOS/Xcode for iOS.
**No Python installation or external decoder is required by the apps.**

```sh
./gradlew :sample:run                 # Desktop
./gradlew :sample:assembleDebug       # Android APK
```

- **More → Load GP file** opens a Desktop chooser or Android's Storage Access Framework
  picker. The sample decodes classic **GP1–GP5** (`.gtp`, `.gp3`, `.gp4`, `.gp5`)
  with the actual Java readers from TuxGuitar, off the UI thread. GPX/GP6–8 are
  explicitly rejected. Input is bounded to 16 MiB; errors do not replace the score.
- **More → Load requested tab** retrieves the supplied
  [Nothing Else Matters GP4](https://gprotab.net/en/tabs/metallica/nothing-else-matters?download).
  If the host blocks downloads, save the link and use **Load GP file**.
- The same Java MIDI exporter expands repeats/alternate endings and embedded
  tempo changes. The Kotlin adapter uses TuxGuitar's matching repeat controller
  to map those passes back to stable notated beat/note IDs.
- **Desktop** uses JavaSound's sequencer tick position and General MIDI synth.
  CI runs with explicit **silent cursor preview** when audio is disabled.
- **Android** uses native `MediaPlayer` SMF/General MIDI playback. Its actual
  `currentPosition` is converted through the exported MIDI's tempo map to drive
  the cursor. Play/Pause/Stop, seek-slider and note-click seeking are available.
  Audio focus loss and leaving the activity pause playback; destruction releases
  the player and removes cached MIDI. No storage permission is needed. Playback
  is foreground-only, not a background media service. Devices missing a MIDI
  codec show an audio-unavailable message, not a fake playing clock.
- The score fills the screen, with only playback, scrubber and **More** in a
  compact bottom bar. **More → Settings** contains track selection, layout,
  notation/tab, zoom, theme and highlight controls. Song/file details and player
  diagnostics are there too, rather than occupying the score viewport.
- Hammer-on/pull-off arcs and bend arrows are anchored to the affected tab fret,
  not the top of the staff. Connected legato runs use a single slur; optional H/P
  labels can be enabled. Bends use taller curves and filled arrows, with decoded
  hold/release information and zigzag vibrato. Ghost frets use `(5)`, natural
  harmonics `<12>`, and other harmonics a diamond prefix plus a type label.
  `Effect.DeadNote` and `Effect.Slap` render percussive string hits as `x`.
  `Beat.strum` draws up/down arrows beside chords. The sample preserves these
  flags from actual TuxGuitar decoding.
- Tab-only rhythm defaults to **stem-and-beam guides without oval note heads**,
  below the strings. `ScoreStyle.tabRhythmStyle = TabRhythmStyle.Notes` restores
  the previous note-headed style; `showTabRhythm = false` hides the guide.
  This changes tab-only rhythm, not normal staff notation.
- Tab height is adjustable independently of notation: `tabStringSpacing` defaults
  to 18 logical units and `tabSystemSpacing` to 52. **More → Settings** exposes both
  sliders, rhythm style/visibility and optional H/P labels. Tab-only systems no
  longer retain the notation system's large minimum height. Hit tests and cursor
  bounds use the same spacing, including nearest-string selection in tight tabs.
- Playback-follow pans animate the actual viewport over 280 ms, retaining correct
  culling/hit testing throughout. Dragging/wheeling interrupts follow; re-enable
  **More → Follow cursor** to resume. The host still owns the playback clock.
  The sample is not a physical guitar synthesizer or full GP articulation editor.

Optional preparation (standard Python downloader only, **no pip dependencies**):

```sh
python3 sample/tools/prepare_gp4.py --fetch
# Downloads into ignored sample/local/nothing-else-matters.gp4, auto-loaded on Desktop.
./gradlew :sample:run --args="--gp4=/absolute/path/to/song.gp5"
```

The copyrighted tab is **not bundled or committed**. Both Android and Desktop
initially decode a bundled, original GP4 exercise in Java. iOS retains its
predecoded JSON/UI-only exercise; this sample-only JVM decoder/player is not
advertised as an iOS implementation. Replace `SampleServices` for other platforms.
TuxGuitar's selected Java sources, pin, LGPL 2.1 license and rebuild/relinking
instructions are in [`sample-tuxguitar`](sample-tuxguitar/README.md).

## Modules and verification

```text
score-model/   Kotlin values, ScoreAdapter, ScoreDocument, ScoreIndex, ScoreTimeline
score-ui/      Reactive state, horizontal/wrapped layout, Canvas, highlights, input
sample/        Consumer UI, JVM Kotlin adapter, Android/Desktop pickers and players
sample-tuxguitar/  Sample-only pinned Java GP readers, models and MIDI exporter (LGPL 2.1)
sample/tools/  Optional fixture generator, legacy JSON exporter and downloader
```

```sh
./gradlew :score-model:desktopTest :score-ui:desktopTest
./gradlew :sample:desktopTest
./gradlew :sample:connectedDebugAndroidTest  # emulator/device
./gradlew :sample:assembleDebug
./gradlew :sample:createDistributable
./gradlew :sample:linkDebugFrameworkIosSimulatorArm64  # macOS
```

[GitHub Actions](https://github.com/romankolov/compose-tab/actions/workflows/build.yml)
runs unit/Compose pointer-keyboard tests, Java GP3/4/5 and repeat/tempo/MIDI tests, attempts the exact
requested download, builds Android/Desktop, launches Desktop and its packaged
binary under Xvfb, captures both layout modes and a **moving** cursor, and links
an iOS simulator framework on macOS. External-host download failures are explicit
warnings, not mistaken for successful verification of that song. Inspect the run
logs and screenshot title to distinguish requested-tab vs original-fixture runs.
A separate Android emulator job exercises native MIDI clock/play/pause/seek/stop
and imports the real original fixture through the system document picker, capturing
the playing app. This does not claim physical-device or audible listening tests.

Screenshots are real captures, not generated mockups:
[vertical](docs/screenshots/both.png) · [horizontal](docs/screenshots/horizontal.png) ·
[playing cursor](docs/screenshots/playing.png) · [selection](docs/screenshots/selected.png) ·
[Android import/playback](docs/screenshots/android-playing.png) ·
[tab articulations](docs/screenshots/effects.png).

## Performance and extension boundaries

Layout is memoized independently of selection and clock ticks. Canvas binary
searches sorted measure bounds within vertically visible systems and draws only
visible measures. Text layouts are cached. Playback/selection do not rebuild the
score layout. ID indexes are constructed once per document/layout. Timing lookup
uses a binary start index and prefix end bounds to retain overlapping notes.
Whole-score layout/document construction is still synchronous: adapt large
scores off the UI thread and profile very large documents. Static bitmap tile
caching is an optional optimization, not an implemented or mandatory cache.

This is a usable interactive score/tab UI, **not a notation-complete publication
engraver**. Known engraving limits include upward stems, no key
signatures, full tuplet brackets, slur/tie engraving, printing/pagination, or
collision resolution for dense chords/voices. Exact timing is preserved even when
an articulation/engraving is simplified. Sample warnings identify simplified
effects. Jump signs beyond ordinary repeat/alternate endings are not implemented
in the sample MIDI exporter. Zoom/selection are not process-restoration state;
the host can persist them. Review accessibility/device testing before a public app.

## Consumption and licensing

Use `implementation(project(":score-ui"))`, or publish library modules with
`maven-publish`, group `dev.composetab`, version `0.3.0`. No Maven Central upload
is claimed. Publish all needed KMP/platform publications together before using
`implementation("dev.composetab:score-ui:0.3.0")` from another project.

```sh
./gradlew :score-model:publishToMavenLocal :score-ui:publishToMavenLocal # macOS + SDK/Xcode
```

TuxGuitar Java sources are used **only by the sample**, under LGPL 2.1; the
score libraries remain decoder-independent. Bravura is bundled under SIL OFL.
PyGuitarPro (LGPL-3.0) and mido (MIT) are optional developer fixture tools. See [third-party notices](THIRD_PARTY_NOTICES.md).

The verified-preview shipping workflow in `.github/workflows/release-preview.yml`
accepts a successful build run from the same branch and attaches **those exact
artifacts** to a preview tag. It does not merge the PR or publish to mobile stores.

Clefs: `Track.clef` supports treble, octave-transposing guitar treble (`Treble8vb`), and bass. Set `staffStep` from written pitch for transposing clefs; sounding MIDI is unchanged. The sample maps guitar to written octave-up treble and low four-string instruments to bass, and layout leaves extra room for low ledger lines before tablature.

### Optional custom JSON parser

The separate [`score-json`](docs/custom-json.md) multiplatform module reads custom track JSON
with tuning, measures, voices, rational timing and zero-based string/fret data, then adapts it
to `ScoreData`. It preserves the source metadata and reports unsupported engraving without
adding decoding dependencies to `score-ui`. See the guide for integration and limitations.

### Ties and grace notes

Use `Note(tieFrom = sourceNoteId, ...)` for an incoming same-pitch/string sustain.
The Canvas renders ties across measures and wrapped systems in staff notation and tab.
Use `Beat(grace = GraceKind.BeforeBeat, ...)` (slashed) or `GraceKind.OnBeat` (unslashed)
for small, individually selectable ornaments. `metricalDurationQuarters` is zero for
grace beats; their nominal `durationQuarters` still controls engraving. The default
`ScoreTimeline` excludes grace beats; hosts can supply an explicit performance timeline.
The optional JSON adapter maps `tie` and `graceNote` into these decoder-independent fields.

### Vertical TAB clef and hollow capsule cursor

The TAB label uses stacked uppercase letters spanning the strings, centered on the tab height.
Its dedicated inset lane clears start-repeat bars/dots at every custom string spacing.
Choose **More → Settings → Cursor → Capsule** in the sample, or configure:

```kotlin
ScoreStyle(playhead = ScoreHighlightStyle(HighlightShape.Capsule, color = Color(0xFF2482B0), lineWidth = 2f))
```

The capsule is an outline only. It encloses the active beat's notes/chord across the visible
staves and stays on that beat during sustain (the existing line cursor still interpolates).
It works with beat positions or just playing note IDs. Existing filled playing highlights are
independent; use `playingHighlight = ScoreHighlightStyle(HighlightShape.None)` for outline only.
