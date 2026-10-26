#!/usr/bin/env python3
"""Move the Compose application database to a fresh PostgreSQL 17 Alpine volume.

Run after applying the security patch, before rebuilding the running stack.
The old volume is retained. Application services are stopped during the copy.
Backups contain private application data; keep deploy/backups private.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import uuid


ROOT = Path(__file__).resolve().parents[1]
LEGACY_IMAGE = "postgres:17.11-trixie"
DATABASE_PATH = "/var/lib/postgresql/data"


def command(args, *, capture=False, **kwargs):
    return subprocess.run(args, check=True, text=capture,
                          stdout=subprocess.PIPE if capture else kwargs.pop("stdout", None),
                          **kwargs)


def compose_command(env_file):
    if shutil.which("docker-compose"):
        executable = ["docker-compose"]
    else:
        command(["docker", "compose", "version"], capture=True)
        executable = ["docker", "compose"]
    return executable + ["--env-file", str(env_file), "-f", str(ROOT / "compose.deploy.yaml")]


def stop_container(name):
    subprocess.run(["docker", "stop", "--time", "30", name],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(["docker", "rm", name],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def wait_database(container):
    # pg_isready alone can see the temporary initdb server before init scripts finish.
    probe = 'test "$(cat /proc/1/comm)" = postgres && pg_isready -q -U postgres -d "$POSTGRES_DB"'
    for _ in range(120):
        result = subprocess.run(["docker", "exec", container, "sh", "-c", probe],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if result.returncode == 0:
            return
        time.sleep(1)
    raise RuntimeError(f"Database did not become ready: {container}")


def identifier(value):
    return '"' + value.replace('"', '""') + '"'


def sql(container, database, query):
    result = command(["docker", "exec", container, "psql", "-X", "-A", "-t",
                      "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", database,
                      "-c", query], capture=True)
    return result.stdout.strip()


def fingerprint(container, database):
    tables = json.loads(sql(container, database, """
        SELECT COALESCE(json_agg(json_build_array(schemaname,tablename)), '[]'::json)
        FROM pg_tables WHERE schemaname <> 'information_schema' AND schemaname !~ '^pg_'
    """))
    result = {}
    for schema, table in sorted(tables):
        name = identifier(schema) + "." + identifier(table)
        # Use the C collation for deterministic comparison across glibc and musl.
        query = f'COPY (SELECT row_to_json(t)::text FROM {name} t ORDER BY row_to_json(t)::text COLLATE "C") TO STDOUT'
        digest = hashlib.sha256()
        rows = 0
        with subprocess.Popen(["docker", "exec", container, "psql", "-X", "-q",
                               "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", database,
                               "-c", query], stdout=subprocess.PIPE) as process:
            while chunk := process.stdout.read(1024 * 1024):
                digest.update(chunk)
                rows += chunk.count(b"\n")
            if process.wait() != 0:
                raise RuntimeError(f"Unable to verify table {name}")
        result[name] = {"rows": rows, "sha256": digest.hexdigest()}
    result["sequences"] = json.loads(sql(container, database, """
        SELECT COALESCE(json_agg(json_build_array(schemaname,sequencename,last_value)
            ORDER BY schemaname,sequencename), '[]'::json) FROM pg_sequences
        WHERE schemaname !~ '^pg_' AND schemaname <> 'information_schema'
    """))
    return result


def update_volume(env_file, volume):
    original = env_file.read_text(encoding="utf-8-sig")
    line = "POSTGRES_VOLUME_NAME=" + volume
    updated, replaced = re.subn(r"(?m)^POSTGRES_VOLUME_NAME=.*$", line, original)
    if not replaced:
        updated = original.rstrip() + "\n" + line + "\n"
    temporary = env_file.with_name(env_file.name + ".migration-tmp")
    try:
        with temporary.open("w", encoding="utf-8", newline="\n") as handle:
            os.chmod(temporary, 0o600)
            handle.write(updated)
        os.replace(temporary, env_file)
    finally:
        temporary.unlink(missing_ok=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env.deploy")
    args = parser.parse_args(argv)
    env_file = args.env_file.resolve()
    compose = compose_command(env_file)
    config = json.loads(command(compose + ["config", "--format", "json"], capture=True).stdout)
    postgres = config["services"]["postgres"]
    settings = postgres["environment"]
    database = settings["POSTGRES_DB"]
    old_volume = config["volumes"]["app_postgres_data"]["name"]
    app_image = postgres["image"]
    command(["docker", "volume", "inspect", old_volume], capture=True)
    token = time.strftime("%Y%m%d-%H%M%S", time.gmtime()) + "-" + uuid.uuid4().hex[:8]
    new_volume = "leap-pg17-alpine-" + token
    source, target = "leap-migrate-source-" + token, "leap-migrate-target-" + token
    backup = ROOT / "deploy/backups" / token
    backup.mkdir(parents=True, mode=0o700)
    archive = backup / "database.dump"
    original_env = env_file.read_bytes()
    backup.joinpath("env.original").write_bytes(original_env)
    os.chmod(backup / "env.original", 0o600)
    runtime_env = dict(os.environ, **{str(k): str(v) for k, v in settings.items()})
    started = False
    switched = False
    try:
        # Build/pull first, keeping the old application available during slow downloads.
        command(["docker", "build", "--pull", "-t", app_image, str(ROOT / "deploy/postgres")])
        inspect = subprocess.run(["docker", "run", "--rm", "--network", "none", "--entrypoint", "sh",
                                  "-v", old_volume + ":/old:ro", app_image,
                                  "-c", "test -f /old/.leap-alpine-v1"],
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if inspect.returncode == 0:
            print("This volume already uses the Alpine runtime; no migration is needed.")
            return 0
        if inspect.returncode != 1:
            raise RuntimeError("Unable to inspect the existing database volume")
        command(["docker", "pull", LEGACY_IMAGE])
        print("Stopping application writers and the existing database for a consistent copy...")
        started = True
        command(compose + ["stop", "frontend", "trading-platform", "market-data-service", "postgres"])
        shared = ["-e", "POSTGRES_DB", "-e", "POSTGRES_PASSWORD", "-e", "APP_DB_USERNAME",
                  "-e", "APP_DB_PASSWORD", "-e", "TZ=UTC"]
        command(["docker", "run", "-d", "--name", source, "--network", "none",
                 "-v", old_volume + ":" + DATABASE_PATH, *shared, LEGACY_IMAGE], env=runtime_env)
        wait_database(source)
        before = fingerprint(source, database)
        with archive.open("wb") as handle:
            os.chmod(archive, 0o600)
            command(["docker", "exec", source, "pg_dump", "-U", "postgres", "-d", database,
                     "--format=custom", "--no-owner", "--no-acl"], stdout=handle)
        if archive.stat().st_size == 0:
            raise RuntimeError("Database backup is empty")
        command(["docker", "volume", "create", new_volume], capture=True)
        command(["docker", "run", "-d", "--name", target, "--network", "none",
                 "-v", new_volume + ":" + DATABASE_PATH, *shared, app_image], env=runtime_env)
        wait_database(target)
        with archive.open("rb") as handle:
            command(["docker", "exec", "-i", target, "pg_restore", "-U", "postgres", "-d", database,
                     "--role", settings["APP_DB_USERNAME"], "--clean", "--if-exists",
                     "--no-owner", "--no-acl", "--exit-on-error"], stdin=handle)
        after = fingerprint(target, database)
        backup.joinpath("verification.json").write_text(json.dumps({
            "source_volume": old_volume, "target_volume": new_volume,
            "source": before, "target": after,
        }, indent=2), encoding="utf-8")
        if before != after:
            raise RuntimeError("Restored table data or sequence values differ; the existing volume remains selected")
        stop_container(target)
        stop_container(source)
        if env_file.read_bytes() != original_env:
            raise RuntimeError("The environment file changed during migration; it was not overwritten")
        switched = True
        update_volume(env_file, new_volume)
        new_env = dict(os.environ, POSTGRES_VOLUME_NAME=new_volume)
        command(compose + ["up", "-d", "--no-build", "--wait", "--wait-timeout", "120", "postgres"], env=new_env)
        print("Migration verified. Existing volume retained: " + old_volume)
        print("Private backup and verification: " + str(backup))
        print("Rebuild/start the application with your usual Compose up -d --build command.")
        return 0
    except BaseException:
        if started:
            stop_container(target)
            stop_container(source)
            # The legacy image still opens the retained source volume. Keep the app
            # stopped until the operator has read the failure and restarted it.
            if switched:
                env_file.write_bytes(original_env)
                os.chmod(env_file, 0o600)
            command(["docker", "tag", LEGACY_IMAGE, app_image])
            rollback_env = dict(os.environ, POSTGRES_VOLUME_NAME=old_volume)
            try:
                command(compose + ["up", "-d", "--no-build", "--wait", "--wait-timeout", "120", "postgres"], env=rollback_env)
            except subprocess.CalledProcessError:
                print("Automatic database restart failed; the original volume is retained: " + old_volume, file=sys.stderr)
            print("Migration failed. Application services remain stopped. Backup: " + str(backup), file=sys.stderr)
        raise
    finally:
        stop_container(target)
        stop_container(source)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, subprocess.CalledProcessError, KeyError, ValueError) as error:
        print("ERROR: " + str(error), file=sys.stderr)
        raise SystemExit(1)
