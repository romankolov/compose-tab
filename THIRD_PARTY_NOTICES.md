# Third-party assets

The music font in `score-ui/src/commonMain/composeResources/font/bravura.otf`
is **Bravura** by Steinberg Media Technologies GmbH, distributed under the
SIL Open Font License, Version 1.1. The full copyright and license text is
bundled at `score-ui/src/commonMain/composeResources/files/Bravura-LICENSE.txt`.

Source: https://github.com/steinbergmedia/bravura/tree/master/redist/otf

## Sample-only Java decoder / MIDI export

Selected, unmodified **TuxGuitar** Java sources are vendored in `sample-tuxguitar`,
from commit `b4feea4fe2f0155d648a37282ec57f1ecb329a35` of
https://github.com/helge17/tuxguitar. Copyright TuxGuitar authors/contributors.
Upstream's GNU LGPL 2.1 text is retained at `sample-tuxguitar/LICENSE`;
provenance, scope and corresponding-source/rebuild instructions are in that module's
README. The score libraries have no dependency on this module. No upstream UI or
native audio engine is used; Android and Desktop use their platform MIDI players.

Optional **developer-only** fixture/legacy JSON export tools use **PyGuitarPro
0.11** (LGPL-3.0) and **mido 1.3.3** (MIT). These are not packaged or invoked by
the apps or required for CI decoding. Their distributions include their licenses.
Sources: https://github.com/Perlence/PyGuitarPro and https://github.com/mido/mido.

The bundled `original-study.gp4`/JSON is an original generated exercise, produced
by `sample/tools/prepare_gp4.py`. The user-requested Metallica tab is downloaded
only on explicit user action or in CI into ignored `sample/local/`; it is not
committed or redistributed inside the library or sample binary. Respect the
source site's and rights holders' terms when using downloaded tabs.
