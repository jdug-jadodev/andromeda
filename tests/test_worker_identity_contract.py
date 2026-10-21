from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]


class WorkerIdentityContractTests(unittest.TestCase):
    def test_runtime_args_match_whole_nul_items_with_bounded_readers(self):
        source=(ROOT/'scripts/resident-model-worker.sh').read_text()
        owned=source[source.index('owned() {'):source.index('cleanup() {')]
        self.assertEqual(owned.count('/system/bin/timeout -s KILL 1 /system/bin/grep -Fzqx -- '),3)
        self.assertIn('spawned || return 1',owned)
        self.assertNotIn("tr '\\000'",source)

    def test_cleanup_keeps_uid_start_caps_and_executable_identity_without_proc_streams(self):
        source=(ROOT/'scripts/resident-model-worker.sh').read_text()
        spawned=source[source.index('spawned() {'):source.index('owned() {')]
        for required in ('"$START"','10218','0000000000000000','readlink','/exe'):
            self.assertIn(required,spawned)
        cleanup=source[source.index('cleanup() {'):source.index('trap cleanup EXIT')]
        self.assertIn('if spawned; then kill -TERM "$PID"',cleanup)
        self.assertIn('if spawned; then kill -KILL "$PID"',cleanup)
        self.assertNotIn('/cmdline',cleanup)


if __name__=='__main__': unittest.main()
