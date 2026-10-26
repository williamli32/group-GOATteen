"""Exercise migration failures without touching a Docker daemon or real database."""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location(
    "postgres_runtime_migration", Path(__file__).resolve().parents[1] / "migrate-postgres-runtime.py")
migration = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(migration)


class MigrationSafetyTest(unittest.TestCase):
    def exercise(self, case):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            env_file = root / ".env.deploy"
            original = b"DEPLOY_JWT_SECRET=test-only-value\r\nPOSTGRES_VOLUME_NAME=original-data\r\n"
            env_file.write_bytes(original)
            settings = {"POSTGRES_DB": "leap_trading", "POSTGRES_PASSWORD": "test-only-password",
                        "APP_DB_USERNAME": "leap_app", "APP_DB_PASSWORD": "test-only-password"}
            config = {"services": {"postgres": {"environment": settings, "image": "leap/postgres:local"}},
                      "volumes": {"app_postgres_data": {"name": "original-data"}}}
            calls = []
            prints = io.StringIO()
            copies = {"public.orders": {"rows": 3, "sha256": "same-content"}, "sequences": [["public", "orders_id_seq", 9]]}
            fingerprints = 0

            def command(args, **kwargs):
                calls.append((args, kwargs))
                if "config" in args:
                    return subprocess.CompletedProcess(args, 0, json.dumps(config))
                if case == "build_failure" and args[:2] == ["docker", "build"]:
                    raise subprocess.CalledProcessError(1, args)
                if "pg_dump" in args and case != "empty_backup":
                    kwargs["stdout"].write(b"test-archive")
                if "pg_restore" in args:
                    self.assertIn("-target-", args[3])
                    self.assertIn("--clean", args)
                    self.assertIn("--if-exists", args)
                    if case == "restore_failure":
                        raise subprocess.CalledProcessError(1, args)
                if case == "restart_failure" and "up" in args and kwargs["env"]["POSTGRES_VOLUME_NAME"] != "original-data":
                    raise subprocess.CalledProcessError(1, args)
                return subprocess.CompletedProcess(args, 0, "")

            def fingerprint(container, database):
                nonlocal fingerprints
                fingerprints += 1
                if case == "source_failure" and fingerprints == 1:
                    raise RuntimeError("Cannot read source database")
                if case == "changed_environment" and fingerprints == 2:
                    env_file.write_text("OPERATOR_CHANGED_THIS=true\n")
                if case == "verification_failure" and fingerprints == 2:
                    return {"public.orders": {"rows": 2, "sha256": "different"}}
                return copies

            probe_status = 0 if case == "already_migrated" else (125 if case == "probe_failure" else 1)
            with mock.patch.object(migration, "ROOT", root), \
                 mock.patch.object(migration, "compose_command", return_value=["compose"]), \
                 mock.patch.object(migration, "command", side_effect=command), \
                 mock.patch.object(migration, "wait_database"), \
                 mock.patch.object(migration, "stop_container"), \
                 mock.patch.object(migration, "fingerprint", side_effect=fingerprint), \
                 mock.patch.object(migration.subprocess, "run", return_value=subprocess.CompletedProcess([], probe_status)), \
                 contextlib.redirect_stdout(prints), contextlib.redirect_stderr(prints):
                if case in ["success", "already_migrated"]:
                    self.assertEqual(0, migration.main(["--env-file", str(env_file)]))
                else:
                    with self.assertRaises((RuntimeError, subprocess.CalledProcessError)):
                        migration.main(["--env-file", str(env_file)])

            # No path deletes a data volume, including rollback and interrupted copies.
            self.assertFalse(any(args[:3] == ["docker", "volume", "rm"] for args, _ in calls))
            stopped = any(args[:2] == ["compose", "stop"] for args, _ in calls)
            if case in ["build_failure", "already_migrated", "probe_failure"]:
                self.assertFalse(stopped)
            else:
                self.assertTrue(stopped)
            if case == "success":
                text = env_file.read_text()
                self.assertIn("POSTGRES_VOLUME_NAME=leap-pg17-alpine-", text)
                self.assertIn("DEPLOY_JWT_SECRET=test-only-value", text)
                self.assertEqual(2, fingerprints)
                report = next((root / "deploy/backups").glob("*/verification.json"))
                saved = json.loads(report.read_text())
                self.assertEqual(saved["source"], saved["target"])
                self.assertEqual("original-data", saved["source_volume"])
            elif case == "changed_environment":
                self.assertEqual("OPERATOR_CHANGED_THIS=true\n", env_file.read_text())
            else:
                self.assertEqual(original, env_file.read_bytes())
            if stopped and case != "success":
                rollback = [(a, k) for a, k in calls if "up" in a and k.get("env", {}).get("POSTGRES_VOLUME_NAME") == "original-data"]
                self.assertEqual(1, len(rollback))
            self.assertFalse(env_file.with_name(".env.deploy.migration-tmp").exists())

    def test_success_switches_only_after_matching_table_and_sequence_fingerprints(self):
        self.exercise("success")

    def test_build_failure_keeps_the_existing_application_running(self):
        self.exercise("build_failure")

    def test_failed_volume_inspection_does_not_start_a_copy(self):
        self.exercise("probe_failure")

    def test_an_existing_alpine_volume_needs_no_copy(self):
        self.exercise("already_migrated")

    def test_restore_failure_preserves_the_existing_selection_and_volume(self):
        self.exercise("restore_failure")

    def test_different_restored_data_does_not_switch_volumes(self):
        self.exercise("verification_failure")

    def test_empty_backup_does_not_switch_volumes(self):
        self.exercise("empty_backup")

    def test_source_read_failure_does_not_switch_volumes(self):
        self.exercise("source_failure")

    def test_failed_new_database_start_restores_the_original_selection(self):
        self.exercise("restart_failure")

    def test_concurrent_environment_edits_are_preserved(self):
        self.exercise("changed_environment")

    def test_identifier_quotes_cannot_change_the_fingerprint_query(self):
        self.assertEqual('"odd""table"', migration.identifier('odd"table'))

    def test_fingerprint_streams_rows_and_uses_a_stable_collation(self):
        rows = b'{"id":1,"note":"escaped\\nnewline"}\n{"id":2}\n'
        child = mock.MagicMock()
        child.__enter__.return_value = child
        child.stdout = io.BytesIO(rows)
        child.wait.return_value = 0
        with mock.patch.object(migration, "sql", side_effect=['[["public","orders"]]', '[]']), \
             mock.patch.object(migration.subprocess, "Popen", return_value=child) as process:
            result = migration.fingerprint("source-container", "database")
        self.assertEqual({"rows": 2, "sha256": hashlib.sha256(rows).hexdigest()}, result['"public"."orders"'])
        self.assertIn('COLLATE "C"', process.call_args.args[0][-1])

    def test_readiness_waits_for_the_final_postgres_process(self):
        with mock.patch.object(migration.subprocess, "run", side_effect=[subprocess.CompletedProcess([], 1), subprocess.CompletedProcess([], 0)]) as run, \
             mock.patch.object(migration.time, "sleep"):
            migration.wait_database("target")
        self.assertEqual(2, run.call_count)
        self.assertIn('/proc/1/comm', run.call_args.args[0][-1])


if __name__ == "__main__":
    unittest.main()
