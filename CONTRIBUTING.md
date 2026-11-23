# Contributing

Thanks for taking the time to improve WebPluginV2.

## Before you start

Check `docs/PROJECT_AUDIT.md` for known cleanup areas. Prefer small pull requests that solve one specific problem.

## Build check

Run this before submitting changes:

```bash
mvn -B -DskipTests package
```

## Code style

- Use 4 spaces for Java indentation.
- Keep platform-specific code thin. Move shared behavior into reusable services where practical.
- Avoid adding new hard-coded absolute paths.
- Avoid shell command strings when a Java API or `ProcessBuilder` with argument lists can be used.
- Do not log secrets, private keys, tokens, or full config files unless they are redacted.

## Testing expectations

For runtime changes, note which platform was tested:

- Bukkit/Paper/Folia
- BungeeCord
- Velocity
- Linux PHP runtime

## License note

The current license restricts modification/copying for distribution. Make sure the intended contribution model and license are aligned before accepting outside contributions.
