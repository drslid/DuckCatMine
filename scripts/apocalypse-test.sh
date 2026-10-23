#!/usr/bin/env bash
# Disposable Ground Zero test instance next to a DuckCatMine install: its own copy of the
# feat/apocalypse branch, its own world and state, loopback-only ports 18080/18443/25585.
# It refuses to touch any directory that is not such a test instance.
set -euo pipefail

BRANCH="feat/apocalypse"
ARCHIVE="https://github.com/drslid/DuckCatMine/archive/refs/heads/$BRANCH.tar.gz"
ISOLATED='PUBLIC_ORIGIN=http://127.0.0.1:18080'

usage() {
  cat >&2 <<'EOF'
Usage : apocalypse-test.sh install <PseudoMinecraftJava> --accept-eula
        apocalypse-test.sh update | status | logs | selftest | connect | down
        apocalypse-test.sh cmd "<commande console Minecraft>"
EOF
  exit 2
}

isolated_only() {
  local dir="$1"
  if [[ -f "$dir/.env" ]] && ! grep -qx "$ISOLATED" "$dir/.env" 2>/dev/null; then
    echo "$dir n'est pas une instance de test isolée : arrêt, rien n'a été modifié." >&2
    exit 1
  fi
}

# Replaces the module code wholesale so a file removed upstream cannot linger; .env, data and state stay.
fetch_branch() {
  local dest="$1" archive
  archive=$(mktemp)
  curl -fsSL "$ARCHIVE" -o "$archive"
  isolated_only "$dest"
  rm -rf "$dest/apocalypse"
  tar -xzf "$archive" -C "$dest" --strip-components=1
  rm -f "$archive"
}

self_dir=$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd || echo /nonexistent)
if [[ ! -f "$self_dir/setup.py" || ! -f "$self_dir/../compose.yaml" ]]; then
  for tool in curl tar python3; do
    command -v "$tool" >/dev/null || { echo "Outil manquant : $tool" >&2; exit 1; }
  done
  dest="${DUCKCAT_TEST_DIR:-$HOME/duckcatmine-apocalypse-test}"
  isolated_only "$dest"
  mkdir -p "$dest"
  echo "Copie de la branche $BRANCH dans $dest"
  fetch_branch "$dest"
  # A piped bootstrap must not hand the rest of its own text to the commands that follow.
  exec bash "$dest/scripts/apocalypse-test.sh" "$@" </dev/null
fi

cd "$self_dir/.."
docker_cmd=(docker)
docker info >/dev/null 2>&1 || docker_cmd=(sudo docker)
compose() { "${docker_cmd[@]}" compose "$@"; }
rcon() { compose exec -T minecraft rcon-cli "$@"; }

installed_only() {
  [[ -f .env ]] || { echo "Aucune instance dans $PWD : lance d'abord install." >&2; exit 1; }
  isolated_only "$PWD"
}

wait_ready() {
  printf 'Attente de Minecraft'
  for _ in $(seq 1 180); do
    if rcon list >/dev/null 2>&1; then
      echo ' : prêt.'
      break
    fi
    printf '.'
    sleep 5
  done
  if ! rcon plugins 2>/dev/null | grep -q DuckCatApocalypse; then
    echo
    echo "DuckCatApocalypse n'est pas chargé. Dernières lignes du serveur :" >&2
    compose logs --tail=80 minecraft >&2
    exit 1
  fi
}

connect() {
  local user="${USER:-$(id -un)}" host="ADRESSE-DU-VPS" port="" server_ip server_port
  if [[ -n "${SSH_CONNECTION:-}" ]]; then
    read -r _ _ server_ip server_port <<<"$SSH_CONNECTION"
    host="$server_ip"
    [[ "$server_port" == 22 ]] || port="-p $server_port "
  fi
  cat <<EOF

Sur ton PC avec Minecraft Java 26.3, garde ce tunnel ouvert pendant la partie :
  ssh -N ${port}-L 25585:127.0.0.1:25585 -L 18080:127.0.0.1:18080 $user@$host
Puis Minecraft : Multijoueur > Ajouter un serveur > localhost:25585
En jeu : /apocalypse join ground_zero
EOF
}

install_instance() {
  local name="${1:-}"
  [[ "$name" =~ ^[A-Za-z0-9_]{3,16}$ ]] || usage
  if [[ "${2:-}" != --accept-eula ]]; then
    echo "Lis https://www.minecraft.net/eula puis relance avec --accept-eula." >&2
    exit 2
  fi
  isolated_only "$PWD"
  if [[ -f .env ]]; then
    compose up -d
  else
    local others
    others=$("${docker_cmd[@]}" ps --filter label=com.docker.compose.project=duckcatmine-stage \
      --format '{{.Label "com.docker.compose.project.working_dir"}}' | sort -u)
    if [[ -n "$others" ]]; then
      echo "Une autre instance de test tourne déjà dans : $others" >&2
      echo "Arrête-la d'abord : cd $others && docker compose down" >&2
      exit 1
    fi
    local setup=(python3 scripts/setup.py --local --apocalypse --admins "$name" --profile small
      --accept-eula --non-interactive --no-invites)
    if [[ "${docker_cmd[0]}" == sudo ]]; then sudo "${setup[@]}"; else "${setup[@]}"; fi
  fi
  wait_ready
  rcon "apocalypse admin world create"
  rcon "apocalypse admin enable"
  rcon "apocalypse admin selftest"
  connect
}

case "${1:-}" in
  install) shift; install_instance "$@" ;;
  update)
    installed_only
    fetch_branch "$PWD"
    exec bash scripts/apocalypse-test.sh rebuild
    ;;
  rebuild)
    installed_only
    compose run --build --rm plugins
    compose up -d --build
    compose restart minecraft
    wait_ready
    rcon "apocalypse admin selftest"
    ;;
  status) installed_only; compose ps; rcon "apocalypse admin status" ;;
  logs) installed_only; compose logs --tail=400 minecraft | grep -iE 'apocalypse|exception|error' || true ;;
  selftest) installed_only; rcon "apocalypse admin selftest" ;;
  cmd) installed_only; [[ -n "${2:-}" ]] || usage; rcon "$2" ;;
  connect) connect ;;
  down) installed_only; compose down ;;
  *) usage ;;
esac
