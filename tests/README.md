# Isolated integration checks

These scripts deliberately change, back up and restore a disposable world. They refuse any panel whose `PUBLIC_ORIGIN` is not `http://127.0.0.1:18080`. Use a fresh installation made with `scripts/setup.py --local`, never a production project.

The main check is documented in the root README. To exercise the extension manager too, compile the original, harmless Java fixture using the test server's cached Paper API:

```bash
docker run --rm --user "$(id -u):$(id -g)" \
  -v "$PWD/data/libraries:/libraries:ro" \
  -v "$PWD/tests/plugin-fixture:/fixture" \
  -v "$PWD/admin/jobs:/out" -w /fixture \
  maven:3.9.11-eclipse-temurin-25 sh -c \
  'mkdir -p classes; javac -cp "$(find /libraries -name "*.jar" | paste -sd :)" -d classes DCTestPlugin.java; cp plugin.yml classes/; jar cf /out/plugin-fixture.jar -C classes .'

docker compose exec -T panel node - < tests/plugins-integration.cjs
```

The check uses the real Modrinth catalogue, verifies a real download, cancels it, streams the fixture upload, confirms that staging leaves the running server untouched, cancels and imports again, then activates it with a backed-up restart. It checks Paper's `onEnable` log and restores the preceding backup, confirming that the added plugin disappears and the core plugins remain. A temporary web account is removed in `finally`.

Unit tests cover incompatible archives, protected core names, missing dependencies, unexpected changes to an installed JAR, rollback after a failed copy, authentication/CSRF, transfer verification, and health indicator thresholds. Run those without a Minecraft server using the root README commands.
