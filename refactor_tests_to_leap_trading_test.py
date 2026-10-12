from pathlib import Path
import re
import sys

ROOT = Path.cwd()

files = {
    "trade_reconstruction": ROOT / "backend/trading-platform/src/test/java/com/goatteen/trading/audit/TradeReconstructionIntegrationTest.java",
    "account_isolation": ROOT / "backend/trading-platform/src/test/java/com/goatteen/trading/execution/OrderExecutionAccountIsolationTest.java",
    "rollback": ROOT / "backend/trading-platform/src/test/java/com/goatteen/trading/execution/OrderExecutionRollbackIntegrationTest.java",
    "lifecycle": ROOT / "backend/trading-platform/src/test/java/com/goatteen/trading/execution/OrderExecutionLifecycleIntegrationTest.java",
    "concurrent": ROOT / "backend/trading-platform/src/test/java/com/goatteen/trading/execution/OrderExecutionConcurrentDuplicateProcessingTest.java",
    "restart": ROOT / "backend/trading-platform/src/test/java/com/goatteen/trading/recovery/ApplicationRestartRecoveryIntegrationTest.java",
    "test_yaml": ROOT / "backend/trading-platform/src/test/resources/application-test.yaml",
}

missing = [str(p) for p in files.values() if not p.exists()]
if missing:
    print("Run this script from the repository root.")
    print("Missing:")
    for p in missing:
        print("  " + p)
    sys.exit(1)


def write_changed(path: Path, old: str, new: str):
    if old == new:
        print(f"UNCHANGED {path}")
        return
    backup = path.with_suffix(path.suffix + ".bak")
    if not backup.exists():
        backup.write_text(old, encoding="utf-8")
    path.write_text(new, encoding="utf-8")
    print(f"UPDATED   {path}")


def remove_dynamic_property_imports(text: str) -> str:
    text = text.replace(
        "import org.springframework.test.context.DynamicPropertyRegistry;\n", ""
    )
    text = text.replace(
        "import org.springframework.test.context.DynamicPropertySource;\n", ""
    )
    return text


def remove_test_user_assertion(text: str, constant_name: str = "TEST_USER") -> str:
    pattern = rf'''\s*assertEquals\(\s*{re.escape(constant_name)},\s*jdbc\.queryForObject\(\s*"SELECT current_user",\s*String\.class\),\s*"Refusing to run with an unexpected database user"\);'''
    return re.sub(pattern, "\n", text, flags=re.DOTALL)


# TradeReconstructionIntegrationTest
p = files["trade_reconstruction"]
old = p.read_text(encoding="utf-8")
text = remove_dynamic_property_imports(old)
text = re.sub(
    r'''private\s+static\s+final\s+String\s+TEST_DB\s*=\s*"leap_sprint4_rollback_test";\s*private\s+static\s+final\s+String\s+TEST_USER\s*=\s*"leap_sprint4_test";\s*@DynamicPropertySource\s*static\s+void\s+configureDatabase\(\s*DynamicPropertyRegistry\s+registry\)\s*\{.*?\n\s*\}''',
    'private static final String TEST_DB = "leap_trading_test";',
    text,
    count=1,
    flags=re.DOTALL,
)
text = remove_test_user_assertion(text)
write_changed(p, old, text)


# OrderExecutionAccountIsolationTest
p = files["account_isolation"]
old = p.read_text(encoding="utf-8")
text = remove_dynamic_property_imports(old)
text = re.sub(
    r'''private\s+static\s+final\s+String\s+TEST_DB\s*=\s*"leap_sprint4_rollback_test";\s*private\s+static\s+final\s+String\s+TEST_USER\s*=\s*"leap_sprint4_test";\s*private\s+static\s+final\s+String\s+TEST_DB_URL\s*=\s*"jdbc:postgresql://localhost:5432/"\s*\+\s*TEST_DB;\s*@DynamicPropertySource\s*static\s+void\s+configureDatabase\(\s*DynamicPropertyRegistry\s+registry\)\s*\{.*?\n\s*\}''',
    'private static final String TEST_DB = "leap_trading_test";',
    text,
    count=1,
    flags=re.DOTALL,
)
text = remove_test_user_assertion(text)
write_changed(p, old, text)


# OrderExecutionRollbackIntegrationTest
p = files["rollback"]
old = p.read_text(encoding="utf-8")
text = remove_dynamic_property_imports(old)
text = re.sub(
    r'''private\s+static\s+final\s+String\s+TEST_DB_NAME\s*=\s*"leap_sprint4_rollback_test";\s*private\s+static\s+final\s+String\s+TEST_DB_USER\s*=\s*"leap_sprint4_test";\s*private\s+static\s+final\s+String\s+TEST_DB_URL\s*=\s*"jdbc:postgresql://localhost:5432/"\s*\+\s*TEST_DB_NAME;.*?@DynamicPropertySource\s*static\s+void\s+configureDatabase\(\s*DynamicPropertyRegistry\s+registry\)\s*\{.*?\n\s*\}''',
    'private static final String TEST_DB_NAME = "leap_trading_test";',
    text,
    count=1,
    flags=re.DOTALL,
)
text = re.sub(
    r'''\s*String\s+actualUser\s*=\s*jdbc\.queryForObject\(\s*"SELECT current_user",\s*String\.class\);''',
    '',
    text,
    count=1,
    flags=re.DOTALL,
)
text = re.sub(
    r'''\s*assertEquals\(\s*TEST_DB_USER,\s*actualUser,\s*"Refusing to run with an unexpected database user"\);''',
    '',
    text,
    count=1,
    flags=re.DOTALL,
)
text = text.replace("dedicated local Sprint 4 test database.", "shared integration-test database.")
write_changed(p, old, text)


# OrderExecutionLifecycleIntegrationTest
p = files["lifecycle"]
old = p.read_text(encoding="utf-8")
text = remove_dynamic_property_imports(old)
text = re.sub(
    r'''private\s+static\s+final\s+String\s+TEST_DB\s*=\s*"leap_sprint4_rollback_test";\s*private\s+static\s+final\s+String\s+TEST_USER\s*=\s*"leap_sprint4_test";\s*@DynamicPropertySource\s*static\s+void\s+configureDatabase\(DynamicPropertyRegistry\s+registry\)\s*\{.*?\n\s*\}''',
    'private static final String TEST_DB = "leap_trading_test";',
    text,
    count=1,
    flags=re.DOTALL,
)
text = remove_test_user_assertion(text)
write_changed(p, old, text)


# OrderExecutionConcurrentDuplicateProcessingTest
p = files["concurrent"]
old = p.read_text(encoding="utf-8")
text = remove_dynamic_property_imports(old)
text = re.sub(
    r'''private\s+static\s+final\s+String\s+TEST_DB\s*=\s*"leap_sprint4_rollback_test";\s*private\s+static\s+final\s+String\s+TEST_USER\s*=\s*"leap_sprint4_test";\s*private\s+static\s+final\s+String\s+TEST_DB_URL\s*=\s*"jdbc:postgresql://localhost:5432/"\s*\+\s*TEST_DB;\s*@DynamicPropertySource\s*static\s+void\s+configureDatabase\(\s*DynamicPropertyRegistry\s+registry\)\s*\{.*?\n\s*\}''',
    'private static final String TEST_DB = "leap_trading_test";',
    text,
    count=1,
    flags=re.DOTALL,
)
text = remove_test_user_assertion(text)
write_changed(p, old, text)


# ApplicationRestartRecoveryIntegrationTest
p = files["restart"]
old = p.read_text(encoding="utf-8")
text = old
text = re.sub(
    r'''private\s+static\s+final\s+String\s+TEST_DB\s*=\s*"leap_sprint4_rollback_test";\s*private\s+static\s+final\s+String\s+TEST_USER\s*=\s*"leap_sprint4_test";\s*private\s+static\s+final\s+String\s+TEST_URL\s*=\s*"jdbc:postgresql://localhost:5432/"\s*\+\s*TEST_DB;''',
    'private static final String TEST_DB = "leap_trading_test";',
    text,
    count=1,
    flags=re.DOTALL,
)
text = re.sub(r'''\s*String\s+password\s*=\s*requireTestPassword\(\);''', '', text, count=1)
text = text.replace("startApplication(\n                password,\n                false)", "startApplication(\n                false)")
text = text.replace("startApplication(\n                password,\n                true)", "startApplication(\n                true)")
text = text.replace(
    "private ConfigurableApplicationContext startApplication(\n            String password,\n            boolean recoveryEnabled)",
    "private ConfigurableApplicationContext startApplication(\n            boolean recoveryEnabled)",
)
text = re.sub(
    r'''\s*/\*\s*\* These are passed as command-line properties.*?\*/\s*\.run\(\s*"--server\.port=0",\s*"--spring\.datasource\.url="\s*\+\s*TEST_URL,\s*"--spring\.datasource\.username="\s*\+\s*TEST_USER,\s*"--spring\.datasource\.password="\s*\+\s*password,\s*"--spring\.jpa\.hibernate\.ddl-auto=validate",\s*"--spring\.flyway\.enabled=true",\s*"--app\.market-simulator\.enabled=false",\s*"--app\.recovery\.enabled="\s*\+\s*recoveryEnabled\);''',
    '''\n                .run(\n                        "--server.port=0",\n                        "--spring.jpa.hibernate.ddl-auto=validate",\n                        "--spring.flyway.enabled=true",\n                        "--app.market-simulator.enabled=false",\n                        "--app.reporting.backfill-enabled=false",\n                        "--app.recovery.enabled="\n                                + recoveryEnabled);''',
    text,
    count=1,
    flags=re.DOTALL,
)
text = re.sub(
    r'''\n\s*private\s+String\s+requireTestPassword\(\)\s*\{.*?\n\s*\}\s*(?=\n\s*private\s+void\s+assertDedicatedDatabase)''',
    '\n',
    text,
    count=1,
    flags=re.DOTALL,
)
text = re.sub(
    r'''\s*assertEquals\(\s*TEST_USER,\s*jdbc\.queryForObject\(\s*"SELECT current_user",\s*String\.class\)\);''',
    '',
    text,
    count=1,
    flags=re.DOTALL,
)
write_changed(p, old, text)


# application-test.yaml
p = files["test_yaml"]
old = p.read_text(encoding="utf-8")
text = old
text = text.replace("url: ${DB_URL}", "url: ${TEST_DB_URL}")
text = text.replace("username: ${DB_USERNAME}", "username: ${TEST_DB_USERNAME}")
text = text.replace("password: ${DB_PASSWORD}", "password: ${TEST_DB_PASSWORD}")
if "reporting:\n    backfill-enabled:" not in text:
    if not text.endswith("\n"):
        text += "\n"
    text += "\n  reporting:\n    backfill-enabled: false\n"
write_changed(p, old, text)


# Validation
bad = []
for key, path in files.items():
    content = path.read_text(encoding="utf-8")
    if key != "test_yaml" and "leap_sprint4_rollback_test" in content:
        bad.append(f"{path}: still contains leap_sprint4_rollback_test")
    if key != "test_yaml" and "leap_sprint4_test" in content:
        bad.append(f"{path}: still contains leap_sprint4_test")
    if key != "test_yaml" and "LEAP_TEST_DB_PASSWORD" in content:
        bad.append(f"{path}: still contains LEAP_TEST_DB_PASSWORD")

yaml = files["test_yaml"].read_text(encoding="utf-8")
for required in ("${TEST_DB_URL}", "${TEST_DB_USERNAME}", "${TEST_DB_PASSWORD}"):
    if required not in yaml:
        bad.append(f"application-test.yaml missing {required}")

if bad:
    print("\nVALIDATION FAILED")
    for item in bad:
        print(" -", item)
    sys.exit(2)

print("\nSUCCESS")
print("All six integration tests now use application-test.yaml and expect leap_trading_test.")
print("Backups were created next to changed files with the .bak suffix.")
