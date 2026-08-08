# Third-party assets & licenses

## Bundled map label fonts (glyph PBFs)

Location in this repo: `app/src/main/assets/map/glyphs/`

The app bundles pre-rendered SDF glyph sets (`.pbf`) so that map labels render
fully offline, without contacting any third-party font server.

- **Font:** Noto Sans (Regular and Bold), rebuilt by OpenMapTiles as the
  "Klokantech Noto Sans" glyph set.
- **Copyright:** Noto fonts © Google Inc.
- **License:** SIL Open Font License (OFL), Version 1.1.
- **Source of the pre-built glyphs:** https://github.com/openmaptiles/fonts
  (gh-pages), ranges `0-255` and `256-511`.
- **License text:** the full OFL 1.1 is bundled alongside the glyphs at
  `app/src/main/assets/map/glyphs/LICENSE.txt` and ships inside the APK.

Redistribution note: the OFL 1.1 explicitly permits bundling, embedding and
redistributing the fonts (and format-converted derivatives such as these SDF
glyph PBFs) with software, provided the fonts are not sold on their own, the
license text accompanies them, and the reserved font name is respected. The
"Klokantech Noto Sans" build already renames away from the reserved "Noto"
name, so this bundling is compliant.
