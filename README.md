# Compose Tab · 0.3.0

A UI-only Compose Multiplatform library for interactive music scores and guitar
 tablature on **Android, iOS and Desktop**. The library has **no decoder, player,
 audio device, file I/O or playback clock**. The separate sample demonstrates
real GP4 decoding and a Desktop General MIDI player.

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

`staffStep` is diatonic distance above the bottom treble E4 line; MIDI alone does
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

## Run the real GP4 / playback sample

Prerequisites: JDK 17, Android SDK 35 for this build's Android targets; macOS/Xcode
for iOS. **Desktop GP4 import additionally needs Python 3.10+** with the two pinned
sample dependencies. They are intentionally outside the library and Java runtime:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r sample/tools/requirements.txt
COMPOSE_TAB_PYTHON="$PWD/.venv/bin/python" ./gradlew :sample:run
```

Windows: use `.venv\Scripts\python.exe` and set `COMPOSE_TAB_PYTHON` accordingly.
For a packaged Desktop app, set this variable to the **absolute** interpreter path.
It must have `PyGuitarPro==0.11` and `mido==1.3.3` installed. The Python bridge
has a file-size limit, a decoder process timeout, explicit schema validation and
error reporting. It is a real GP4 parser, not an extension/header-only fake.

- **Open GP4** imports a local file using the real parser.
- **Load requested tab** retrieves the supplied
  [Nothing Else Matters GP4](https://gprotab.net/en/tabs/metallica/nothing-else-matters?download),
  decodes it off the UI thread, and displays its pitched tracks. If the source host
  blocks a download, save that link in your browser and use **Open GP4**.
- Play/Pause/Stop and scrubbing use Java MIDI's actual sequencer tick position,
  including embedded tempo changes. The generated MIDI expands repeat/alternate
  ending passes; those passes reference original notated IDs for the UI.
- Choose a track, layout, highlight styles and follow mode in the toolbar.
- Audio uses the operating system's General MIDI synthesizer. When there is no
  audio device (e.g. CI), the UI explicitly says **silent cursor preview**. No
  sound output is claimed in that case. Guitar articulations are simplified in
  playback; this is not a physical guitar synthesizer.

Optional preparation (also used by CI):

```sh
.venv/bin/python sample/tools/prepare_gp4.py --fetch
# Downloads into ignored sample/local/nothing-else-matters.gp4, auto-loaded on Desktop.
./gradlew :sample:run --args="--gp4=/absolute/path/to/song.gp4"
```

The copyrighted tab is **not bundled or committed**. The default distributable
contains a clearly titled, original generated GP4 exercise. Android/iOS show a
predecoded JSON version of that original exercise; **runtime GP4 import and MIDI
playback are Desktop reference features**, not falsely enabled mobile controls.
Replace `SampleServices` with native decoder/audio implementations in your app.

## Modules and verification

```text
score-model/   Kotlin values, ScoreAdapter, ScoreDocument, ScoreIndex, ScoreTimeline
score-ui/      Reactive state, horizontal/wrapped layout, Canvas, highlights, input
sample/        Consumer UI, GP4 wire mapper, Python bridge, Desktop MIDI transport
sample/tools/  Decoder requirements, fixture generator/downloader and integration tests
```

```sh
./gradlew :score-model:desktopTest :score-ui:desktopTest
.venv/bin/python sample/tools/test_gp4.py
./gradlew :sample:assembleDebug
./gradlew :sample:createDistributable
./gradlew :sample:linkDebugFrameworkIosSimulatorArm64  # macOS
```

[GitHub Actions](https://github.com/romankolov/compose-tab/actions/workflows/build.yml)
runs unit/Compose pointer-keyboard tests, Python GP4/MIDI tests, attempts the exact
requested download, builds Android/Desktop, launches Desktop and its packaged
binary under Xvfb, captures both layout modes and a **moving** cursor, and links
an iOS simulator framework on macOS. External-host download failures are explicit
warnings, not mistaken for successful verification of that song. Inspect the run
logs and screenshot title to distinguish requested-tab vs original-fixture runs.
Mobile UI is compiled/linked, not physical-device-tested by this workflow.

Screenshots are real captures, not generated mockups:
[vertical](docs/screenshots/both.png) · [horizontal](docs/screenshots/horizontal.png) ·
[playing cursor](docs/screenshots/playing.png) · [selection](docs/screenshots/selected.png).

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
engraver**. Known engraving limits include upward stems, treble clef only, no key
signatures, full tuplet brackets, slur/tie engraving, printing/pagination, or
collision resolution for dense chords/voices. Exact timing is preserved even when
an articulation/engraving is simplified. GP4 decode warnings surface simplified
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

TuxGuitar is a conceptual object-hierarchy reference; no TuxGuitar source is
copied. Bravura is bundled under SIL OFL. PyGuitarPro is separately installed
under LGPL-3.0 and mido under MIT. See [third-party notices](THIRD_PARTY_NOTICES.md).

The verified-preview shipping workflow in `.github/workflows/release-preview.yml`
accepts a successful build run from the same branch and attaches **those exact
artifacts** to a preview tag. It does not merge the PR or publish to mobile stores.
