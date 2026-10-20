import io
import pathlib
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, '/')
import worker


class WorkerTests(unittest.TestCase):
    def setUp(self):
        # Tests must never create /jobs or depend on the developer's permissions.
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        jobs = patch.object(worker, 'JOBS', pathlib.Path(directory.name))
        jobs.start()
        self.addCleanup(jobs.stop)

    def test_restore_backup_from_before_an_addon_was_installed(self):
        from plugin_manager_test import jar
        with tempfile.TemporaryDirectory() as directory:
            old_data, old_backups, old_jobs = worker.DATA, worker.BACKUPS, worker.JOBS
            try:
                root = pathlib.Path(directory)
                worker.DATA, worker.BACKUPS, worker.JOBS = root / 'data', root / 'backups', root / 'jobs'
                (worker.DATA / 'plugins').mkdir(parents=True)
                worker.BACKUPS.mkdir()
                worker.JOBS.mkdir()
                jar(worker.DATA / 'plugins' / 'BlueMap.jar', name='BlueMap')
                jar(worker.DATA / 'plugins' / 'Addon.jar')
                filename = worker.BACKUPS / 'world-20260101-000000.tar.gz'
                with tarfile.open(filename, 'w:gz') as archive:
                    archive.add(worker.DATA / 'plugins' / 'BlueMap.jar', arcname='./plugins/BlueMap.jar')
                with patch.object(worker, 'compose', return_value='There are 0 of a max of 10 players online:'):
                    worker.run_job({'type': 'restore', 'backup': filename.name})
                self.assertTrue((worker.DATA / 'plugins' / 'BlueMap.jar').exists())
                self.assertFalse((worker.DATA / 'plugins' / 'Addon.jar').exists())
            finally:
                worker.DATA, worker.BACKUPS, worker.JOBS = old_data, old_backups, old_jobs

    def test_clear_data_keeps_panel_mount_directories_but_removes_contents(self):
        with tempfile.TemporaryDirectory() as directory:
            previous = worker.DATA
            try:
                worker.DATA = pathlib.Path(directory)
                mount = worker.DATA / 'plugins/DuckCatTowers/schematics'
                mount.mkdir(parents=True)
                inode = mount.stat().st_ino
                (mount / 'old.schem').write_bytes(b'old')
                (worker.DATA / 'world').mkdir()
                worker.clear_data()
                self.assertEqual(mount.stat().st_ino, inode)
                self.assertEqual(list(mount.iterdir()), [])
                self.assertFalse((worker.DATA / 'world').exists())
            finally:
                worker.DATA = previous

    def test_restore_refuses_archives_missing_plugin_binaries_before_stopping_game(self):
        with tempfile.TemporaryDirectory() as directory:
            old_data, old_backups = worker.DATA, worker.BACKUPS
            try:
                root = pathlib.Path(directory)
                worker.DATA, worker.BACKUPS = root / 'data', root / 'backups'
                (worker.DATA / 'plugins').mkdir(parents=True)
                worker.BACKUPS.mkdir()
                (worker.DATA / 'plugins' / 'Required.jar').write_bytes(b'plugin')
                filename = worker.BACKUPS / 'world-20260101-000000.tar.gz'
                with tarfile.open(filename, 'w:gz') as archive:
                    info = tarfile.TarInfo('./world/level.dat')
                    info.size = 4
                    archive.addfile(info, io.BytesIO(b'data'))
                with patch.object(worker, 'compose') as compose:
                    with self.assertRaises(ValueError):
                        worker.run_job({'type': 'restore', 'backup': filename.name})
                    compose.assert_not_called()
                self.assertEqual((worker.DATA / 'plugins' / 'Required.jar').read_bytes(), b'plugin')
            finally:
                worker.DATA, worker.BACKUPS = old_data, old_backups

    def test_map_policy_and_rollback(self):
        with tempfile.TemporaryDirectory() as directory:
            previous = worker.DATA
            try:
                worker.DATA = pathlib.Path(directory)
                config = worker.DATA / 'plugins' / 'BlueMap' / 'plugin.conf'
                config.parent.mkdir(parents=True)
                original = 'enabled: true\nplayer-render-limit: -1\nother-setting: true\n'
                config.write_text(original, encoding='utf-8')
                with patch.object(worker, 'compose', return_value='OK'):
                    worker.run_job({'type': 'map-policy', 'policy': 'idle'})
                self.assertIn('player-render-limit: 1', config.read_text())
                self.assertIn('other-setting: true', config.read_text())
                with self.assertRaises(ValueError):
                    worker.run_job({'type': 'map-policy', 'policy': 'unknown'})
                before = config.read_text()
                with patch.object(worker, 'compose', side_effect=[RuntimeError('reload failed'), 'OK']):
                    with self.assertRaises(RuntimeError):
                        worker.run_job({'type': 'map-policy', 'policy': 'live'})
                self.assertEqual(config.read_text(), before)
            finally:
                worker.DATA = previous
    def test_stage_properties_preserves_other_values(self):
        with tempfile.TemporaryDirectory() as directory:
            previous = worker.DATA
            try:
                worker.DATA = pathlib.Path(directory)
                prop = worker.DATA / 'server.properties'
                prop.write_text('online-mode=true\nmax-players=10\npvp=true\n', encoding='utf-8')
                worker.stage_properties({'maxPlayers': 12, 'pvp': False, 'motd': 'Mon monde'})
                result = prop.read_text(encoding='utf-8')
                self.assertIn('online-mode=true', result)
                self.assertIn('max-players=12', result)
                self.assertIn('pvp=false', result)
                self.assertIn('motd=Mon monde', result)
                with self.assertRaises(ValueError):
                    worker.stage_properties({'maxPlayers': 1000})
            finally:
                worker.DATA = previous

    def test_archive_paths_are_confined(self):
        with tempfile.TemporaryDirectory() as directory:
            filename = pathlib.Path(directory) / 'bad.tar.gz'
            with tarfile.open(filename, 'w:gz') as archive:
                payload = b'bad'
                info = tarfile.TarInfo('../escape.txt')
                info.size = len(payload)
                archive.addfile(info, io.BytesIO(payload))
            with tarfile.open(filename, 'r:gz') as archive:
                with self.assertRaises(ValueError):
                    worker.safe_members(archive)

    def test_extract_normal_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            filename = root / 'good.tar.gz'
            with tarfile.open(filename, 'w:gz') as archive:
                payload = b'world data'
                info = tarfile.TarInfo('./world/level.dat')
                info.size = len(payload)
                archive.addfile(info, io.BytesIO(payload))
            previous = worker.DATA
            try:
                worker.DATA = root / 'data'
                worker.DATA.mkdir()
                worker.extract_archive(filename)
                self.assertEqual((worker.DATA / 'world' / 'level.dat').read_bytes(), b'world data')
            finally:
                worker.DATA = previous


if __name__ == '__main__':
    unittest.main()
