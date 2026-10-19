# Visual direction

Art-led launcher in WoW Forever's own colours.

- **Art:** home tiles, the boot splash and addon details use WoW Forever promo art from Blizzard's official page. It is loaded at runtime from Blizzard's image CDN and cached (`wow/ui/components/Art.kt`), not bundled. Text sits on a flat translucent band, with no gradient fades.
- **Palette (from the Forever logo):** slate ink `#10171A`, slate stone `#19232A` / `#24323A`, parchment `#EFE6D6`, weathered `#A8A08F`, bronze `#C9A06A` (focus and primary actions), gem teal `#3FA7C2` (Forever-specific accents). Status colours: `#8CC08E`, `#E8925A`.
- **Type:** Sora for titles, Manrope for everything else (OFL, `res/font/fw_*.ttf`).
- **Shapes:** rounded corners (10 dp controls, 16 dp tiles, pill chips).
- **Focus:** a 3 dp bronze ring with a slight lift on tiles. Bronze buttons use a parchment ring instead.
- **Game-version marks:** Blizzard's round WoW Forever and WoW Classic icons, rendered from its icon host with AndroidSVG.
- **Upstream screens:** upstream colour tokens, Material scheme and typography are remapped to this palette, and upstream gradients are flattened.
- **Rule:** no gradients, no blue/purple accents, no accent-bar cards, no emoji icons.

Tokens: `app/src/main/java/app/gamenative/wow/ui/theme/ForeverywhereTheme.kt`.
