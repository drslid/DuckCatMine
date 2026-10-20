FROM node:22-bookworm-slim AS interface
WORKDIR /build/client
COPY client/package.json client/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY client/ ./
RUN npm run build

FROM node:22-bookworm-slim

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY server.js ./server.js
COPY runtime.js ./runtime.js
COPY items.json ./items.json
COPY public ./public
COPY --from=interface /build/public/v2 ./public/v2

ENV NODE_ENV=production PORT=3000
EXPOSE 3000
USER 1001:1001
CMD ["node", "server.js"]
