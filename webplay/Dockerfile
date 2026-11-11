# Build steps adapted from the pinned minecraft.wasm.click Dockerfile (GPL-3.0).
FROM node:24.22.1-bookworm-slim@sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205 AS build
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends python3 ca-certificates patch && rm -rf /var/lib/apt/lists/*
COPY --from=launcher /package.json /package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts
COPY --from=launcher / .
COPY --from=relay /tcp.mjs /public-target.mjs /transport.mjs ./relay-service/
COPY policy.mjs ./server/duckcat-play.mjs
COPY smoke-test.mjs ./server/duckcat-play.test.mjs
COPY launcher.patch /tmp/launcher.patch
RUN patch -p1 --fuzz=0 < /tmp/launcher.patch && node tools/stage-public.mjs \
    && node --test server/duckcat-play.test.mjs && rm server/duckcat-play.test.mjs

FROM node:24.22.1-bookworm-slim@sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205
ENV NODE_ENV=production HOST=0.0.0.0 PORT=8080
WORKDIR /app
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/package.json ./package.json
COPY --from=build /app/site ./site
COPY --from=build /app/server ./server
COPY --from=build /app/relay-service/access.mjs /app/relay-service/server.mjs /app/relay-service/tcp.mjs /app/relay-service/public-target.mjs /app/relay-service/transport.mjs /app/relay-service/pack.mjs /app/relay-service/pack-node.mjs ./relay-service/
COPY --from=build /app/runtime/tools/profile-property-verifier.mjs ./runtime/tools/profile-property-verifier.mjs
USER node
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s CMD node -e "fetch('http://127.0.0.1:8080/healthz').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "server/app.mjs"]
