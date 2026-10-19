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

## Code review

- Reopening the same generator screen used a permanently closed preview. Removal now drops the preview instance, and initialization configures a fresh one even when the catalog is unchanged. A client regression waits for a newly uploaded preview after returning from a child screen.
- Reinitialization replaced the search box with an empty one despite the existing promise to retain the search. Its value is now restored; the same client regression checks it.
- A glint export that failed while writing its CSS left the earlier temporary JSON mapping behind. Both mapping writes now sit inside their cleanup scope. A client regression blocks the CSS path with a directory and checks the failure, released export slot and absence of temporary files.

Judgment calls left unchanged: most screen labels are English literals rather than translation keys; the two keybinding labels do have translations. Export publication replaces several files individually, so an I/O error between renames can leave mixed old/new outputs. Abrupt client shutdown does not explicitly cancel queued export or skin work. Those require a transactional-output or cancellation design rather than a local error-message change.

## Running the suite

```sh
heavy --mem 8G -- env ALSOFT_DRIVERS=null ./gradlew --no-daemon build runAllGameTests --continue -Pfabricmoddingconventions.devClientOptions=/nonexistent
```

The 2026-10-19 run on `thomas-server` passed both loader builds, all 11 unit tests (layout/sampling 4, mappings 2, image writers 3, commands 2) and the Fabric production client GameTest. NeoForge server GameTests were skipped as configured; no NeoForge client run is claimed. Checkstyle and Spotless checks were skipped by the default quality settings.
