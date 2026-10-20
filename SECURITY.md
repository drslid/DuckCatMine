# Security

DuckCatMine is an early self-hosted application for trusted administrators. It has not received an independent security audit.

Do not post passwords, tokens, activation links, world archives, authentication files or unsanitized private logs in public issues. Report non-sensitive defects through GitHub Issues. For a sensitive vulnerability, use the repository's private vulnerability reporting feature if enabled; otherwise contact the maintainer through the public profile without including exploit secrets in the first message.

## Trust boundaries

- Caddy terminates TLS and asks the API to authorize map requests.
- RCON is internal. The panel can execute powerful game commands.
- All web accounts have full administrative permissions. They are separate from the game's OP list.
- Only the maintenance worker mounts the Docker socket. This access effectively trusts the worker with the host.
- Uploaded schematics and backup restores modify the world. Keep off-host backups and verify file origins.
- noindex discourages indexing; authentication protects private content.

## Revoking an account in this alpha

Stop the panel, back up `admin/state/users.json`, remove the intended account entry, then start the panel. Restarting clears in-memory sessions. Review and remove that account's push subscriptions if needed. Do not remove unrelated accounts or publish the file.

## Updates

The first alpha has no automatic updater. Track release notes, back up, test separately and plan migrations. Do not change the Minecraft version without checking world compatibility.
