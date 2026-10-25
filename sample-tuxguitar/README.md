# Sample-only TuxGuitar Java sources

Unmodified production Java sources from TuxGuitar's `TuxGuitar-lib`,
`TuxGuitar-gtp`, `TuxGuitar-gm-utils`, and `TuxGuitar-midi` modules:
https://github.com/helge17/tuxguitar/tree/b4feea4fe2f0155d648a37282ec57f1ecb329a35/common

Pinned upstream commit: `b4feea4fe2f0155d648a37282ec57f1ecb329a35`.
Copyright TuxGuitar authors/contributors. Upstream distributes these sources under
GNU LGPL 2.1 (see LICENSE, copied verbatim from upstream docs/LICENSE).
Original source headers are retained. The Gradle build and this notice are new.
This module is only a dependency of the sample's Android/Desktop implementations;
neither score-model nor score-ui depends on it. Classic GP1–GP5 readers and the
platform-independent MIDI exporter are used; no Python or TuxGuitar UI is needed.

Corresponding source and build scripts are supplied in this repository. When
redistributing binaries, retain this license/notice and provide the corresponding
source and relinking/rebuilding materials required by LGPL 2.1. Build the replaceable
Java dependency with `./gradlew :sample-tuxguitar:jar :sample-tuxguitar:sourcesJar`,
then rebuild the sample with `:sample:assembleDebug` or `:sample:createDistributable`.
