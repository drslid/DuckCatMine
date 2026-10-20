"""Offline plugin validation and transactional staging. Never loads code in the worker."""
import hashlib
import json
import pathlib
import re
import shutil
import time
import zipfile

import yaml

MAX_JAR = 50 * 1024 * 1024
CORE = {'duckcatwelcome', 'duckcattowers', 'bluemap', 'skinsrestorer', 'worldedit'}
ID = re.compile(r'^[a-f0-9]{32}$')


def digest(filename):
    with open(filename, 'rb') as source:
        return hashlib.file_digest(source, 'sha512').hexdigest()


def metadata(filename):
    if filename.is_symlink() or not 0 < filename.stat().st_size <= MAX_JAR:
        raise ValueError('Fichier .jar invalide ou trop volumineux (50 Mio maximum).')
    with zipfile.ZipFile(filename) as archive:
        if len(archive.infolist()) > 20000:
            raise ValueError('Archive trop complexe.')
        entry = next((name for name in ('paper-plugin.yml', 'plugin.yml') if name in archive.namelist()), None)
        if not entry or archive.getinfo(entry).file_size > 65536:
            raise ValueError('Ce fichier n’est pas un plugin Paper/Bukkit (métadonnées absentes).')
        info = yaml.safe_load(archive.read(entry))
        if not isinstance(info, dict):
            raise ValueError('Métadonnées invalides.')
        name, version, main = str(info.get('name', '')), str(info.get('version', '')), str(info.get('main', ''))
        if not re.fullmatch(r'[A-Za-z0-9_.-]{1,80}', name) or not 1 <= len(version) <= 120:
            raise ValueError('Nom ou version de plugin invalide.')
        if not re.fullmatch(r'[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)+', main):
            raise ValueError('Classe principale invalide.')
        with archive.open(main.replace('.', '/') + '.class') as source:
            header = source.read(8)
        if len(header) != 8 or header[:4] != b'\xca\xfe\xba\xbe' or int.from_bytes(header[6:8], 'big') > 69:
            raise ValueError('Ce plugin exige une version Java supérieure à Java 25, ou contient une classe invalide.')
        dependencies = info.get('depend', []) or []
        if not isinstance(dependencies, list):
            raise ValueError('Liste de dépendances invalide.')
        paper = (info.get('dependencies') or {}).get('server', {}) if entry == 'paper-plugin.yml' else {}
        dependencies += [key for key, value in paper.items() if isinstance(value, dict) and value.get('required', True)]
        provides = info.get('provides', []) or []
        if not isinstance(provides, list):
            provides = []
        if any(not isinstance(d, str) or not re.fullmatch(r'[A-Za-z0-9_.-]{1,80}', d) for d in dependencies + provides):
            raise ValueError('Nom de dépendance invalide.')
        return {'name': name, 'version': version, 'dependencies': [str(d) for d in dependencies],
                'provides': [str(p) for p in provides], 'core': name.lower() in CORE}


def atomic_json(filename, value):
    temp = filename.with_suffix('.tmp')
    temp.write_text(json.dumps(value, ensure_ascii=False), encoding='utf-8')
    temp.replace(filename)


class PluginManager:
    def __init__(self, data, jobs):
        self.data, self.jobs = pathlib.Path(data), pathlib.Path(jobs)
        self.store = self.jobs / 'plugins'
        self.store.mkdir(parents=True, exist_ok=True)
        self.pending_file = self.jobs / 'plugins-pending.json'
        self.cache = {}

    def pending(self):
        return json.loads(self.pending_file.read_text(encoding='utf-8')) if self.pending_file.exists() else []

    def inventory(self):
        result = []
        active = set()
        for filename in sorted((self.data / 'plugins').glob('*.jar')):
            stat = filename.stat()
            key = (str(filename), stat.st_mtime_ns, stat.st_size)
            active.add(key)
            if key not in self.cache:
                try:
                    item = metadata(filename)
                except Exception:
                    item = {'name': filename.stem, 'version': 'inconnue', 'dependencies': [], 'provides': [], 'core': False,
                            'warning': 'Métadonnées non lisibles ; consulter la console.'}
                self.cache[key] = {**item, 'filename': filename.name, 'size': stat.st_size}
            result.append(self.cache[key])
        self.cache = {key: value for key, value in self.cache.items() if key in active}
        return result

    def publish(self):
        version = None
        try:
            version = json.loads((self.data / '.papermc-manifest.json').read_text())['minecraftVersion']
        except (OSError, ValueError, KeyError):
            pass
        atomic_json(self.jobs / 'plugins-inventory.json', {'installed': self.inventory(), 'gameVersion': version,
                    'observedAt': time.time() * 1000})

    def stage(self, job):
        artifact = job.get('artifact', '')
        if not ID.fullmatch(artifact):
            raise ValueError('Identifiant de fichier invalide.')
        source = self.jobs / (artifact + '.upload')
        destination = self.store / (artifact + '.jar')
        try:
            if digest(source) != job.get('sha512'):
                raise ValueError('L’empreinte du fichier ne correspond pas.')
            item = metadata(source)
            if item['core']:
                raise ValueError('Cette extension fait partie du socle DuckCatMine ; utilise une mise à jour de la stack.')
            pending = self.pending()
            if len(pending) >= 20 or any(p['name'].lower() == item['name'].lower() for p in pending):
                raise ValueError('Extension déjà préparée ou limite de 20 ajouts atteinte.')
            existing = [i for i in self.inventory() if i['name'].lower() == item['name'].lower()]
            if len(existing) > 1:
                raise ValueError('Plusieurs fichiers existent pour cette extension ; nettoyer manuellement avant de la modifier.')
            previous = existing[0] if existing else None
            item.update({'id': artifact, 'sha512': job['sha512'], 'source': str(job.get('source', 'Import .jar'))[:200],
                         'target': previous['filename'] if previous else 'DuckCatAddon-' + item['name'] + '.jar',
                         'previousVersion': previous['version'] if previous else None,
                         'basis': digest(self.data / 'plugins' / previous['filename']) if previous else None})
            if not previous and (self.data / 'plugins' / item['target']).exists():
                raise ValueError('Le nom du fichier cible est déjà utilisé.')
            source.replace(destination)
            try:
                atomic_json(self.pending_file, pending + [item])
            except Exception:
                destination.unlink(missing_ok=True)
                raise
            self.publish()
            return f"{item['name']} {item['version']} prêt pour la prochaine relance sauvegardée."
        finally:
            source.unlink(missing_ok=True)

    def cancel(self, artifact):
        if not isinstance(artifact, str) or not ID.fullmatch(artifact):
            raise ValueError('Identifiant invalide.')
        atomic_json(self.pending_file, [p for p in self.pending() if p['id'] != artifact])
        (self.store / (artifact + '.jar')).unlink(missing_ok=True)
        return 'Ajout annulé.'

    def apply(self):
        pending = self.pending()
        if not pending:
            return
        installed = self.inventory()
        names = {n.lower() for p in installed + pending for n in [p['name'], *p.get('provides', [])]}
        for p in pending:
            if not ID.fullmatch(p['id']) or pathlib.Path(p['target']).name != p['target'] or '/' in p['target'] or '\\' in p['target']:
                raise ValueError('Cible invalide.')
            source = self.store / (p['id'] + '.jar')
            if digest(source) != p['sha512'] or metadata(source)['name'] != p['name']:
                raise ValueError('Le fichier préparé a changé.')
            missing = [d for d in p['dependencies'] if d.lower() not in names]
            if missing:
                raise ValueError(p['name'] + ' : dépendances manquantes : ' + ', '.join(missing))
            target = self.data / 'plugins' / p['target']
            if (digest(target) if target.exists() else None) != p['basis']:
                raise ValueError('Le plugin installé a changé depuis sa préparation : ' + p['name'])
        rollback = self.store / 'rollback'
        rollback.mkdir(exist_ok=True)
        changed = []
        try:
            for p in pending:
                target = self.data / 'plugins' / p['target']
                before = rollback / (p['id'] + '.jar')
                if target.exists():
                    shutil.copy2(target, before)
                changed.append((target, before, p['basis'] is not None))
                temp = target.with_suffix('.jar.prepared')
                shutil.copy2(self.store / (p['id'] + '.jar'), temp)
                temp.replace(target)
            atomic_json(self.pending_file, [])
        except Exception:
            for target, before, existed in reversed(changed):
                if existed:
                    shutil.copy2(before, target)
                else:
                    target.unlink(missing_ok=True)
                target.with_suffix('.jar.prepared').unlink(missing_ok=True)
            raise
        for p in pending:
            (self.store / (p['id'] + '.jar')).unlink(missing_ok=True)
        shutil.rmtree(rollback)
        self.publish()
