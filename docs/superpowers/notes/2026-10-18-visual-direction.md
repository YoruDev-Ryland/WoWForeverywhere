# Visual direction: "Brass on stone" (pending user approval)

Look book (mockups of home, addons, install step): https://claude.ai/artifact/6KP3r7VRsswMKYhaTq37rD

- Palette: Ground #14150F, Stone #1F2118, Stone-2 #292C20, Rule #3A3D2E, Bone #E6E0CF, Ash #A39E8C, Brass #D39B3F (only accent), status Ok #8FB26A / Warn #D9774A.
- Type: IM Fell English SC (titles and tile names only), Atkinson Hyperlegible (body; legibility on 6" screens), IBM Plex Mono (versions, sizes, progress). All OFL, bundled in res/font/fw_*.ttf.
- Surfaces flat, 1dp hairlines, corners 2dp max, no shadows or gradients.
- Focus and selection: a 3dp brass outline (`Modifier.focusRing()`).
- Flavor marks: Forever = solid brass square with "F"; Classic = open ash circle with "C". The shape carries the meaning, not only the colour.
- Tokens: `app/src/main/java/app/gamenative/wow/ui/theme/ForeverywhereTheme.kt`.
