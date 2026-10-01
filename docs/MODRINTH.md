# Modrinth publishing

Modrinth publishing is performed by the FabricModdingConventions reusable `release.yml` workflow. It publishes the exact Fabric and NeoForge release JARs prepared from a matching `v<mod_version>` tag; see [RELEASE.md](RELEASE.md).

## Required configuration

Configure these repository settings before publishing:

- `MODRINTH_TOKEN` secret: a Modrinth token permitted to publish versions.
- `MODRINTH_PROJECT_ID` variable: the existing Modrinth project ID for TextureAtlasGenerator.

`.modrinth/project.json` holds the project slug and categories that the publishing tasks read. The NeoForge version declares no extra dependencies, so there is no `.modrinth/neoforge-dependencies.json`.
