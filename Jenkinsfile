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
        CI_TEST_DB = 'leap_trading_test'
        TEST_DB_USERNAME = 'leap_test'

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
         * Keep the Maven repository and wrapper distributions beside the
         * workspace so Maven clean and checkout cleanup do not remove them.
         * Builds on another Jenkins agent will populate that agent's cache.
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
                            sh -lc 'npm ci && npm test -- --watch=false && npm run build'
                    '''
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
                        postgres:17

                    echo "Waiting for PostgreSQL (up to 60 readiness attempts)..."

                    attempt=1
                    until docker exec "$CI_DB_CONTAINER" \
                        pg_isready \
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
                        if [ -z "${JWT_SECRET}" ]; then
                            echo "ERROR: JWT_SECRET is empty"
                            exit 1
                        fi

                        docker run --rm \
                            --network "container:$CI_DB_CONTAINER" \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/tmp \
                            -e TEST_DB_URL="jdbc:postgresql://localhost:5432/${CI_TEST_DB}" \
                            -e TEST_DB_USERNAME="${TEST_DB_USERNAME}" \
                            -e TEST_DB_PASSWORD="${TEST_DB_PASSWORD}" \
                            -e JWT_SECRET="${JWT_SECRET}" \
                            -v "$PWD:/app" \
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

                    /* Missing reports must not turn a no-tests run green. */
                    junit(
                        testResults: 'backend/trading-platform/target/surefire-reports/TEST-*.xml',
                        allowEmptyResults: false
                    )
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
