# Third-party assets

The music font in `score-ui/src/commonMain/composeResources/font/bravura.otf`
is **Bravura** by Steinberg Media Technologies GmbH, distributed under the
SIL Open Font License, Version 1.1. The full copyright and license text is
bundled at `score-ui/src/commonMain/composeResources/files/Bravura-LICENSE.txt`.

Source: https://github.com/steinbergmedia/bravura/tree/master/redist/otf

No TuxGuitar source or decoder implementation is bundled. Its object hierarchy
is a conceptual reference only.

## Sample decoder / MIDI export

The Desktop sample invokes the separately installed **PyGuitarPro 0.11** parser
(LGPL-3.0) and **mido 1.3.3** (MIT) through a Python process. No implementation
code from either is copied into the library. Install via
`sample/tools/requirements.txt`; their distributions include their licenses.
Sources: https://github.com/Perlence/PyGuitarPro and https://github.com/mido/mido.

The bundled `original-study.gp4`/JSON is an original generated exercise, produced
by `sample/tools/prepare_gp4.py`. The user-requested Metallica tab is downloaded
only on explicit user action or in CI into ignored `sample/local/`; it is not
committed or redistributed inside the library or sample binary. Respect the
source site's and rights holders' terms when using downloaded tabs.
