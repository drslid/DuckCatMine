#!/usr/bin/env bash
set -euo pipefail
# Stable release bootstrap. No world or credential is shipped in this archive.
VERSION="v0.2.0-alpha.5"
DEST="${DUCKCATMINE_DIR:-/opt/duckcatmine}"
for tool in curl tar python3; do
  command -v "$tool" >/dev/null || { echo "Missing requirement: $tool (Ubuntu/Debian: apt install curl python3 ca-certificates)"; exit 1; }
done
if [[ -e "$DEST/.env" || -d "$DEST/data" ]]; then
  echo "An installation already exists in $DEST. Nothing has been overwritten. See the update guide."
  exit 1
fi
if ! command -v docker >/dev/null; then
  if [[ $(id -u) != 0 ]]; then
    echo 'Docker installation requires sudo. Run this installer with sudo, or use an account with Docker already configured.'
    exit 1
  fi
  printf 'Docker is missing. Install Docker Engine from get.docker.com? [yes/no] ' >/dev/tty
  read -r answer </dev/tty
  [[ "$answer" == yes ]] || exit 1
  installer=$(mktemp)
  curl -fsSL https://get.docker.com -o "$installer"
  sh "$installer"
  rm -f "$installer"
fi
docker compose version >/dev/null
docker info >/dev/null
temp=$(mktemp -d)
trap 'rm -rf -- "$temp"' EXIT
curl -fsSL "https://github.com/drslid/DuckCatMine/archive/refs/tags/$VERSION.tar.gz" -o "$temp/source.tar.gz"
mkdir -p "$DEST" || { echo 'Choose a writable path with DUCKCATMINE_DIR or run with sudo.'; exit 1; }
tar -xzf "$temp/source.tar.gz" -C "$DEST" --strip-components=1
python3 "$DEST/scripts/setup.py" "$@"
