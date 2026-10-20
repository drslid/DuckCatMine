#!/usr/bin/env python3
"""Guided first installation. Refuses to overwrite an existing installation."""
import argparse
import getpass
import hashlib
import json
import os
import pathlib
import re
import secrets
import shutil
import socket
import subprocess
import sys
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
PROFILES = {
    'small': {'MC_HEAP': '2G', 'MC_LIMIT': '3g', 'VIEW_DISTANCE': '6', 'SIMULATION_DISTANCE': '4', 'MAX_PLAYERS': '6'},
    'friends': {'MC_HEAP': '4G', 'MC_LIMIT': '6g', 'VIEW_DISTANCE': '8', 'SIMULATION_DISTANCE': '6', 'MAX_PLAYERS': '10'},
}


def run(*command, capture=False):
    return subprocess.run(command, cwd=ROOT, check=True, text=True,
                          stdout=subprocess.PIPE if capture else None).stdout


def prompt(question, default=''):
    # stdin may be a curl pipe. Always read interactive answers from the terminal.
    with open('/dev/tty', 'r+') as terminal:
        terminal.write(f'{question}' + (f' [{default}]' if default else '') + ': ')
        terminal.flush()
        return terminal.readline().strip() or default


def write_file(filename, content, private=False):
    path = ROOT / filename
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    path.chmod(0o600 if private else 0o644)


def download_plugins():
    manifest = json.loads((ROOT / 'plugins.lock.json').read_text())
    for plugin in manifest:
        dest = ROOT / 'data/plugins' / plugin['filename']
        if dest.exists() and hashlib.sha512(dest.read_bytes()).hexdigest() == plugin['sha512']:
            continue
        print(f"Download {plugin['project']} {plugin['version']} (checksum verified).", flush=True)
        request = urllib.request.Request(plugin['url'], headers={'User-Agent': 'DuckCatMine/2.0'})
        with urllib.request.urlopen(request, timeout=120) as response:
            payload = response.read(100 * 1024 * 1024)
        if hashlib.sha512(payload).hexdigest() != plugin['sha512']:
            raise RuntimeError('Plugin checksum mismatch; installation stopped.')
        temporary = dest.with_suffix('.download')
        temporary.write_bytes(payload)
        temporary.replace(dest)


def configure(origin):
    write_file('data/plugins/BlueMap/core.conf', 'accept-download: true\nrender-thread-count: 1\n')
    write_file('data/plugins/BlueMap/plugin.conf', 'live-player-markers: true\nplayer-render-limit: 1\nskin-download: true\n')
    write_file('data/plugins/BlueMap/webserver.conf', 'enabled: true\nport: 8100\nsse-enabled: true\n')
    write_file('data/plugins/BlueMap/webapp.conf', 'enabled: true\nscripts: ["/map-picker.js"]\n')
    # Minecraft 26.3 stores dimension regions below world/dimensions/minecraft.
    for map_id, dimension in [('world', 'overworld'), ('world_the_nether', 'the_nether'), ('world_the_end', 'the_end')]:
        write_file(f'data/plugins/BlueMap/maps/{map_id}.conf',
                   f'world: "world/dimensions/minecraft/{dimension}"\n'
                   f'dimension: "minecraft:{dimension}"\nname: "{map_id}"\n'
                   'enable-flat-view: true\nenable-perspective-view: true\n')
    welcome = (ROOT / 'welcome/src/main/resources/config.yml').read_text(encoding='utf-8')
    write_file('data/plugins/DuckCatWelcome/config.yml', welcome + 'panel-url: ' + json.dumps(origin) + '\n')


def own_runtime(uid, gid):
    if os.geteuid() != 0:
        return
    # Only paths created for this fresh installation are traversed.
    for folder in ['secrets', 'admin/state', 'admin/jobs', 'data', 'backups', 'admin/public/icons']:
        root = ROOT / folder
        for directory, dirs, files in os.walk(root, followlinks=False):
            os.chown(directory, uid, gid)
            for name in dirs + files:
                path = pathlib.Path(directory) / name
                if not path.is_symlink():
                    os.chown(path, uid, gid)
    os.chown(ROOT / '.env', uid, gid)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--domain')
    parser.add_argument('--admins', help='Comma-separated official Java account names')
    parser.add_argument('--profile', choices=PROFILES)
    parser.add_argument('--accept-eula', action='store_true')
    parser.add_argument('--non-interactive', action='store_true')
    parser.add_argument('--prepare-only', action='store_true', help='Prepare files, do not start containers')
    parser.add_argument('--local', action='store_true', help='Isolated development install on loopback ports, HTTP only')
    parser.add_argument('--no-invites', action='store_true', help='For automated integration checks')
    args = parser.parse_args()
    if (ROOT / '.env').exists() or (ROOT / 'data/level.dat').exists() or (ROOT / 'data/world').exists():
        raise RuntimeError('Existing installation detected. No files changed. See the update guide.')
    if '\n' in str(ROOT) or ' ' in str(ROOT) or ':' in str(ROOT):
        raise RuntimeError('Use a Linux install directory without spaces or colons.')
    run('docker', 'compose', 'version')
    if not args.accept_eula:
        if args.non_interactive:
            raise RuntimeError('Read https://www.minecraft.net/eula and explicitly pass --accept-eula.')
        answer = prompt('Read https://www.minecraft.net/eula — accept the Minecraft EULA? Type yes')
        if answer != 'yes':
            raise RuntimeError('EULA not accepted. Installation stopped.')
    domain = args.domain or ('localhost' if args.local else (prompt('Domain (example.duckdns.org or your own)') if not args.non_interactive else ''))
    domain = domain.strip().lower().rstrip('.')
    if not re.fullmatch(r'(?=.{1,253}$)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?', domain) or '..' in domain or ('.' not in domain and not args.local):
        raise RuntimeError('Enter a hostname only, without https://, port or path.')
    admins = args.admins or (prompt('Minecraft Java administrators (comma separated)') if not args.non_interactive else '')
    names = [name.strip() for name in admins.split(',')]
    if not names or not all(re.fullmatch(r'[A-Za-z0-9_]{3,16}', name) for name in names) or len(names) > 10:
        raise RuntimeError('Specify 1–10 valid Minecraft names.')
    profile = args.profile or ('friends' if args.non_interactive else prompt('Profile: small (4 GB VPS, 6 slots) or friends (8 GB VPS, 10 slots)', 'friends'))
    if profile not in PROFILES:
        raise RuntimeError('Unknown profile.')
    mem = os.sysconf('SC_PAGE_SIZE') * os.sysconf('SC_PHYS_PAGES')
    minimum = 3.5 if profile == 'small' else 7
    if mem < minimum * 1024**3:
        raise RuntimeError(f'{profile} requires a VPS with at least {minimum:g} GiB RAM.')
    if shutil.disk_usage(ROOT).free < 15 * 1024**3:
        raise RuntimeError('At least 15 GiB of free disk space is required.')
    bindings = [('127.0.0.1', 18080), ('127.0.0.1', 18443), ('127.0.0.1', 25585)] if args.local else [('0.0.0.0', p) for p in (80, 443, 25565)]
    for address, port in bindings:
        with socket.socket() as sock:
            try:
                sock.bind((address, port))
            except OSError:
                raise RuntimeError(f'TCP port {port} is already in use. No installation started.') from None
    token = ''
    subdomain = ''
    if not args.local and domain.endswith('.duckdns.org'):
        subdomain = domain.removesuffix('.duckdns.org')
        if not re.fullmatch(r'[a-z0-9-]+', subdomain):
            raise RuntimeError('Invalid DuckDNS subdomain.')
        if not args.non_interactive:
            with open('/dev/tty', 'w') as terminal:
                token = getpass.getpass('DuckDNS token (hidden; empty = manage DNS yourself): ', stream=terminal)
        if token:
            from duckdns import update
            update(subdomain, token)
    if not args.local:
        socket.getaddrinfo(domain, 443)
        print(f'DNS resolves for {domain}. Check that A/AAAA records point to this VPS and ports 80/443/25565 are allowed in its firewall.')
    uid = int(os.environ.get('SUDO_UID', os.getuid() if os.getuid() else 1000))
    gid = int(os.environ.get('SUDO_GID', os.getgid() if os.getgid() else 1000))
    origin = 'http://127.0.0.1:18080' if args.local else f'https://{domain}'
    env = dict(PROFILES[profile], EULA='TRUE', ADMINS=','.join(names), PUBLIC_ORIGIN=origin,
               SITE_ADDRESS='http://:80' if args.local else domain, MC_ADDRESS=f'{domain}:25585' if args.local else domain,
               INSTALL_DIR=str(ROOT), PUID=str(uid), PGID=str(gid),
               DOCKER_GID=str(pathlib.Path('/var/run/docker.sock').stat().st_gid),
               COMPOSE_PROJECT_NAME='duckcatmine-stage' if args.local else 'duckcatmine',
               HTTP_BIND='127.0.0.1:18080' if args.local else '80',
               HTTPS_BIND='127.0.0.1:18443' if args.local else '443',
               GAME_BIND='127.0.0.1:25585' if args.local else '25565')
    if token:
        env.update(DUCKDNS_SUBDOMAIN=subdomain, COMPOSE_PROFILES='duckdns')
    write_file('.env', ''.join(f'{key}={value}\n' for key, value in env.items()), private=True)
    write_file('secrets/rcon_password', secrets.token_hex(32) + '\n', private=True)
    write_file('secrets/duckdns_token', token + '\n', private=True)
    for folder in ['admin/state', 'admin/jobs', 'data/plugins/mCraft/items', 'data/plugins/DuckCatTowers/schematics', 'backups', 'admin/public/icons']:
        (ROOT / folder).mkdir(parents=True, exist_ok=True)
    configure(origin)
    download_plugins()
    print('Generate item previews locally from the official Minecraft client.', flush=True)
    run(sys.executable, str(ROOT / 'admin/build-item-visuals.py'))
    own_runtime(uid, gid)
    run('docker', 'compose', 'config', '--quiet')
    if args.prepare_only:
        print('Configuration prepared. Run docker compose run --build --rm plugins, then docker compose up -d --build.')
        return
    run('docker', 'compose', 'run', '--build', '--rm', 'plugins')
    run('docker', 'compose', 'up', '-d', '--build')
    print('Waiting for the panel...', flush=True)
    for _ in range(90):
        try:
            run('docker', 'compose', 'exec', '-T', 'panel', 'node', '-e', "fetch('http://127.0.0.1:3000/healthz').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))", capture=True)
            break
        except subprocess.CalledProcessError:
            time.sleep(2)
    else:
        raise RuntimeError('Panel did not start. Run docker compose logs panel. Your files are preserved.')
    print(f'\nPanel: {origin}\nMinecraft Java: {env["MC_ADDRESS"]}\nThe first game start and HTTPS certificate can take a few minutes.')
    if not args.no_invites:
        print('Private activation links (48 hours). Set your web passwords in your browser:')
        for name in names:
            link = run('docker', 'compose', 'exec', '-T', 'panel', 'node', 'server.js', 'invite', name, capture=True)
            print(f'{name}: {link.strip()}')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f'Installation stopped: {error}', file=sys.stderr)
        sys.exit(1)
