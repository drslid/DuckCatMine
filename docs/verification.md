# Verification — v0.2.0-alpha.2

Measurements from 20 October 2026. These are engineering checks, not universal performance promises or provider benchmarks.

## API

- Unit checks: incremental log appends, partial UTF-8 lines, truncation/rotation, bounded partial buffer; shared in-flight status collection and invalidation.
- Smoke checks: activation, authentication, session, CSRF, logout, chat, push subscription validation, catalogs and rejected invalid commands.
- Local warm-cache test: 30 simultaneous status reads, about 15–21 ms total on the development machine, **zero additional RCON commands**. This excludes first collection and network latency.
- On an 8-vCPU/23-GB Ubuntu VPS: 30 simultaneous warm-cache reads inside the panel, median 57 ms and p95 67 ms on one run. First collection approximately 1.5 seconds. These are inside-container measurements, not remote browser timings.
- Shared SSE initial chat/status, item catalog (1657 entries), three defense presets, skin search and hashed frontend assets verified against a running game.

## CPU and memory

A five-second Java-thread sample identified BlueMap rendering as the dominant CPU consumer. A temporary pause changed the observed game-container CPU from about 158% to 40%; after enabling `player-render-limit: 1`, a later sample with one player was about 47%. Docker CPU percentages refer to cores (100% = one core).

These point samples were not a controlled before/after workload experiment. They identify a practical optimization but do not promise a fixed reduction.

The panel was observed around 20–23 MiB resident container memory and roughly 0.1% CPU when lightly used. Minecraft remained around 4.8 GiB with a 4-GB heap and its existing extensions. The live game heap was not reduced during a player's session.

## Frontend

- Svelte check: no errors or warnings.
- Initial production JavaScript: approximately 74 KB raw / **29 KB gzip**, excluding CSS and lazy-loaded tools. Build output is the source of these figures.
- Navigation and main workflows checked in Chromium at 320, 390 and 768 px: page width did not exceed the viewport; mobile text inputs remained at least 16 px.
- Fake-data workflows: select player, item search/give, skin preview, map placement drawer/confirmation, chat send and unread UI.
- This is not a physical iPhone or Safari/WebKit certification. Test push and safe-area behavior on actual devices before claiming full platform coverage.

## Fresh isolated installation

The installer was run in a new Linux directory on the same VPS, using the Small profile and independent loopback-only ports/world/state. It built both original Java plugins and the panel, downloaded hash-verified third-party plugins and generated local thumbnails. No production world files or credentials were copied.

`tests/integration.cjs` verified:

- Reverse proxy and panel startup.
- Map access: 401 without authentication, 200 with a session.
- Map data, item image, catalog and initial SSE event.
- Terrain check, tower placement and automatic shots against a test monster in forced-loaded test chunks.
- Worker map-policy job.
- Real backup, streamed archive download, restore and Minecraft returning online.
- All five plugins and the map returning after restore; panel upload directories remaining writable.

The restore exercise exposed the backup tool's default exclusion of JAR files. The stack now explicitly includes plugin binaries in backups and refuses older incomplete archives before stopping the game. Restore preserves the directories bind-mounted into the panel. Regression checks cover both behaviors. Six worker tests pass.

This isolated test used HTTP on loopback. The release bootstrap is also checked with an existing Docker Engine and a fresh directory, without sudo. It did not request a certificate for a new public domain or change the existing DuckDNS record. The existing live panel uses HTTPS. Fresh-host Docker installation, real DuckDNS-token renewal, provider cloud-init and ARM hosts remain separate deployment tests; do not claim they were exercised by this run.

## Reproduce

Use a new disposable Linux directory and follow the development section of the README. The integration script explicitly requires the isolated localhost origin and changes its world. Stop it with `docker compose down` afterwards; preserve artifacts if diagnosing a failed check.
