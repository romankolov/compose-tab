# Custom track JSON → Compose Tab

`score-json` is an optional, pure Kotlin Multiplatform module (Android, iOS and JVM).
It depends on `score-model` and kotlinx.serialization JSON, **not Compose or the player**.
Neither `score-model` nor `score-ui` acquires a parser dependency. Fetching text, choosing a file,
threading, caching and audio remain the host application's responsibility.

```kotlin
// commonMain.dependencies { implementation(project(":score-json")) }
import dev.composetab.json.*
import dev.composetab.ui.*

val document = TabJsonParser().parse(jsonText)
val result = TabScoreAdapter.adapt(document, TabAdapterOptions(
    title = "My song", idPrefix = "lead-guitar"
))
val state = ScoreState(result.score)
// In your composable: ScoreComposable(state)
// result.warnings: unsupported engraving with source JSON paths
// result.locations[event.noteId]: source measure / voice / beat / note indices
// result.document.source: complete original JSON including all extensions
```

For a minimal integration: `ScoreState(TabJsonParser().parse(jsonText).toScoreData())`.
Prefer the result API when you want diagnostics and source mapping. Catch `TabParseException`;
its `path` identifies invalid structure, types, tuning, pitch, chord or duration data.
Run parsing off the UI thread for large inputs. Defaults limit text to 8 Mi characters and
200,000 beats and 128 nesting levels; override with `TabParseLimits`. JSON syntax is strict; unknown fields are retained.
The source model types are `TabDocument`, `TabMeasure`, `TabVoice`, `TabBeat`, `TabNote`.

## Source contract

One document describes **one track**, not a whole multitrack song:

```json
{
  "name": "Original exercise",
  "strings": 6,
  "tuning": [64, 59, 55, 50, 45, 40],
  "measures": [{
    "signature": [4, 4],
    "voices": [{"beats": [
      {"type": 8, "duration": [1, 8], "notes": [{"string": 0, "fret": 5, "hp": true}]},
      {"type": 8, "duration": [1, 8], "notes": [{"string": 0, "fret": 7}]},
      {"type": 4, "rest": true, "notes": [{"rest": true}]}
    ]}]
  }]
}
```

- `tuning` is required: highest string first, sounding MIDI pitches. `string` is zero-based
  in JSON and converted to the renderer's one-based convention. No pitch clamping.
- Signature changes carry forward; absent initial signature means 4/4.
- `duration: [n,d]` is a rational fraction of a whole note: quarter duration = `4*n/d`.
  It is authoritative over `type`, dots and tuplets. Missing duration uses `type` and dots;
  tuplets require explicit rational duration rather than a guessed ratio.
- Each voice starts at offset zero independently. All voices map to beats with explicit
  `offsetQuarters`; they are not appended sequentially. Ordering and IDs are deterministic.
- `type` selects engraving, `durationScale` preserves tuplets, multiple dots, 64ths and 128ths.
  The renderer currently has no separate 64th/128th flag glyphs or tuplet numerals.
- Guitar notation defaults to `Treble8vb`; MIDI pitch is unchanged, written staff pitch is
  one octave higher. Sharps are used for chromatic pitches (not key-aware spelling).
- Supported effects: `dead`, `ghost`, `vibrato`, `palmMute`, named harmonic kinds and
  outgoing `hp` (direction determined by the following same-string fret). Up/down strokes
  are mapped from beat `upStroke` / `downStroke` fields.
- Grace beats remain parsed but are omitted from score timing and drawing with a warning.
  Ties remain separate source notes, not merged or engraved; they produce warnings.
- Bends/slides/slaps, staccato, text/chords, let-ring and tuplet captions currently remain
  in source metadata with warnings rather than inventing effect semantics/units.
- Automations, repeat metadata, markers, instrument data, sound changes, beam hints and
  unknown extensions remain accessible in `source`. They do **not** drive playback or
  repeat expansion; this is a rendering adapter, not a player. Repeats are not yet mapped.
- The parser is structurally strict but extension-tolerant; it is not a schema validator for
  every vendor extension. The adapter validates the supported effect flags it reads.
- Use distinct `idPrefix` values if combining independently adapted tracks into one score.

## Verification against the requested documents

CI downloads both complete user-provided CloudFront JSON files into temporary storage,
then runs two JVM contract tests alongside offline common tests. Full song transcriptions
are not committed, cached in application resources, or redistributed. External-host failures
fail the contract step explicitly instead of claiming successful validation.

Sources:
- https://dqsljvtekg760.cloudfront.net/439/9821972/v0-3-2-ZFupge6YADvcqilc/3.json
- https://dqsljvtekg760.cloudfront.net/523530/523926/v2-34-0-uILv-D-NdsCuc2xn/0.json

Offline: `./gradlew :score-json:desktopTest`.
For live tests, download them as `track-1.json` and `track-2.json` into a directory and set
`COMPOSE_TAB_JSON_SAMPLES=/absolute/path` when running that task. Live tests are opt-in locally.
