"""Bounded backup retention, sharing mc-backup's lock. No game commands."""

from contextlib import contextmanager
from datetime import datetime, timezone
import gzip
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import tarfile

BACKUP_LIMIT = 5
BACKUP_NAME = re.compile(r'^(?:world|manual|pre-restore)-[0-9-]+\.tar\.gz$')


@contextmanager
def archive_lock(directory):
    # mc-backup holds this lock while writing its final archive name.
    fd = os.open(Path(directory) / '.mc-backup-lock',
                 os.O_CREAT | os.O_RDWR | getattr(os, 'O_NOFOLLOW', 0), 0o600)
    try:
        if os.name == 'nt':
            import msvcrt
            msvcrt.locking(fd, msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield
    finally:
        os.close(fd)


def inventory(directory):
    result = []
    for path in Path(directory).iterdir():
        if not BACKUP_NAME.fullmatch(path.name):
            continue
        info = path.lstat()
        if stat.S_ISREG(info.st_mode) and info.st_size > 0:
            result.append((path, info))
    return sorted(result, key=lambda item: (item[1].st_mtime_ns, item[0].name), reverse=True)


def restore_sources(jobs):
    protected = set()
    for suffix in ('request', 'running'):
        for path in Path(jobs).glob(f'*.{suffix}'):
            try:
                job = json.loads(path.read_text(encoding='utf-8'))
                if job.get('type') == 'restore':
                    protected.add(job.get('backup', ''))
            except (OSError, ValueError):
                continue
    return protected


def write_json(path, value):
    temp = path.with_suffix('.tmp')
    temp.write_text(json.dumps(value, ensure_ascii=False), encoding='utf-8')
    temp.replace(path)


def verify_latest(entry, jobs, required_files):
    path, info = entry
    required = sorted({'world/level.dat', 'server.properties', *required_files})
    identity = [path.name, info.st_size, info.st_mtime_ns, info.st_ino, required]
    cache = Path(jobs) / 'backup-verification.json'
    try:
        verified = json.loads(cache.read_text(encoding='utf-8'))
    except (OSError, ValueError):
        verified = {}
    if verified.get('identity') == identity:
        if not verified.get('ok'):
            raise ValueError(verified['error'])
        return
    try:
        found = set()
        # Streaming avoids keeping a multi-GB archive or all its headers in RAM.
        # Drain gzip after tar's end marker to verify the gzip CRC/trailer too.
        with gzip.open(path, 'rb') as stream:
            with tarfile.open(fileobj=stream, mode='r|') as archive:
                for member in archive:
                    if (member.name.startswith('/') or '..' in PurePosixPath(member.name).parts
                            or not (member.isfile() or member.isdir())):
                        raise ValueError('Chemin non autorisé dans la dernière archive.')
                    name = member.name.removeprefix('./')
                    if member.isfile() and name in required:
                        found.add(name)
            while stream.read(1024 * 1024):
                pass
        if not set(required).issubset(found):
            raise ValueError('La dernière archive ne contient pas tous les fichiers nécessaires à la restauration.')
    except (OSError, ValueError, tarfile.TarError, EOFError) as error:
        message = f'Nettoyage suspendu : dernière sauvegarde invalide ({error}).'
        write_json(cache, {'identity': identity, 'ok': False, 'error': message})
        raise ValueError(message) from error
    write_json(cache, {'identity': identity, 'ok': True})


def remove_entry(entry):
    path, before = entry
    current = path.lstat()
    if (not stat.S_ISREG(current.st_mode) or
            (current.st_ino, current.st_size, current.st_mtime_ns) !=
            (before.st_ino, before.st_size, before.st_mtime_ns)):
        raise ValueError('La sauvegarde a changé ; recommence après son actualisation.')
    path.unlink()
    return {'name': path.name, 'size': before.st_size}


def prune(directory, jobs, required_files=()):
    with archive_lock(directory):
        entries = inventory(directory)
        # A queued restore must keep its exact input until it has finished.
        if len(entries) <= BACKUP_LIMIT or restore_sources(jobs):
            return []
        verify_latest(entries[0], jobs, required_files)
        removed = [remove_entry(entry) for entry in entries[BACKUP_LIMIT:]]
        write_json(Path(jobs) / 'backup-retention.json', {
            'at': datetime.now(timezone.utc).isoformat(), 'limit': BACKUP_LIMIT,
            'removed': removed, 'freedBytes': sum(item['size'] for item in removed),
        })
        return removed


def delete(directory, jobs, name, expected, required_files=()):
    if not isinstance(name, str) or not BACKUP_NAME.fullmatch(name):
        raise ValueError('Nom de sauvegarde invalide.')
    with archive_lock(directory):
        entries = inventory(directory)
        target = next((entry for entry in entries if entry[0].name == name), None)
        if not target:
            raise FileNotFoundError('Sauvegarde introuvable.')
        if target == entries[0]:
            raise ValueError('La sauvegarde la plus récente est protégée.')
        if name in restore_sources(jobs):
            raise ValueError('Cette sauvegarde est utilisée pour une restauration.')
        info = target[1]
        if (not isinstance(expected, dict) or expected.get('size') != info.st_size or
                abs(expected.get('mtimeMs', -1) - info.st_mtime_ns / 1_000_000) >= 1):
            raise ValueError('La sauvegarde a changé ; actualise la liste.')
        verify_latest(entries[0], jobs, required_files)
        removed = remove_entry(target)
        write_json(Path(jobs) / 'backup-deletion.json', {
            'at': datetime.now(timezone.utc).isoformat(), **removed,
        })
        return removed
