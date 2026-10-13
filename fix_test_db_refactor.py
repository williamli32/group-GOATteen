from pathlib import Path
import shutil
import sys

ROOT = Path.cwd()

JAVA_FILES = {
    "trade_reconstruction": ROOT / "backend/trading-platform/src/test/java/com/goatteen/trading/audit/TradeReconstructionIntegrationTest.java",
    "account_isolation": ROOT / "backend/trading-platform/src/test/java/com/goatteen/trading/execution/OrderExecutionAccountIsolationTest.java",
    "rollback": ROOT / "backend/trading-platform/src/test/java/com/goatteen/trading/execution/OrderExecutionRollbackIntegrationTest.java",
    "lifecycle": ROOT / "backend/trading-platform/src/test/java/com/goatteen/trading/execution/OrderExecutionLifecycleIntegrationTest.java",
    "concurrent": ROOT / "backend/trading-platform/src/test/java/com/goatteen/trading/execution/OrderExecutionConcurrentDuplicateProcessingTest.java",
    "restart": ROOT / "backend/trading-platform/src/test/java/com/goatteen/trading/recovery/ApplicationRestartRecoveryIntegrationTest.java",
}

YAML_FILE = ROOT / "backend/trading-platform/src/test/resources/application-test.yaml"


def die(message):
    print(f"ERROR: {message}")
    sys.exit(1)


def read_lines(path):
    return path.read_text(encoding="utf-8").replace("\r\n", "\n").splitlines()


def write_lines(path, lines):
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def remove_import(lines, exact):
    return [line for line in lines if line.strip() != exact]


def remove_assert_block_using(lines, token):
    """Remove assertEquals(...) blocks that reference token."""
    out = []
    i = 0
    while i < len(lines):
        if lines[i].strip() == "assertEquals(":
            j = i
            block = []
            while j < len(lines):
                block.append(lines[j])
                if lines[j].strip().endswith(");"):
                    break
                j += 1
            if any(token in line for line in block):
                i = j + 1
                # Remove one following blank line if present.
                if i < len(lines) and not lines[i].strip():
                    i += 1
                continue
        out.append(lines[i])
        i += 1
    return out


def remove_assignment(lines, starts_with_text):
    out = []
    i = 0
    while i < len(lines):
        if starts_with_text in lines[i]:
            j = i
            while j < len(lines) and not lines[j].strip().endswith(");"):
                j += 1
            i = min(j + 1, len(lines))
            if i < len(lines) and not lines[i].strip():
                i += 1
            continue
        out.append(lines[i])
        i += 1
    return out


def remove_method(lines, method_marker):
    start = next((i for i, line in enumerate(lines) if method_marker in line), None)
    if start is None:
        die(f"Could not find method marker: {method_marker}")

    depth = 0
    saw_open = False
    end = None
    for i in range(start, len(lines)):
        depth += lines[i].count("{")
        if "{" in lines[i]:
            saw_open = True
        depth -= lines[i].count("}")
        if saw_open and depth == 0:
            end = i
            break
    if end is None:
        die(f"Could not determine end of method: {method_marker}")

    result = lines[:start] + lines[end + 1:]
    # Collapse an extra blank line at the join.
    while start < len(result) - 1 and not result[start - 1].strip() and not result[start].strip():
        del result[start]
    return result


def replace_config_section(lines, old_db_name, constant_line):
    old_idx = next((i for i, line in enumerate(lines) if old_db_name in line), None)
    if old_idx is None:
        die(f"Could not find old database reference {old_db_name}")

    autowired_idx = next(
        (i for i in range(old_idx + 1, len(lines)) if lines[i].strip() == "@Autowired"),
        None,
    )
    if autowired_idx is None:
        die("Could not find @Autowired after database configuration")

    indent = lines[old_idx][: len(lines[old_idx]) - len(lines[old_idx].lstrip())]
    replacement = [indent + constant_line, ""]
    return lines[:old_idx] + replacement + lines[autowired_idx:]


def restore_backups():
    print("Restoring the six Java files from their .bak backups...")
    for path in JAVA_FILES.values():
        backup = Path(str(path) + ".bak")
        if not backup.exists():
            die(f"Missing backup: {backup}")
        shutil.copyfile(backup, path)
        print(f"  restored {path}")


def refactor_standard_file(key, constant_line, user_token):
    path = JAVA_FILES[key]
    lines = read_lines(path)

    lines = remove_import(lines, "import org.springframework.test.context.DynamicPropertyRegistry;")
    lines = remove_import(lines, "import org.springframework.test.context.DynamicPropertySource;")

    lines = replace_config_section(
        lines,
        "leap_sprint4_rollback_test",
        constant_line,
    )

    lines = remove_assert_block_using(lines, user_token)

    if key == "rollback":
        lines = remove_assignment(lines, "String actualUser =")
        lines = [
            line.replace(
                "dedicated local Sprint 4 test database.",
                "shared integration-test database.",
            )
            for line in lines
        ]

    write_lines(path, lines)
    print(f"Refactored {path}")


def remove_run_arg_pair(lines, marker):
    out = []
    i = 0
    removed = False
    while i < len(lines):
        if marker in lines[i]:
            removed = True
            # The next source line is the concatenated value, e.g. + TEST_URL,
            i += 2
            # Consume one blank line following the pair.
            if i < len(lines) and not lines[i].strip():
                i += 1
            continue
        out.append(lines[i])
        i += 1
    if not removed:
        die(f"Could not find restart application argument {marker}")
    return out


def remove_comment_containing(lines, marker):
    idx = next((i for i, line in enumerate(lines) if marker in line), None)
    if idx is None:
        return lines
    start = idx
    while start >= 0 and "/*" not in lines[start]:
        start -= 1
    end = idx
    while end < len(lines) and "*/" not in lines[end]:
        end += 1
    if start < 0 or end >= len(lines):
        return lines
    return lines[:start] + lines[end + 1:]


def refactor_restart_file():
    path = JAVA_FILES["restart"]
    lines = read_lines(path)

    # Replace old constants block with only the test DB safety constant.
    old_idx = next((i for i, line in enumerate(lines) if "leap_sprint4_rollback_test" in line), None)
    if old_idx is None:
        die("Could not find old DB in ApplicationRestartRecoveryIntegrationTest")
    test_idx = next((i for i in range(old_idx + 1, len(lines)) if lines[i].strip() == "@Test"), None)
    if test_idx is None:
        die("Could not find @Test after restart-test constants")
    indent = lines[old_idx][: len(lines[old_idx]) - len(lines[old_idx].lstrip())]
    lines = lines[:old_idx] + [indent + 'private static final String TEST_DB = "leap_trading_test";', ""] + lines[test_idx:]

    # Remove password acquisition.
    lines = [line for line in lines if "String password = requireTestPassword();" not in line]

    # Remove the password argument from all startApplication(...) calls.
    lines = [line for line in lines if line.strip() != "password,"]

    # Remove String password parameter from startApplication method.
    lines = [line for line in lines if line.strip() != "String password,"]

    # Remove obsolete datasource command-line overrides so application-test.yaml wins.
    lines = remove_comment_containing(lines, "These are passed as command-line properties")
    lines = remove_run_arg_pair(lines, '"--spring.datasource.url="')
    lines = remove_run_arg_pair(lines, '"--spring.datasource.username="')
    lines = remove_run_arg_pair(lines, '"--spring.datasource.password="')

    # Disable reporting backfill explicitly for this multi-restart test.
    market_idx = next((i for i, line in enumerate(lines) if '"--app.market-simulator.enabled=false"' in line), None)
    if market_idx is None:
        die("Could not find market-simulator test argument")
    indent2 = lines[market_idx][: len(lines[market_idx]) - len(lines[market_idx].lstrip())]
    if not any("--app.reporting.backfill-enabled=false" in line for line in lines):
        lines.insert(market_idx + 1, "")
        lines.insert(market_idx + 2, indent2 + '"--app.reporting.backfill-enabled=false",')

    lines = remove_method(lines, "private String requireTestPassword()")
    lines = remove_assert_block_using(lines, "TEST_USER")

    write_lines(path, lines)
    print(f"Refactored {path}")


def write_application_test_yaml():
    content = """spring:
  config:
    import: optional:file:.env[.properties]

  datasource:
    url: ${TEST_DB_URL}
    username: ${TEST_DB_USERNAME}
    password: ${TEST_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate

    show-sql: true

  flyway:
    enabled: true

app:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiration-minutes: 15

  auth:
    refresh-token-expiration-days: 7

  market-simulator:
    enabled: false

  recovery:
    enabled: false

  reporting:
    backfill-enabled: false
"""
    YAML_FILE.write_text(content, encoding="utf-8")
    print(f"Updated {YAML_FILE}")


def validate():
    bad = []
    forbidden = (
        "leap_sprint4_rollback_test",
        "leap_sprint4_test",
        "LEAP_TEST_DB_PASSWORD",
        "@DynamicPropertySource",
        "DynamicPropertyRegistry",
        "DynamicPropertySource",
    )
    for path in JAVA_FILES.values():
        text = path.read_text(encoding="utf-8")
        for token in forbidden:
            if token in text:
                bad.append(f"{path}: still contains {token}")
        if "leap_trading_test" not in text:
            bad.append(f"{path}: missing leap_trading_test safety constant")

    yaml = YAML_FILE.read_text(encoding="utf-8")
    for token in ("${TEST_DB_URL}", "${TEST_DB_USERNAME}", "${TEST_DB_PASSWORD}"):
        if token not in yaml:
            bad.append(f"{YAML_FILE}: missing {token}")

    if bad:
        print("\nVALIDATION FAILED")
        for item in bad:
            print(" - " + item)
        sys.exit(2)

    print("\nValidation passed.")
    print("No active Java test file contains the old Sprint 4 DB/user/password configuration.")


def main():
    for path in JAVA_FILES.values():
        if not path.parent.exists():
            die(f"Run this script from the group-GOATteen repository root. Missing: {path.parent}")

    restore_backups()

    refactor_standard_file(
        "trade_reconstruction",
        'private static final String TEST_DB = "leap_trading_test";',
        "TEST_USER",
    )
    refactor_standard_file(
        "account_isolation",
        'private static final String TEST_DB = "leap_trading_test";',
        "TEST_USER",
    )
    refactor_standard_file(
        "rollback",
        'private static final String TEST_DB_NAME = "leap_trading_test";',
        "TEST_DB_USER",
    )
    refactor_standard_file(
        "lifecycle",
        'private static final String TEST_DB = "leap_trading_test";',
        "TEST_USER",
    )
    refactor_standard_file(
        "concurrent",
        'private static final String TEST_DB = "leap_trading_test";',
        "TEST_USER",
    )
    refactor_restart_file()
    write_application_test_yaml()
    validate()

    print("\nNext commands:")
    print(r"  cd backend\trading-platform")
    print(r"  .\mvnw.cmd -DskipTests test-compile")
    print(r"  .\mvnw.cmd clean test")


if __name__ == "__main__":
    main()
