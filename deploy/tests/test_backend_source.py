import hashlib
import importlib.util
import io
from pathlib import Path
from types import SimpleNamespace
import tarfile
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('backend_source', Path(__file__).resolve().parents[1] / 'scripts/backend-source.py')
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)
TAG = 'b' * 40


class BackendSourceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name) / 'project'
        self.release = Path(self.tmp.name) / 'release'
        self.root.mkdir()
        for name in source.FIXED - {'source-revision.txt'}:
            p = self.root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('build input')
        for module in source.MODULES:
            p = self.root / module / 'src/main/java/Main.java'
            p.parent.mkdir(parents=True)
            p.write_text('class Main {}')

    def test_round_trip_includes_sources_but_excludes_local_secrets_and_outputs(self):
        for name in ['xc-api/src/main/resources/application-local.yml', 'xc-api/src/main/resources/.env', 'xc-api/target/app.jar', '.env']:
            p = self.root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('secret or generated output')
        source.pack(self.root, self.release, TAG)
        extracted = source.unpack(self.release, TAG)
        self.assertTrue((extracted / 'xc-api/src/main/java/Main.java').exists())
        self.assertFalse((extracted / 'xc-api/src/main/resources/application-local.yml').exists())
        self.assertFalse((extracted / 'xc-api/src/main/resources/.env').exists())
        self.assertFalse((extracted / 'xc-api/target/app.jar').exists())
        self.assertFalse((extracted / '.env').exists())
        self.assertEqual((extracted / 'source-revision.txt').read_text().strip(), TAG)

    def test_symlink_source_is_rejected(self):
        (self.root / 'xc-api/src/main/java/linked.java').symlink_to(self.root / 'pom.xml')
        with self.assertRaisesRegex(ValueError, 'Symlinked'):
            source.pack(self.root, self.release, TAG)

    def test_checksum_and_wrong_revision_fail_before_build_context_creation(self):
        archive = source.pack(self.root, self.release, TAG)
        with self.assertRaisesRegex(ValueError, 'revision mismatch'):
            source.unpack(self.release, 'c' * 40)
        archive.write_bytes(b'corrupt')
        with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
            source.unpack(self.release, TAG)
        self.assertFalse(list(self.release.glob('source-*')))

    def test_unsafe_members_and_links_are_rejected(self):
        self.release.mkdir()
        for name, kind in [('../escape', tarfile.REGTYPE), ('/tmp/escape', tarfile.REGTYPE),
                           ('.env', tarfile.REGTYPE), ('pom.xml', tarfile.SYMTYPE)]:
            with self.subTest(name=name, kind=kind):
                archive = self.release / 'source.tar.gz'
                with tarfile.open(archive, 'w:gz') as tar:
                    info = tarfile.TarInfo(name)
                    info.type = kind
                    info.linkname = '/tmp/escape' if kind == tarfile.SYMTYPE else ''
                    tar.addfile(info, io.BytesIO())
                (self.release / 'source.tar.gz.sha256').write_text(hashlib.sha256(archive.read_bytes()).hexdigest() + '  source.tar.gz\n')
                with self.assertRaisesRegex(ValueError, 'Unsafe'):
                    source.unpack(self.release, TAG)

    def test_resource_preflight_rejects_insufficient_ram_and_disk(self):
        meminfo = self.root / 'meminfo'
        meminfo.write_text('MemAvailable: 1000000 kB\n')
        with self.assertRaisesRegex(ValueError, 'RAM'):
            source.check_resources(self.root, meminfo)
        meminfo.write_text('MemAvailable: 3000000 kB\n')
        with patch.object(source.shutil, 'disk_usage', return_value=SimpleNamespace(free=1024)):
            with self.assertRaisesRegex(ValueError, 'disk'):
                source.check_resources(self.root, meminfo)


if __name__ == '__main__':
    unittest.main()
