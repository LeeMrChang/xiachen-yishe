"""Verify actual Compose interpolation while one service migrates to TCR."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(shutil.which('docker'), 'Docker Compose is available in CI')
class ComposeTest(unittest.TestCase):
    def test_per_service_digest_does_not_change_other_service_registry(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            compose = root / 'docker-compose.yml'
            shutil.copyfile(ROOT / 'deploy/docker-compose.yml', compose)
            (root / '.env').write_text('CI_ONLY=true\n')
            target = 'ccr.ccs.tencentyun.com/leemrchang-xiachen-yishe/xc-api@sha256:' + 'b' * 64
            env = dict(os.environ, XC_IMAGE_REPO='ghcr.io/legacy/repository', XC_API_TAG='old-api',
                       XC_ADMIN_TAG='old-admin', XC_API_IMAGE=target, XC_ADMIN_IMAGE='',
                       XC_API_JWT_SECRET='ci-only-api', XC_ADMIN_JWT_SECRET='ci-only-admin')
            result = subprocess.run(['docker', 'compose', '--env-file', str(root / '.env'), '-f', str(compose),
                                     'config', '--format', 'json'], env=env, check=True, text=True, capture_output=True)
            services = json.loads(result.stdout)['services']
            self.assertEqual(services['xc-api']['image'], target)
            self.assertEqual(services['xc-admin']['image'], 'ghcr.io/legacy/repository/xc-admin:old-admin')
