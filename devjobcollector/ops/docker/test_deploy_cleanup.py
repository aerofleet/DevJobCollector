import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from test_image_cleanup import BASH


DEPLOY = Path(__file__).with_name('deploy.sh')
DOCKER = r'''#!/usr/bin/env bash
printf '%s\n' "$*" >> "$MOCK_LOG"
case "$1" in
  login) cat >/dev/null ;;
  logout) : ;;
  ps) [[ "$*" == *'project=djc'* ]] && echo current || true ;;
  inspect) [[ "$*" == *'.Config.Image'* ]] && echo ghcr.io/aerofleet/devjobcollector:old || echo sha256:old ;;
  compose)
    case "$*" in
      *'up -d --no-build --force-recreate app'*) [[ "${FAIL_UP:-0}" != 1 ]] ;;
      *'ps -q app'*) echo current ;;
      *) : ;;
    esac ;;
  *) : ;;
esac
'''


class DeployTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.bundle = root / 'bundle'
        self.bundle.mkdir()
        for name in ('compose.yaml', 'devjobcollector.env', 'ghcr-token', 'ghcr-username'):
            (self.bundle / name).write_text('test\n')
        self.bin = root / 'bin'
        self.bin.mkdir()
        for name, content in {
            'docker': DOCKER,
            'flock': '#!/usr/bin/env bash\nexit "${FAIL_LOCK:-0}"\n',
            'systemctl': '#!/usr/bin/env bash\n[[ "$1" != is-active ]]\n',
            'ss': '#!/usr/bin/env bash\nexit 1\n',
            'mock_curl': '#!/usr/bin/env bash\necho \'{"status":"UP"}\'\n',
            'mock_install': '#!/usr/bin/env bash\nif [[ "$1" == -d ]]; then mkdir -p "${@: -1}"; else cp "${@: -2:1}" "${@: -1}"; fi\n',
        }.items():
            path = self.bin / name
            path.write_text(content, newline='\n')
            path.chmod(0o755)
        cleanup = self.bundle / 'docker-image-cleanup.sh'
        cleanup.write_text('#!/usr/bin/env bash\nprintf "cleanup %s\\n" "$*" >> "$MOCK_LOG"\nexit "${FAIL_CLEANUP:-0}"\n', newline='\n')
        cleanup.chmod(0o755)
        script = DEPLOY.read_text().replace('/opt/devjobcollector', (root / 'app').as_posix())
        script = script.replace('/etc/devjobcollector', (root / 'config').as_posix())
        script = script.replace('/run/lock/djc-image-cleanup.lock', (root / 'lock').as_posix())
        script = script.replace('install -', 'mock_install -')
        script = script.replace('curl -', 'mock_curl -')
        self.script = root / 'deploy.sh'
        self.script.write_text(script, newline='\n')
        self.log = root / 'calls'
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ['PATH'], MOCK_LOG=str(self.log))

    def run_deploy(self, **options):
        return subprocess.run([BASH, str(self.script), str(self.bundle)],
                              env=dict(self.env, **options), text=True, capture_output=True, timeout=30)

    def test_cleanup_failure_does_not_rollback_healthy_deploy(self):
        result = self.run_deploy(FAIL_CLEANUP='1')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('WARNING: image cleanup failed', result.stderr)
        calls = self.log.read_text()
        self.assertIn('cleanup --apply --protect sha256:old', calls)
        self.assertNotIn(' down', calls)

    def test_failed_deploy_rolls_back_without_cleanup(self):
        result = self.run_deploy(FAIL_UP='1')
        self.assertNotEqual(0, result.returncode)
        calls = self.log.read_text()
        self.assertIn(' down', calls)
        self.assertNotIn('cleanup --apply', calls)

    def test_contended_deploy_does_not_log_out_other_deployment(self):
        result = self.run_deploy(FAIL_LOCK='1')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.log.exists())
        self.assertFalse((self.bundle / 'devjobcollector.env').exists())


if __name__ == '__main__':
    unittest.main()
