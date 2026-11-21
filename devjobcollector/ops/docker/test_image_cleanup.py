import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import shutil


SCRIPT = Path(__file__).with_name('docker-image-cleanup.sh')
BASH = os.environ.get('BASH_FOR_TESTS', 'bash')
MOCK = r'''#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >> "$MOCK_LOG"
case "$1 $2" in
  'ps -aq')
    if [[ "${3:-}" != --filter ]]; then echo container1; fi ;;
  'inspect --format') echo sha256:12 ;;
  'image ls')
    for i in $(seq 1 14); do echo "sha256:$i"; done
    echo sha256:1 ;;
  'image inspect')
    id="${@: -1}"; n="${id#sha256:}"
    [[ "$id" == bad ]] && exit 1
    case "$4" in
      *Created*) printf '2026-11-%02dT00:00:00Z %s\n' "$((15-n))" "$id" ;;
      *RepoTags*)
        echo "ghcr.io/aerofleet/devjobcollector:tag$n"
        if [[ "$n" == 13 ]]; then echo other/repository:tag; fi
        if [[ "$n" == 14 ]]; then echo ghcr.io/aerofleet/devjobcollector:latest; fi ;;
      *) if [[ "$id" == *:tag* ]]; then echo "sha256:${id##*:tag}"; else echo "$id"; fi ;;
    esac ;;
  'image rm') [[ "${MOCK_FAIL:-0}" != 1 ]] ;;
  *) exit 1 ;;
esac
'''


class CleanupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        docker = self.directory / 'docker'
        docker.write_text(MOCK, encoding='utf-8', newline='\n')
        docker.chmod(0o755)
        flock = self.directory / 'flock'
        flock.write_text('#!/usr/bin/env bash\nexit 0\n', encoding='utf-8', newline='\n')
        flock.chmod(0o755)
        self.log = self.directory / 'calls'
        self.env = dict(os.environ, PATH=str(self.directory) + os.pathsep + os.environ['PATH'],
                        MOCK_LOG=str(self.log), DJC_DEPLOY_LOCK_HELD='1')

    def run_cleanup(self, *args, fail=False):
        env = dict(self.env, MOCK_FAIL='1' if fail else '0')
        return subprocess.run([BASH, str(SCRIPT), *args], env=env, text=True,
                              capture_output=True, timeout=30)

    def test_default_dry_run_deduplicates_and_protects_containers(self):
        result = self.run_cleanup()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('[KEEP] sha256:10', result.stdout)
        self.assertIn('[KEEP] sha256:12', result.stdout)
        self.assertIn('[DRY-RUN] ghcr.io/aerofleet/devjobcollector:tag11', result.stdout)
        self.assertNotIn('other/repository', result.stdout)
        self.assertNotIn(':latest', result.stdout)
        self.assertNotIn('image rm', self.log.read_text())

    def test_apply_requires_and_respects_rollback_image(self):
        self.assertNotEqual(0, self.run_cleanup('--apply').returncode)
        result = self.run_cleanup('--apply', '--protect', 'sha256:11')
        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.log.read_text()
        self.assertNotIn('image rm ghcr.io/aerofleet/devjobcollector:tag11', calls)
        self.assertIn('image rm ghcr.io/aerofleet/devjobcollector:tag13', calls)
        self.assertNotIn('image rm other/', calls)

    def test_removal_failure_is_reported(self):
        result = self.run_cleanup('--apply', '--protect', 'sha256:11', fail=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('[FAILED]', result.stderr)

    def test_missing_protected_image_fails_closed(self):
        result = self.run_cleanup('--apply', '--protect', 'bad')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('image rm', self.log.read_text())

    @unittest.skipIf(os.name == 'nt' or not shutil.which('flock'), 'requires Linux flock')
    def test_concurrent_cleanup_lock_fails_closed(self):
        import fcntl
        lock = self.directory / 'lock'
        copy = self.directory / 'cleanup.sh'
        copy.write_text(SCRIPT.read_text().replace('/run/lock/djc-image-cleanup.lock', str(lock)))
        (self.directory / 'flock').unlink()
        env = dict(self.env)
        env.pop('DJC_DEPLOY_LOCK_HELD')
        with lock.open('w') as held:
            fcntl.flock(held, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = subprocess.run([BASH, str(copy)], env=env, text=True, capture_output=True, timeout=30)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('already running', result.stderr)
        self.assertFalse(self.log.exists())


if __name__ == '__main__':
    unittest.main()
