"""Run actual deployment scripts against disposable state and fake Docker/network commands."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
TAG = 'a' * 40
IMAGE = 'ccr.ccs.tencentyun.com/leemrchang-xiachen-yishe/xc-api@sha256:' + 'b' * 64
OLD_IMAGE = 'ghcr.io/owner/repo/xc-api:old'


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name)
        self.bin = self.path / 'bin'
        self.bin.mkdir()
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ['PATH'],
                        SERVICE='xc-api', TAG=TAG, TAG_VAR='XC_API_TAG', IMAGE_REF=IMAGE,
                        REGISTRY_USER='100054079705', REGISTRY_PASSWORD='ci-only-password',
                        HEALTH_ATTEMPTS='1', TEST_LOG=str(self.path / 'calls'),
                        TEST_STATE=str(self.path / 'state'), TEST_AUTH=str(self.path / 'auth-path'))
        self.stub('sleep', 'pass')
        self.stub('flock', 'pass')
        self.stub('timeout', '''
args = sys.argv[1:]
while args[0].startswith('--'): args.pop(0)
args.pop(0)
os.execvp(args[0], args)
''')
        deploy = self.path / 'production'
        release = deploy / 'releases' / TAG
        (release / 'deploy/scripts').mkdir(parents=True)
        (release / 'deploy/docker-compose.yml').write_text('services: {}\n')
        (release / 'deploy/scripts/healthcheck.sh').write_text('# healthcheck\n')
        self.initial_env = 'XC_API_TAG=old\nXC_ADMIN_IMAGE=ghcr.io/owner/repo/xc-admin:old-admin\n'
        (deploy / '.env').write_text(self.initial_env)
        self.deploy = deploy
        self.env.update(DEPLOY_PATH=str(deploy), RELEASE_DIR=str(release))
        self.stub('docker', '''
from pathlib import Path
args = sys.argv[1:]
assert 'ci-only-password' not in args, 'Password must never be passed as an argument'
assert args[0] != 'build', 'Production must not compile images'
open(os.environ['TEST_LOG'], 'a').write('docker ' + ' '.join(args) + '\\n')
state = Path(os.environ['TEST_STATE'])
previous = os.environ.get('PREVIOUS_REF', 'ghcr.io/owner/repo/xc-api:old')
if args[0] == 'login':
    assert sys.stdin.read() == 'ci-only-password'
    config = Path(os.environ['DOCKER_CONFIG'])
    (config / 'config.json').write_text('{"auths":{}}')
    Path(os.environ['TEST_AUTH']).write_text(str(config))
    sys.exit(24 if os.environ.get('FAIL_LOGIN') else 0)
elif args[0] == 'pull':
    assert 'REGISTRY_PASSWORD' not in os.environ
    assert '@sha256:' in args[-1]
    sys.exit(23 if os.environ.get('FAIL_PULL') else 0)
elif args[:2] == ['image', 'inspect']:
    print('sha256:target' if args[-1] == '{{.Id}}' else ('wrong' if os.environ.get('BAD_REVISION') else os.environ['TAG']))
elif args[:2] == ['compose', 'up']:
    state.write_text(os.environ['XC_API_IMAGE'])
elif args[0] == 'inspect':
    fmt = args[-1]
    if fmt == '{{.Config.Image}}': print(previous)
    elif fmt == '{{.State.Running}}': print('true')
    elif fmt == '{{.Image}}':
        is_old = not state.exists() or state.read_text() == previous
        print('sha256:old' if is_old else ('sha256:wrong' if os.environ.get('WRONG_IMAGE') else 'sha256:target'))
''')
        self.stub('curl', '''
if sys.argv[-1].endswith('/actuator/health'): print('{"status":"UP"}')
else: print('{"code":50000}' if os.environ.get('BAD_BUSINESS') else '{"code":0,"data":{}}')
''')

    def stub(self, name, code):
        p = self.bin / name
        p.write_text('#!/usr/bin/env python3\nimport os, sys\n' + code)
        p.chmod(0o755)

    def run_script(self, name):
        return subprocess.run(['bash', str(ROOT / 'deploy/scripts' / name)],
                              env=self.env, cwd=ROOT, text=True, capture_output=True, timeout=10)

    def assert_auth_removed(self):
        location = Path((self.path / 'auth-path').read_text())
        self.assertFalse(location.exists(), 'Temporary registry credentials must be removed')

    def test_pull_verifies_digest_and_revision_without_restarting_services(self):
        result = self.run_script('remote-pull-image.sh')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assert_auth_removed()
        self.assertNotIn('ci-only-password', result.stdout + result.stderr + (self.path / 'calls').read_text())
        self.assertNotIn('compose', (self.path / 'calls').read_text())
        self.assertEqual((self.deploy / '.env').read_text(), self.initial_env)

    def test_wrong_login_stops_before_pull_and_cleans_credentials(self):
        self.env['FAIL_LOGIN'] = '1'
        result = self.run_script('remote-pull-image.sh')
        self.assertEqual(result.returncode, 24)
        self.assert_auth_removed()
        self.assertNotIn('docker pull', (self.path / 'calls').read_text())

    def test_pull_failure_keeps_production_and_cleans_credentials(self):
        self.env['FAIL_PULL'] = '1'
        result = self.run_script('remote-pull-image.sh')
        self.assertEqual(result.returncode, 23)
        self.assert_auth_removed()
        self.assertNotIn('compose', (self.path / 'calls').read_text())
        self.assertEqual((self.deploy / '.env').read_text(), self.initial_env)

    def test_bad_revision_does_not_activate_image(self):
        self.env['BAD_REVISION'] = '1'
        result = self.run_script('remote-pull-image.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assert_auth_removed()
        self.assertNotIn('compose', (self.path / 'calls').read_text())

    def test_mutable_or_foreign_references_fail_before_login(self):
        for image in ['ccr.ccs.tencentyun.com/test/xc-api:latest', 'ghcr.io/test/xc-api@sha256:' + 'b' * 64]:
            with self.subTest(image=image):
                self.env['IMAGE_REF'] = image
                result = self.run_script('remote-pull-image.sh')
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse((self.path / 'calls').exists())

    def test_success_persists_only_service_digest_and_tag(self):
        result = self.run_script('remote-deploy.sh')
        self.assertEqual(result.returncode, 0, result.stderr)
        expected = self.initial_env.replace('XC_API_TAG=old', 'XC_API_TAG=' + TAG) + 'XC_API_IMAGE=' + IMAGE + '\n'
        self.assertEqual((self.deploy / '.env').read_text(), expected)

    def test_wrong_running_image_rolls_back_without_persisting_target(self):
        self.env['WRONG_IMAGE'] = '1'
        result = self.run_script('remote-deploy.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((self.deploy / '.env').read_text(), self.initial_env)
        self.assertEqual((self.path / 'state').read_text(), OLD_IMAGE)

    def test_business_failure_rolls_back_healthy_container(self):
        self.env['BAD_BUSINESS'] = '1'
        result = self.run_script('remote-deploy.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((self.deploy / '.env').read_text(), self.initial_env)
        self.assertEqual((self.path / 'state').read_text(), OLD_IMAGE)

    def test_rollback_preserves_complete_previous_digest_reference(self):
        previous = 'ccr.ccs.tencentyun.com/leemrchang-xiachen-yishe/xc-api@sha256:' + 'c' * 64
        self.env.update(PREVIOUS_REF=previous, BAD_BUSINESS='1')
        result = self.run_script('remote-deploy.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((self.path / 'state').read_text(), previous)
        self.assertEqual((self.deploy / '.env').read_text(), self.initial_env)


if __name__ == '__main__':
    unittest.main()
