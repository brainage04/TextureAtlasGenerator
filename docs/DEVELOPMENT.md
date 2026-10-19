# Development

## Test pruning rubric

Frozen before reviewing candidates on 2026-10-19:

- Keep: exercises production behaviour with an observable result or guards a real regression. Broad integration tests are not redundant merely because they share helpers.
- Redundant: adds no distinct behaviour beyond another existing named test; identify that covering test.
- Tautological: only tests a copy of the implementation, mock echo, wiring or a constant without an observable production behaviour.

The initial inventory contains one Fabric client GameTest entrypoint, `TextureAtlasGeneratorClientGameTest.runTest`, and no unit or server GameTests. The typed judge classified it as keep (confidence 1.0): it drives the generator and validates exported files. No tests were flagged, re-read for removal or removed. Helpers are part of that single scenario, not separate test cases.
