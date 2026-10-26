pipeline {

    agent any

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
    }

    environment {

        /*
         * Ephemeral PostgreSQL container used only by CI.
         */
        CI_DB_CONTAINER = 'leap-postgres-ci'

        /*
         * All backend integration tests now share one
         * dedicated test database.
         */
        CI_TEST_DB = 'leap_trading_test'

        /*
         * Dedicated CI/test database user.
         *
         * This user exists only inside the temporary
         * PostgreSQL container created during the build.
         */
        TEST_DB_USERNAME = 'leap_test'

        /*
         * Password for the disposable CI database, supplied by Jenkins.
         */
        TEST_DB_PASSWORD = credentials('leap-ci-db-password')

        /* JWT token used for authentication in the backend tests */
        JWT_SECRET = credentials('leap-ci-jwt-secret')
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Deployment Tooling Tests') {
            steps {
                sh '''
                    docker run --rm \
                        -u "$(id -u):$(id -g)" \
                        -e PYTHONDONTWRITEBYTECODE=1 \
                        -v "$PWD:/app" -w /app \
                        python:3.12-slim \
                        python -m unittest discover -s deploy/tests -p 'test_*.py'
                '''
            }
        }

        stage('Frontend Test & Build') {
            steps {
                dir('frontend') {
                    sh '''
                        set -eu

                        docker run --rm \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/tmp \
                            -e CI=true \
                            -e NG_CLI_ANALYTICS=false \
                            -v "$PWD:/app" \
                            -w /app \
                            node:24-bookworm \
                            sh -lc 'npm ci && npm test -- --watch=false && npm run build && npm audit --audit-level=high'
                    '''
                }
            }
        }

        stage('Frontend Browser Validation') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                dir('frontend') {
                    dir('test-results') { deleteDir() }
                    dir('playwright-report') { deleteDir() }
                    sh '''
                        set -eu
                        mkdir -p test-results playwright-report
                        browser_image_id_file="$(mktemp)"
                        rm -f "$browser_image_id_file"
                        cleanup_browser_image() {
                            if [ -s "$browser_image_id_file" ]; then
                                docker image rm "$(cat "$browser_image_id_file")" >/dev/null 2>&1 || true
                            fi
                            rm -f "$browser_image_id_file"
                        }
                        trap cleanup_browser_image EXIT

                        docker build --iidfile "$browser_image_id_file" -f Dockerfile.e2e .
                        # Playwright removes and recreates its output directories.
                        # Mount their writable parent, including this build's npm install.
                        docker run --rm --init --shm-size=1g \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/tmp \
                            -v "$PWD:/app" \
                            -w /app \
                            "$(cat "$browser_image_id_file")" \
                            sh -c 'npm run test:e2e:types && npm run test:e2e'
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts(
                        artifacts: 'frontend/playwright-report/**,frontend/test-results/**',
                        allowEmptyArchive: true
                    )
                    junit(
                        testResults: 'frontend/test-results/e2e-junit.xml',
                        allowEmptyResults: currentBuild.currentResult != 'SUCCESS'
                    )
                }
            }
        }

        stage('Start CI Database') {
            options {
                /* Backstop for a stalled image pull or Docker command. */
                timeout(time: 5, unit: 'MINUTES')
            }

            steps {
                sh '''
                    set +x
                    set -eu

                    echo "Removing any previous CI database container..."
                    docker rm -f "$CI_DB_CONTAINER" >/dev/null 2>&1 || true

                    echo "Starting PostgreSQL CI database..."
                    export POSTGRES_PASSWORD="$TEST_DB_PASSWORD"

                    docker run -d \
                        --name ${CI_DB_CONTAINER} \
                        -e POSTGRES_DB=${CI_TEST_DB} \
                        -e POSTGRES_USER=${TEST_DB_USERNAME} \
                        -e POSTGRES_PASSWORD="${TEST_DB_PASSWORD}" \
                        postgres:17.11-alpine3.24

                    echo "Waiting for PostgreSQL (up to 60 readiness attempts)..."

                    attempt=1
                    until docker exec "$CI_DB_CONTAINER" \
                        pg_isready \
                        -h 127.0.0.1 \
                        -U ${TEST_DB_USERNAME} \
                        -d ${CI_TEST_DB}
                    do
                        if [ "$attempt" -ge 60 ]; then
                            echo "ERROR: PostgreSQL did not become ready."
                            docker logs --tail 100 "$CI_DB_CONTAINER" || true
                            exit 1
                        fi

                        attempt=$((attempt + 1))
                        sleep 1
                    done

                    echo "PostgreSQL is ready."

                    echo "CI test database:"
                    echo "  ${CI_TEST_DB}"
                '''
            }
        }

        stage('Backend Build & Test') {
            steps {
                dir('backend/trading-platform') {
                    /* Do not publish reports left over from a previous build. */
                    dir('target/surefire-reports') {
                        deleteDir()
                    }

                    /*
                     * The Maven container shares the PostgreSQL
                     * container's network namespace.
                     *
                     * Therefore:
                     *
                     * jdbc:postgresql://localhost:5432/...
                     *
                     * points directly at the PostgreSQL
                     * CI container.
                     *
                     * application-test.yaml reads:
                     *
                     * TEST_DB_URL
                     * TEST_DB_USERNAME
                     * TEST_DB_PASSWORD
                     *
                     * Every database-backed test therefore uses
                     * leap_trading_test.
                     */
                    sh '''
                        set +x
                        set -eu

                        if [ -z "${JWT_SECRET}" ]; then
                            echo "ERROR: JWT_SECRET is empty"
                            exit 1
                        fi

                        # Create the cache as Jenkins, before Docker starts.
                        # Its contents survive container removal and Maven clean.
                        maven_cache_dir="${WORKSPACE}@maven-cache"
                        mkdir -p "$maven_cache_dir/.m2/repository" "$maven_cache_dir/.m2/wrapper"

                        docker run --rm \
                            --network "container:$CI_DB_CONTAINER" \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/var/maven \
                            -e MAVEN_CONFIG=/var/maven/.m2 \
                            -e MAVEN_USER_HOME=/var/maven/.m2 \
                            -e TEST_DB_URL="jdbc:postgresql://localhost:5432/${CI_TEST_DB}" \
                            -e TEST_DB_USERNAME="${TEST_DB_USERNAME}" \
                            -e TEST_DB_PASSWORD="${TEST_DB_PASSWORD}" \
                            -e JWT_SECRET="${JWT_SECRET}" \
                            -v "$PWD:/app" \
                            -v "$maven_cache_dir:/var/maven" \
                            -w /app \
                            maven:3.9.9-eclipse-temurin-21 \
                            ./mvnw -B -ntp \
                            -Duser.home=/var/maven \
                            -Dmaven.repo.local=/var/maven/.m2/repository \
                            clean verify
                    '''
                }
            }

            post {
                always {
                    archiveArtifacts(
                        artifacts: 'backend/trading-platform/target/surefire-reports/*.txt',
                        allowEmptyArchive: true
                    )

                    /*
                     * Preserve the original failure if Maven stopped before
                     * producing reports. Successful builds still require XML.
                     */
                    junit(
                        testResults: 'backend/trading-platform/target/surefire-reports/TEST-*.xml',
                        allowEmptyResults: currentBuild.currentResult != 'SUCCESS'
                    )
                }
            }
        }
        stage('Market Data Build & Test') {
            steps {
                dir('backend/market-data-service') {
                    dir('target/surefire-reports') { deleteDir() }
                    sh '''
                        set -eu
                        maven_cache_dir="${WORKSPACE}@maven-cache"
                        mkdir -p "$maven_cache_dir/.m2/repository" "$maven_cache_dir/.m2/wrapper"
                        docker run --rm \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/var/maven \
                            -e MAVEN_CONFIG=/var/maven/.m2 \
                            -e MAVEN_USER_HOME=/var/maven/.m2 \
                            -v "$PWD:/app" -v "$maven_cache_dir:/var/maven" -w /app \
                            maven:3.9.9-eclipse-temurin-21 \
                            bash ./mvnw -B -ntp -Duser.home=/var/maven \
                            -Dmaven.repo.local=/var/maven/.m2/repository clean verify
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts(artifacts: 'backend/market-data-service/target/surefire-reports/*.txt', allowEmptyArchive: true)
                    junit(testResults: 'backend/market-data-service/target/surefire-reports/TEST-*.xml',
                          allowEmptyResults: currentBuild.currentResult != 'SUCCESS')
                }
            }
        }

        stage('Security Image Checks') {
            options { timeout(time: 30, unit: 'MINUTES') }
            steps { sh 'bash deploy/security-scan.sh' }
            post {
                always {
                    archiveArtifacts(artifacts: 'security-reports/*.json', allowEmptyArchive: true)
                }
            }
        }
    }

    post {
        always {
            sh '''
                echo "Cleaning up CI PostgreSQL container..."
                docker rm -f "$CI_DB_CONTAINER" || true
            '''
        }

        success {
            echo '''
            ==========================================
                     LEAP CI PASSED
            ==========================================

            Frontend tests:       PASSED
            Frontend build:       PASSED
            Browser UI checks:    PASSED
            Security scans:       PASSED
            Backend tests:        PASSED
            Backend build:        PASSED
            PostgreSQL tests:     PASSED
            Integration tests:    PASSED

            Test database:
            leap_trading_test

            ==========================================
            '''
        }

        failure {
            echo '''
            ==========================================
                     LEAP CI FAILED
            ==========================================

            Check the failed stage, console output,
            published test results and report artifacts.

            ==========================================
            '''
        }
    }
}
