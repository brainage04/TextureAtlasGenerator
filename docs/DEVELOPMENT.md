# Development

## Test pruning rubric

Frozen before reviewing candidates on 2026-10-19:

- Keep: exercises production behaviour with an observable result or guards a real regression. Broad integration tests are not redundant merely because they share helpers.
- Redundant: adds no distinct behaviour beyond another existing named test; identify that covering test.
- Tautological: only tests a copy of the implementation, mock echo, wiring or a constant without an observable production behaviour.

The initial inventory contains one Fabric client GameTest entrypoint, `TextureAtlasGeneratorClientGameTest.runTest`, and no unit or server GameTests. The typed judge classified it as keep (confidence 1.0): it drives the generator and validates exported files. No tests were flagged, re-read for removal or removed. Helpers are part of that single scenario, not separate test cases.

## Coverage review

Player-visible gaps found after pruning:

- Export image pixels were never decoded. Unit tests now round-trip streamed PNG and lossless WebP through independent ImageIO decoders, with transparency, palettes, gradients, noise, varied dimensions and multiple PNG bands.
- Layout testing only checked the largest vanilla layout. Unit tests now cover every supported cell size, page-boundary coordinates, final-page dimensions and matching JSON, text and CSS mappings.
- Small-icon alpha-weighted downsampling and bottom-up GPU readback had no focused tests. Both now have fixed pixel fixtures.
- Commands only exercised `/atlas`, vanilla and glint in the client. Unit tests now check the shared command parser's types and size limits; the client scenario also opens with `G`, rejects invalid typed sizes, accepts non-power-of-two sizes, checks all four potion-bearing item types and compares repeated exports byte for byte.

Remaining gaps: NeoForge has no client GameTest runner in the current conventions, so its keybinding, Mods screen entry and event lifecycle are reviewed but not exercised by this suite. Dedicated-server tests are intentionally disabled for this client-only mod. There are no gamerules or persistent settings. Real SkyBlock download failures, resource-pack reloads, maximum-size GPU rendering and abrupt client shutdown during an export need a client-specific integration harness; the suite avoids depending on the public skin service.
