#!/usr/bin/env python3
"""Private, file-queued Minecraft maintenance worker. No HTTP listener."""

import json
import os
import pathlib
import re
import shutil
import subprocess
import tarfile
import time
from datetime import datetime, timezone
from plugin_manager import PluginManager


PROJECT = pathlib.Path(os.environ.get('PROJECT_DIR', '/srv/duckcatmine'))
JOBS = pathlib.Path(os.environ.get('JOBS_DIR', '/jobs'))
DATA = PROJECT / 'data'
BACKUPS = PROJECT / 'backups'
COMPOSE = PROJECT / 'compose.yaml'
PENDING = pathlib.Path(os.environ.get('PENDING_FILE', str(PROJECT / 'admin' / 'state' / 'pending-settings.json')))
BACKUP_NAME = re.compile(r'^(?:world|manual|pre-restore)-[0-9-]+\.tar\.gz$')
PROPERTIES = {
    'motd': ('motd', lambda v: isinstance(v, str) and 1 <= len(v) <= 80 and not any(c in v for c in '\r\n\0')),
    'maxPlayers': ('max-players', lambda v: type(v) is int and 2 <= v <= 50),
    'pvp': ('pvp', lambda v: type(v) is bool),
    'viewDistance': ('view-distance', lambda v: type(v) is int and 4 <= v <= 16),
    'simulationDistance': ('simulation-distance', lambda v: type(v) is int and 4 <= v <= 12),
}


def compose(*args, timeout=1800):
    result = subprocess.run(
        ['docker', 'compose', '-f', str(COMPOSE), *args],
        capture_output=True, text=True, timeout=timeout,
    )
    if result.returncode:
        raise RuntimeError((result.stderr or result.stdout).strip()[-2000:])
    return result.stdout.strip()


def no_players():
    output = compose('exec', '-T', 'minecraft', 'rcon-cli', 'list', timeout=20)
    if not re.search(r'\b0 of a max of \d+ players online\b', output):
        raise RuntimeError('Des joueurs sont connectés ; opération annulée.')


def update_metrics():
    result = subprocess.run(
        ['docker', 'stats', '--no-stream', '--format', '{{json .}}'],
        capture_output=True, text=True, timeout=20,
    )
    if result.returncode or not result.stdout.strip():
        return
    stats = [json.loads(line) for line in result.stdout.splitlines() if line.strip()]
    game_name = os.environ.get('MC_CONTAINER_NAME', 'duckcatmine-minecraft-1')
    game = next((item for item in stats if item.get('Name') == game_name), None)
    if game is None:
        return
    def bytes_used(text):
        value = text.split('/')[0].strip()
        match = re.match(r'([\d.]+)\s*([KMGT]?i?B)', value, re.I)
        if not match:
            return 0
        unit = match.group(2).lower()
        power = {'b': 0, 'kib': 1, 'mib': 2, 'gib': 3, 'tib': 4,
                 'kb': 1, 'mb': 2, 'gb': 3, 'tb': 4}[unit]
        base = 1024 if 'i' in unit else 1000
        return int(float(match.group(1)) * base ** power)
    services = [{
        'name': item.get('Name', ''),
        'cpu': float(item.get('CPUPerc', '0').removesuffix('%')),
        'memoryBytes': bytes_used(item.get('MemUsage', '')),
    } for item in stats]
    services.sort(key=lambda item: item['memoryBytes'], reverse=True)
    try:
        previous = json.loads((JOBS / 'metrics.json').read_text(encoding='utf-8'))
        history = previous.get('history', [])[-119:]
    except (FileNotFoundError, ValueError):
        history = []
    at = datetime.now(timezone.utc).isoformat()
    history.append({'at': at, 'cpu': float(game.get('CPUPerc', '0').removesuffix('%')),
                    'memoryBytes': bytes_used(game.get('MemUsage', ''))})
    host = {}
    try:
        meminfo = dict(re.findall(r'^(MemTotal|MemAvailable):\s*(\d+)\s*kB$',
                                  pathlib.Path('/proc/meminfo').read_text(), re.M))
        host = {key: int(value) * 1024 for key, value in meminfo.items()}
    except OSError:
        pass
    value = {
        'memory': game.get('MemUsage', ''),
        'memoryBytes': bytes_used(game.get('MemUsage', '')),
        'memoryLimitBytes': bytes_used(game.get('MemUsage', '').split('/')[-1]),
        'cpu': game.get('CPUPerc', ''),
        'at': at,
        'services': services,
        'history': history,
        'host': host,
        'cpuCount': os.cpu_count() or 1,
        'hostCpuPercent': float(game.get('CPUPerc', '0').removesuffix('%')) / (os.cpu_count() or 1),
    }
    temp = JOBS / 'metrics.json.tmp'
    temp.write_text(json.dumps(value), encoding='utf-8')
    temp.replace(JOBS / 'metrics.json')


def stage_properties(settings):
    if not isinstance(settings, dict) or not settings:
        raise ValueError('Aucun réglage.')
    replacements = {}
    for key, value in settings.items():
        if key not in PROPERTIES or not PROPERTIES[key][1](value):
            raise ValueError(f'Réglage invalide : {key}')
        replacements[PROPERTIES[key][0]] = str(value).lower() if isinstance(value, bool) else str(value)
    filename = DATA / 'server.properties'
    lines = filename.read_text(encoding='utf-8').splitlines()
    found = set()
    updated = []
    for line in lines:
        key = line.split('=', 1)[0]
        if key in replacements:
            updated.append(f'{key}={replacements[key]}')
            found.add(key)
        else:
            updated.append(line)
    for key in replacements.keys() - found:
        updated.append(f'{key}={replacements[key]}')
    temp = filename.with_suffix('.properties.tmp')
    temp.write_text('\n'.join(updated) + '\n', encoding='utf-8')
    temp.replace(filename)


def safe_members(archive):
    members = archive.getmembers()
    for member in members:
        parts = pathlib.PurePosixPath(member.name).parts
        if (member.name.startswith('/') or '..' in parts or
                not (member.isfile() or member.isdir())):
            raise ValueError('Archive contenant un chemin non autorisé.')
    return members


def extract_archive(filename):
    with tarfile.open(filename, 'r:gz') as archive:
        members = safe_members(archive)
        archive.extractall(DATA, members=members, filter='data')


def snapshot_before_restore():
    stamp = datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')
    filename = BACKUPS / f'pre-restore-{stamp}.tar.gz'
    with tarfile.open(filename, 'w:gz') as archive:
        archive.add(DATA, arcname='.')
    return filename


def clear_data():
    # These directories are bind-mounted into the panel. Keep their inodes so
    # that uploads and custom item writes still reach the restored data tree.
    mounted = [DATA / 'plugins/mCraft/items', DATA / 'plugins/DuckCatTowers/schematics']
    def clear(directory):
        for child in directory.iterdir():
            keep = any(child == mount or child in mount.parents for mount in mounted)
            if child.is_dir() and not child.is_symlink():
                if keep:
                    clear(child)
                else:
                    shutil.rmtree(child)
            else:
                child.unlink()
    clear(DATA)


def run_job(job):
    kind = job.get('type')
    if kind == 'plugin-stage':
        return PluginManager(DATA, JOBS).stage(job)
    if kind == 'plugin-cancel':
        return PluginManager(DATA, JOBS).cancel(job.get('artifact'))
    if kind == 'backup':
        compose('exec', '-T', 'backups', 'backup', 'now', timeout=1800)
        return 'Sauvegarde créée.'
    if kind == 'map-policy':
        policy = job.get('policy')
        if policy not in ('idle', 'live'):
            raise ValueError('Politique de carte invalide.')
        filename = DATA / 'plugins' / 'BlueMap' / 'plugin.conf'
        previous = filename.read_text(encoding='utf-8')
        value = '1' if policy == 'idle' else '-1'
        setting = f'player-render-limit: {value}'
        updated, count = re.subn(r'^player-render-limit:\s*-?\d+\s*$', setting, previous, flags=re.M)
        if not count:
            updated = previous.rstrip() + '\n' + setting + '\n'
        filename.with_suffix('.conf.before-duckcatmine').write_text(previous, encoding='utf-8')
        filename.write_text(updated, encoding='utf-8')
        try:
            output = compose('exec', '-T', 'minecraft', 'rcon-cli', 'bluemap reload light', timeout=90)
            if re.search(r'unknown command|error|failed', output, re.I):
                raise RuntimeError(output[-500:])
            compose('exec', '-T', 'minecraft', 'rcon-cli', 'bluemap start', timeout=30)
        except Exception:
            filename.write_text(previous, encoding='utf-8')
            compose('exec', '-T', 'minecraft', 'rcon-cli', 'bluemap reload light', timeout=90)
            raise
        return ('Priorité au jeu activée : terrain redessiné sans joueur, positions en direct.'
                if policy == 'idle' else 'Actualisation du terrain pendant les parties activée.')
    if kind == 'apply':
        settings = job.get('settings')
        no_players()
        compose('exec', '-T', 'backups', 'backup', 'now', timeout=1800)
        no_players()
        try:
            compose('stop', 'backups', 'minecraft', timeout=180)
            stage_properties(settings)
            PluginManager(DATA, JOBS).apply()
        finally:
            compose('up', '-d', 'minecraft', 'backups', timeout=300)
        if PENDING.exists() and json.loads(PENDING.read_text(encoding='utf-8')) == settings:
            PENDING.unlink()
        return 'Sauvegarde créée, réglages appliqués, puis Minecraft relancé.'
    if kind == 'restart':
        no_players()
        compose('exec', '-T', 'backups', 'backup', 'now', timeout=1800)
        no_players()
        settings = json.loads(PENDING.read_text(encoding='utf-8')) if PENDING.exists() else {}
        try:
            compose('stop', 'backups', 'minecraft', timeout=180)
            if settings:
                stage_properties(settings)
            PluginManager(DATA, JOBS).apply()
        finally:
            compose('up', '-d', 'minecraft', 'backups', timeout=300)
        if settings and PENDING.exists() and json.loads(PENDING.read_text(encoding='utf-8')) == settings:
            PENDING.unlink()
        return 'Sauvegarde créée, réglages en attente appliqués et Minecraft relancé.' if settings else 'Sauvegarde créée, puis Minecraft relancé avec les nouveaux plugins.'
    if kind == 'restore':
        name = job.get('backup')
        if not isinstance(name, str) or not BACKUP_NAME.fullmatch(name):
            raise ValueError('Nom de sauvegarde invalide.')
        source = BACKUPS / name
        if not source.is_file():
            raise FileNotFoundError('Sauvegarde introuvable.')
        with tarfile.open(source, 'r:gz') as archive:
            members = safe_members(archive)
            archived = {member.name.removeprefix('./') for member in members}
            installed = PluginManager(DATA, JOBS).inventory()
            required = {'plugins/' + item['filename'] for item in installed if item['core']}
            has_jars = any(re.fullmatch(r'plugins/[^/]+\.jar', name) for name in archived)
            if not required.issubset(archived) or (installed and not has_jars):
                raise ValueError('Cette ancienne archive ne contient pas toutes les extensions. Crée une sauvegarde complète récente ; pour récupérer cette archive, utilise une restauration manuelle en conservant les extensions.')
        no_players()
        compose('stop', 'backups', 'minecraft', timeout=180)
        snapshot = None
        try:
            snapshot = snapshot_before_restore()
            clear_data()
            extract_archive(source)
        except Exception:
            if snapshot:
                clear_data()
                extract_archive(snapshot)
            raise
        finally:
            compose('up', '-d', 'minecraft', 'backups', timeout=300)
        return f'Sauvegarde {name} restaurée. Copie précédente : {snapshot.name}.'
    raise ValueError('Type d’opération inconnu.')


def handle_request(filename):
    running = filename.with_suffix('.running')
    done = filename.with_suffix('.done')
    filename.replace(running)
    try:
        job = json.loads(running.read_text(encoding='utf-8'))
        if not re.fullmatch(r'[a-f0-9]{32}', job.get('id', '')):
            raise ValueError('Identifiant invalide.')
        message = run_job(job)
        result = {'ok': True, 'message': message}
    except Exception as error:
        print(f'Job failed: {error}', flush=True)
        result = {'ok': False, 'error': str(error)[:500]}
    result['at'] = datetime.now(timezone.utc).isoformat()
    done.write_text(json.dumps(result), encoding='utf-8')
    running.unlink(missing_ok=True)


def main():
    JOBS.mkdir(parents=True, exist_ok=True)
    for stale in JOBS.glob('*.running'):
        failed = stale.with_suffix('.done')
        failed.write_text(json.dumps({'ok': False, 'error': 'Opération interrompue ; vérifier le serveur avant de réessayer.'}), encoding='utf-8')
        stale.unlink()
    last_metrics = 0
    plugins = PluginManager(DATA, JOBS)
    idle_since = {}
    last_idle_check = {}
    while True:
        if time.monotonic() - last_metrics > 15:
            try:
                update_metrics()
                plugins.publish()
            except Exception as error:
                print(f'Metrics unavailable: {error}', flush=True)
            last_metrics = time.monotonic()
        for filename in sorted(JOBS.glob('*.request')):
            try:
                job = json.loads(filename.read_text(encoding='utf-8'))
            except Exception:
                handle_request(filename)
                continue
            if job.get('type') == 'restart':
                job_id = job.get('id', '')
                now = time.monotonic()
                if now - last_idle_check.get(job_id, 0) < 30:
                    continue
                last_idle_check[job_id] = now
                try:
                    no_players()
                    idle_since.setdefault(job_id, now)
                except Exception:
                    idle_since.pop(job_id, None)
                    continue
                if now - idle_since[job_id] < 300:
                    continue
            handle_request(filename)
        time.sleep(2)


if __name__ == '__main__':
    main()
