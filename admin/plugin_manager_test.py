import pathlib
import tempfile
import unittest
import zipfile
from unittest.mock import patch
from plugin_manager import PluginManager, metadata, digest


def jar(filename, name='Example', version='1.0', depend=''):
    with zipfile.ZipFile(filename, 'w') as archive:
        archive.writestr('plugin.yml', f'name: {name}\nversion: "{version}"\nmain: example.Main\n' + depend)
        archive.writestr('example/Main.class', b'\xca\xfe\xba\xbe\x00\x00\x00\x45')


class PluginTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        self.data, self.jobs = self.root / 'data', self.root / 'jobs'
        (self.data / 'plugins').mkdir(parents=True)
        self.jobs.mkdir()
        self.manager = PluginManager(self.data, self.jobs)

    def tearDown(self):
        self.temp.cleanup()

    def stage(self, ident='a' * 32, **kwargs):
        source = self.jobs / (ident + '.upload')
        jar(source, **kwargs)
        self.manager.stage({'artifact': ident, 'sha512': digest(source)})

    def test_stage_does_not_touch_live_plugins_cancel_removes_artifact(self):
        self.stage()
        self.assertEqual(list((self.data / 'plugins').iterdir()), [])
        self.assertEqual(self.manager.pending()[0]['name'], 'Example')
        self.manager.cancel('a' * 32)
        self.assertEqual(self.manager.pending(), [])
        self.assertFalse((self.jobs / 'plugins' / ('a' * 32 + '.jar')).exists())

    def test_missing_dependencies_refuse_all_changes(self):
        self.stage(depend='depend: [Missing]\n')
        with self.assertRaisesRegex(ValueError, 'Missing'):
            self.manager.apply()
        self.assertEqual(list((self.data / 'plugins').iterdir()), [])
        self.assertEqual(len(self.manager.pending()), 1)

    def test_update_replaces_same_filename_and_restore_inventory_is_complete(self):
        target = self.data / 'plugins' / 'Original.jar'
        jar(target)
        self.stage(version='2.0')
        self.assertEqual(metadata(target)['version'], '1.0')
        self.manager.apply()
        self.assertEqual(metadata(target)['version'], '2.0')
        self.assertEqual(len(list((self.data / 'plugins').glob('*.jar'))), 1)
        self.assertEqual(self.manager.pending(), [])

    def test_modified_live_plugin_is_not_overwritten(self):
        target = self.data / 'plugins' / 'Original.jar'
        jar(target)
        self.stage(version='2.0')
        jar(target, version='3.0')
        with self.assertRaisesRegex(ValueError, 'changé'):
            self.manager.apply()
        self.assertEqual(metadata(target)['version'], '3.0')

    def test_failed_second_copy_rolls_back_first(self):
        self.stage()
        self.stage('b' * 32, name='Second')
        import shutil
        original = shutil.copy2
        def copy(source, target):
            if str(source).endswith('b' * 32 + '.jar'):
                raise OSError('simulated disk error')
            return original(source, target)
        with patch('plugin_manager.shutil.copy2', side_effect=copy):
            with self.assertRaises(OSError):
                self.manager.apply()
        self.assertEqual(list((self.data / 'plugins').glob('*.jar')), [])
        self.assertEqual(len(self.manager.pending()), 2)

    def test_core_and_arbitrary_zip_are_rejected_and_cleaned(self):
        with self.assertRaisesRegex(ValueError, 'socle'):
            self.stage(name='BlueMap')
        self.assertFalse((self.jobs / ('a' * 32 + '.upload')).exists())
        source = self.jobs / ('b' * 32 + '.upload')
        with zipfile.ZipFile(source, 'w') as archive:
            archive.writestr('../escape.txt', 'no')
        with self.assertRaises(ValueError):
            self.manager.stage({'artifact': 'b' * 32, 'sha512': digest(source)})
        self.assertFalse(source.exists())

if __name__ == '__main__':
    unittest.main()
