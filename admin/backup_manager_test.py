import gzip
import io
import json
import os
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import backup_manager as manager


class BackupTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.backups = self.root / 'backups'
        self.jobs = self.root / 'jobs'
        self.backups.mkdir()
        self.jobs.mkdir()

    def archive(self, number, prefix='world', valid=True):
        path = self.backups / f'{prefix}-20261101-{number:06}.tar.gz'
        if valid:
            with tarfile.open(path, 'w:gz') as archive:
                for name in ('world/level.dat', 'server.properties', 'plugins/Core.jar'):
                    payload = b'minecraft data'
                    member = tarfile.TarInfo('./' + name)
                    member.size = len(payload)
                    archive.addfile(member, io.BytesIO(payload))
        else:
            path.write_bytes(b'incomplete gzip data')
        os.utime(path, (number + 100, number + 100))
        return path

    def expected(self, path):
        info = path.stat()
        return {'size': info.st_size, 'mtimeMs': info.st_mtime_ns / 1_000_000}

    def test_latest_five_across_all_types_and_unrelated_files_untouched(self):
        archives = [self.archive(n, ('world', 'manual', 'pre-restore')[n % 3]) for n in range(9)]
        other = [self.backups / name for name in ('plugin.jar', 'world-20261101-999999.tar.gz.partial',
                                                   'notes.tar.gz', 'world-20261101-999998.tar.gz')]
        for path in other:
            path.write_bytes(b'' if path == other[-1] else b'untouched')
        removed = manager.prune(self.backups, self.jobs, ['plugins/Core.jar'])
        self.assertEqual({item['name'] for item in removed}, {path.name for path in archives[:4]})
        self.assertEqual({path.name for path, _ in manager.inventory(self.backups)}, {path.name for path in archives[4:]})
        self.assertTrue(all(path.exists() for path in other))
        report = json.loads((self.jobs / 'backup-retention.json').read_text())
        self.assertEqual(report['freedBytes'], sum(item['size'] for item in removed))
        newest = self.archive(10, 'manual')
        again = manager.prune(self.backups, self.jobs, ['plugins/Core.jar'])
        self.assertEqual([item['name'] for item in again], [archives[4].name])
        self.assertEqual(len(manager.inventory(self.backups)), 5)
        self.assertTrue(newest.exists())

    def test_corrupt_newest_never_prunes_previous_recovery_points(self):
        archives = [self.archive(n) for n in range(6)]
        self.archive(7, valid=False)
        with self.assertRaisesRegex(ValueError, 'Nettoyage suspendu'):
            manager.prune(self.backups, self.jobs)
        self.assertTrue(all(path.exists() for path in archives))

    def test_missing_plugin_and_gzip_crc_failure_block_pruning(self):
        for n in range(6):
            self.archive(n)
        with self.assertRaisesRegex(ValueError, 'fichiers nécessaires'):
            manager.prune(self.backups, self.jobs, ['plugins/Missing.jar'])
        newest = self.archive(9)
        payload = bytearray(newest.read_bytes())
        payload[-8] ^= 1
        newest.write_bytes(payload)
        with self.assertRaisesRegex(ValueError, 'Nettoyage suspendu'):
            manager.prune(self.backups, self.jobs)
        self.assertEqual(len(manager.inventory(self.backups)), 7)

    def test_automatic_writer_lock_prevents_prune_and_manual_delete(self):
        old = self.archive(0)
        for n in range(1, 7):
            self.archive(n)
        with manager.archive_lock(self.backups):
            with self.assertRaises((BlockingIOError, OSError)):
                manager.prune(self.backups, self.jobs)
            with self.assertRaises((BlockingIOError, OSError)):
                manager.delete(self.backups, self.jobs, old.name, self.expected(old))
        self.assertEqual(len(manager.inventory(self.backups)), 7)

    def test_restore_source_is_protected_until_request_finishes(self):
        old = self.archive(0)
        for n in range(1, 7):
            self.archive(n)
        for suffix in ('request', 'running'):
            request = self.jobs / ('a' * 32 + '.' + suffix)
            request.write_text(json.dumps({'type': 'restore', 'backup': old.name}))
            self.assertEqual(manager.prune(self.backups, self.jobs), [])
            with self.assertRaisesRegex(ValueError, 'restauration'):
                manager.delete(self.backups, self.jobs, old.name, self.expected(old))
            request.unlink()
        manager.prune(self.backups, self.jobs)
        self.assertFalse(old.exists())

    def test_delete_is_exact_and_latest_or_only_backup_stays_protected(self):
        old = self.archive(0, 'manual')
        latest = self.archive(1)
        with self.assertRaisesRegex(ValueError, 'protégée'):
            manager.delete(self.backups, self.jobs, latest.name, self.expected(latest))
        removed = manager.delete(self.backups, self.jobs, old.name, self.expected(old), ['plugins/Core.jar'])
        self.assertEqual(removed['name'], old.name)
        self.assertFalse(old.exists())
        self.assertTrue(latest.exists())
        with self.assertRaisesRegex(ValueError, 'protégée'):
            manager.delete(self.backups, self.jobs, latest.name, self.expected(latest))

    def test_stale_delete_request_refuses_changed_archive(self):
        old = self.archive(0)
        self.archive(1)
        expected = self.expected(old)
        old.write_bytes(b'new archive content')
        os.utime(old, (100, 100))
        with self.assertRaisesRegex(ValueError, 'changé'):
            manager.delete(self.backups, self.jobs, old.name, expected)
        self.assertTrue(old.exists())

    def test_path_escape_and_symlinks_are_never_deleted(self):
        outside = self.root / 'outside.tar.gz'
        outside.write_bytes(b'keep')
        self.archive(1)
        for name in ('../outside.tar.gz', '/tmp/world-1.tar.gz', 'plugin.jar', None):
            with self.assertRaises(ValueError):
                manager.delete(self.backups, self.jobs, name, {})
        link = self.backups / 'world-20261101-000000.tar.gz'
        try:
            link.symlink_to(outside)
        except OSError:
            return  # Windows may require administrator rights to create symlinks.
        with self.assertRaises(FileNotFoundError):
            manager.delete(self.backups, self.jobs, link.name, {})
        self.assertEqual(outside.read_bytes(), b'keep')
        self.assertNotIn(link.name, [path.name for path, _ in manager.inventory(self.backups)])

    def test_verification_is_cached_until_archive_changes(self):
        latest = self.archive(1)
        entry = manager.inventory(self.backups)[0]
        manager.verify_latest(entry, self.jobs, [])
        with patch.object(gzip, 'open', side_effect=AssertionError('archive decompressed twice')):
            manager.verify_latest(entry, self.jobs, [])
        latest.write_bytes(b'changed')
        with self.assertRaises(ValueError):
            manager.verify_latest(manager.inventory(self.backups)[0], self.jobs, [])


if __name__ == '__main__':
    unittest.main()
