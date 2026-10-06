pipeline {

    agent any

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
    }

    environment {

        /*
         * PostgreSQL CI container.
         *
         * We intentionally use the same database user expected
         * by the Sprint 4 PostgreSQL integration tests.
         */
        CI_DB_CONTAINER = 'leap-postgres-ci'

        DB_USERNAME = 'leap_sprint4_test'

        /*
         * Main database used by the normal backend test profile.
         */
        CI_APP_DB = 'leap_trading_test'

        /*
         * Dedicated database expected by the Sprint 4
         * rollback/concurrency/lifecycle integration tests.
         */
        CI_SPRINT4_DB = 'leap_sprint4_rollback_test'

        /*
         * Stored in:
         *
         * Jenkins
         *   -> Manage Jenkins
         *   -> Credentials
         *
         * Credential type:
         *   Secret text
         *
         * Credential ID:
         *   leap-ci-db-password
         */
        DB_PASSWORD = credentials('leap-ci-db-password')
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
                        docker run --rm \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/tmp \
                            -v "$PWD:/app" \
                            -w /app \
                            node:24-bookworm \
                            sh -lc 'npm ci && npm test -- --watch=false && npm run build'
                    '''
                }
            }
        }

        stage('Start CI Database') {

            steps {

                sh '''
                    echo "Removing any previous CI database container..."

                    docker rm -f ${CI_DB_CONTAINER} || true


                    echo "Starting PostgreSQL CI database..."

                    docker run -d \
                        --name ${CI_DB_CONTAINER} \
                        -e POSTGRES_DB=${CI_SPRINT4_DB} \
                        -e POSTGRES_USER=${DB_USERNAME} \
                        -e POSTGRES_PASSWORD="${DB_PASSWORD}" \
                        postgres:17


                    echo "Waiting for PostgreSQL..."

                    until docker exec \
                        ${CI_DB_CONTAINER} \
                        pg_isready \
                        -U ${DB_USERNAME} \
                        -d ${CI_SPRINT4_DB}
                    do
                        sleep 1
                    done


                    echo "PostgreSQL is ready."


                    echo "Creating normal backend test database..."

                    docker exec \
                        -e PGPASSWORD="${DB_PASSWORD}" \
                        ${CI_DB_CONTAINER} \
                        createdb \
                        -U ${DB_USERNAME} \
                        -O ${DB_USERNAME} \
                        ${CI_APP_DB}


                    echo "CI databases are ready."

                    echo "Created:"
                    echo "  ${CI_APP_DB}"
                    echo "  ${CI_SPRINT4_DB}"
                '''
            }
        }

        stage('Backend Build & Test') {

            steps {

                dir('backend/trading-platform') {

                    /*
                     * Important:
                     *
                     * The Maven container shares the PostgreSQL
                     * container's network namespace.
                     *
                     * Therefore:
                     *
                     * jdbc:postgresql://localhost:5432/...
                     *
                     * points directly at the CI PostgreSQL
                     * container.
                     *
                     * This also allows the Sprint 4 integration
                     * tests, which intentionally use
                     * localhost:5432, to run unchanged.
                     */
                    sh '''
                        docker run --rm \
                            --network container:${CI_DB_CONTAINER} \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/tmp \
                            -e DB_URL="jdbc:postgresql://localhost:5432/${CI_APP_DB}" \
                            -e DB_USERNAME="${DB_USERNAME}" \
                            -e DB_PASSWORD="${DB_PASSWORD}" \
                            -e LEAP_TEST_DB_PASSWORD="${DB_PASSWORD}" \
                            -v "$PWD:/app" \
                            -w /app \
                            maven:3.9.9-eclipse-temurin-21 \
                            ./mvnw clean verify
                    '''
                }
            }
        }
    }

    post {

        always {

            sh '''
                echo "Cleaning up CI PostgreSQL container..."

                docker rm -f ${CI_DB_CONTAINER} || true
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
            Sprint 4 integration: PASSED

            ==========================================
            '''
        }

        failure {

            echo '''
            ==========================================
                     LEAP CI FAILED
            ==========================================

            Check the failed pipeline stage and
            console output for details.

            ==========================================
            '''
        }
    }
}