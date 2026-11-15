"""Run real release scripts with disposable files and fake network/Docker commands."""
import gzip
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
TAG = 'a' * 40


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name)
        self.bin = self.path / 'bin'
        self.bin.mkdir()
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ['PATH'],
                        RUNNER_TEMP=str(self.path), HOST='example.invalid', DEPLOY_USER='ci',
                        DEPLOY_PATH=str(self.path / 'production'), SERVICE='xc-api', TAG=TAG,
                        TAG_VAR='XC_API_TAG', GITHUB_REPOSITORY='Owner/Repo', HEALTH_ATTEMPTS='1',
                        TEST_LOG=str(self.path / 'calls'), TEST_STATE=str(self.path / 'state'))
        # macOS temporary paths contain dots; the real remote path remains strictly validated.
        self.env['DEPLOY_PATH'] = '/tmp/xc-release-test'
        self.stub('sleep', 'pass')
        self.stub('flock', 'pass')
        self.stub('timeout', '''
args = sys.argv[1:]
while args[0].startswith('--'): args.pop(0)
args.pop(0)
os.execvp(args[0], args)
''')
        self.stub('ssh', "open(os.environ['TEST_LOG'], 'a').write('ssh\\n')")
        self.stub('scp', "open(os.environ['TEST_LOG'], 'a').write('scp ' + sys.argv[-2].split('/')[-1] + '\\n')")
        self.stub('sha256sum', '''
import hashlib
if sys.argv[1] == '--check':
    expected, name = open(sys.argv[2]).read().split()
    sys.exit(0 if hashlib.sha256(open(name, 'rb').read()).hexdigest() == expected else 1)
name = sys.argv[1]
print(hashlib.sha256(open(name, 'rb').read()).hexdigest(), name)
''')

    def stub(self, name, code):
        p = self.bin / name
        p.write_text('#!/usr/bin/env python3\nimport os, sys\n' + code)
        p.chmod(0o755)

    def run_script(self, name, *args):
        return subprocess.run(['bash', str(ROOT / 'deploy/scripts' / name), *args],
                              env=self.env, cwd=ROOT, text=True, capture_output=True, timeout=10)

    def archive(self):
        folder = self.path / 'deployment-image'
        folder.mkdir()
        with gzip.open(folder / 'image.tar.gz', 'wb') as f:
            f.write(b'image data')

    def test_transfer_retries_and_only_publishes_checksum_after_success(self):
        self.archive()
        self.stub('rsync', '''
from pathlib import Path
p = Path(os.environ['TEST_STATE'])
attempt = int(p.read_text()) + 1 if p.exists() else 1
p.write_text(str(attempt))
assert '-z' in sys.argv and '--inplace' in sys.argv and '--stats' in sys.argv
sys.exit(23 if attempt == 1 else 0)
''')
        result = self.run_script('backend-release.sh', 'upload')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('exit=23', result.stdout)
        self.assertIn('attempt 2 succeeded', result.stdout)
        self.assertIn('scp image.tar.sha256', (self.path / 'calls').read_text())

    def test_exhausted_transfer_never_loads_or_publishes_checksum(self):
        self.archive()
        self.stub('rsync', 'sys.exit(23)')
        result = self.run_script('backend-release.sh', 'upload')
        self.assertEqual(result.returncode, 23)
        self.assertEqual(result.stdout.count('failed: exit=23'), 3)
        self.assertNotIn('scp', (self.path / 'calls').read_text())

    def remote_setup(self):
        deploy = self.path / 'production'
        release = deploy / 'releases' / TAG
        (release / 'deploy/scripts').mkdir(parents=True)
        (release / 'deploy/docker-compose.yml').write_text('services: {}\n')
        (release / 'deploy/scripts/healthcheck.sh').write_text('# healthcheck\n')
        (deploy / '.env').write_text('XC_API_TAG=old\n')
        self.env.update(DEPLOY_PATH=str(deploy), RELEASE_DIR=str(release),
                        IMAGE_REF=f'ghcr.io/owner/repo/xc-api:{TAG}')
        self.stub('docker', '''
args = sys.argv[1:]
open(os.environ['TEST_LOG'], 'a').write('docker ' + ' '.join(args) + '\\n')
if args[:2] == ['image', 'inspect']:
    print('sha256:target' if args[-1] == '{{.Id}}' else ('wrong' if os.environ.get('BAD_REVISION') else os.environ['TAG']))
elif args[:2] == ['compose', 'up']:
    open(os.environ['TEST_STATE'], 'w').write(os.environ['XC_API_TAG'])
elif args[0] == 'inspect':
    fmt = args[-1]
    if fmt == '{{.Config.Image}}': print('ghcr.io/owner/repo/xc-api:old')
    elif fmt == '{{.State.Running}}': print('true')
    elif fmt == '{{.Image}}': print('sha256:wrong' if os.environ.get('WRONG_IMAGE') else 'sha256:target')
''')
        self.stub('curl', '''
if sys.argv[-1].endswith('/actuator/health'): print('{"status":"UP"}')
else: print('{"code":50000}' if os.environ.get('BAD_BUSINESS') else '{"code":0,"data":{}}')
''')
        return deploy, release

    def test_corrupt_archive_is_rejected_before_docker_load(self):
        _, release = self.remote_setup()
        (release / 'image.tar').write_bytes(b'corrupt')
        (release / 'image.tar.sha256').write_text(hashlib.sha256(b'original').hexdigest() + '  image.tar\n')
        result = self.run_script('remote-load-image.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.path / 'calls').exists())

    def valid_archive(self, release):
        content = b'valid image'
        (release / 'image.tar').write_bytes(content)
        (release / 'image.tar.sha256').write_text(hashlib.sha256(content).hexdigest() + '  image.tar\n')

    def test_verified_loaded_image_becomes_next_delta_basis(self):
        deploy, release = self.remote_setup()
        self.valid_archive(release)
        result = self.run_script('remote-load-image.sh')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((deploy / 'image-cache/xc-api/image.tar').read_bytes(), b'valid image')

    def test_wrong_revision_never_becomes_next_delta_basis(self):
        deploy, release = self.remote_setup()
        self.valid_archive(release)
        self.env['BAD_REVISION'] = '1'
        result = self.run_script('remote-load-image.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((deploy / 'image-cache/xc-api/image.tar').exists())

    def test_success_persists_target_tag(self):
        deploy, _ = self.remote_setup()
        result = self.run_script('remote-deploy.sh')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((deploy / '.env').read_text(), f'XC_API_TAG={TAG}\n')

    def test_wrong_running_image_rolls_back_without_persisting_target(self):
        deploy, _ = self.remote_setup()
        self.env['WRONG_IMAGE'] = '1'
        result = self.run_script('remote-deploy.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((deploy / '.env').read_text(), 'XC_API_TAG=old\n')
        self.assertEqual((self.path / 'state').read_text(), 'old')

    def test_business_failure_rolls_back_healthy_container(self):
        deploy, _ = self.remote_setup()
        self.env['BAD_BUSINESS'] = '1'
        result = self.run_script('remote-deploy.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((deploy / '.env').read_text(), 'XC_API_TAG=old\n')
        self.assertEqual((self.path / 'state').read_text(), 'old')


if __name__ == '__main__':
    unittest.main()
