# WebPluginV2

WebPluginV2 is a source-available Minecraft plugin that provides an integrated web server for server environments. It includes HTML hosting and optional PHP/NGINX runtime support for supported platforms.

> Project status: this codebase is in cleanup/maintenance mode. The first priority is to make the build reproducible, reduce duplicated platform-specific code, and harden the embedded web server paths before larger feature work.

## Supported platforms in the codebase

- Bukkit/Paper/Folia-style servers through `plugin.yml`
- BungeeCord through `bungee.yml`
- Velocity through the Velocity `@Plugin` annotation

## Requirements

- Java JDK suitable for your target Minecraft server version
- Maven 3.9+
- A Minecraft server/proxy matching the API dependencies in `pom.xml`

## Build

```bash
mvn clean package
```

The compiled plugin jar is written to `target/WebPlugin-<version>.jar`.

For local development, you can optionally copy the built jar to a server plugin folder during `mvn install`:

```bash
mvn clean install -Dplugin.copy.dir=/path/to/server/plugins
```

## Development workflow

1. Create a branch from `main` or `master`.
2. Make one focused change at a time.
3. Run `mvn -B -DskipTests package` before opening a pull request.
4. Test on the platform touched by the change: Bukkit/Paper, BungeeCord, and/or Velocity.
5. Update `CHANGELOG.md` for user-facing changes.

See [`docs/VERSION_CONTROL.md`](docs/VERSION_CONTROL.md) for a suggested Git workflow.

## Configuration

Default config lives in `src/main/resources/config.yml`. Runtime config is created in the plugin data folder on first start.

Important defaults:

- HTML server port: `25567`
- PHP server port: `25568`
- Local PHP-FPM port: `25569`
- PHP support is disabled by default
- SSL support is disabled by default

## Known cleanup areas

See [`docs/PROJECT_AUDIT.md`](docs/PROJECT_AUDIT.md) for the initial audit notes. The highest-impact areas are:

- Replace duplicated Bukkit/Bungee/Velocity web-serving classes with shared services.
- Harden static-file serving against path traversal and missing-file edge cases.
- Replace shell-string process execution with safer `ProcessBuilder` helpers.
- Align runtime paths so plugin folder casing is consistent across Linux and Windows.
- Decide whether the license should be open-source or source-available, then make README and license language match.

## License

See [`LICENSE.md`](LICENSE.md). The current license text is source-available and restricts modification/copying for distribution.
