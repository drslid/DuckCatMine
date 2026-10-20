#!/usr/bin/env bash
set -euo pipefail
# Stable release bootstrap. No world or credential is shipped in this archive.
VERSION="v0.2.0-alpha.1"
DEST="${DUCKCATMINE_DIR:-/opt/duckcatmine}"
if [[ $(id -u) != 0 ]]; then
  echo 'Run with sudo: curl -fsSL https://raw.githubusercontent.com/drslid/DuckCatMine/v0.2.0-alpha.1/install.sh | sudo bash'
  exit 1
fi
for tool in curl tar python3; do
  command -v "$tool" >/dev/null || { echo "Missing requirement: $tool (Ubuntu/Debian: apt install curl python3 ca-certificates)"; exit 1; }
done
if [[ -e "$DEST/.env" || -d "$DEST/data" ]]; then
  echo "An installation already exists in $DEST. Nothing has been overwritten. See the update guide."
  exit 1
fi
if ! command -v docker >/dev/null; then
  printf 'Docker is missing. Install Docker Engine from get.docker.com? [yes/no] ' >/dev/tty
  read -r answer </dev/tty
  [[ "$answer" == yes ]] || exit 1
  installer=$(mktemp)
  curl -fsSL https://get.docker.com -o "$installer"
  sh "$installer"
  rm -f "$installer"
fi
docker compose version >/dev/null
temp=$(mktemp -d)
trap 'rm -rf -- "$temp"' EXIT
curl -fsSL "https://github.com/drslid/DuckCatMine/archive/refs/tags/$VERSION.tar.gz" -o "$temp/source.tar.gz"
mkdir -p "$DEST"
tar -xzf "$temp/source.tar.gz" -C "$DEST" --strip-components=1
python3 "$DEST/scripts/setup.py" "$@"
