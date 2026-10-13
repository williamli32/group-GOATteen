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
        TEST_DB_PASSWORD = credentials('leap-ci-db-password')
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
                        -e POSTGRES_DB=${CI_TEST_DB} \
                        -e POSTGRES_USER=${TEST_DB_USERNAME} \
                        -e POSTGRES_PASSWORD="${TEST_DB_PASSWORD}" \
                        postgres:17


                    echo "Waiting for PostgreSQL..."

                    until docker exec \
                        ${CI_DB_CONTAINER} \
                        pg_isready \
                        -U ${TEST_DB_USERNAME} \
                        -d ${CI_TEST_DB}
                    do
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
                        docker run --rm \
                            --network container:${CI_DB_CONTAINER} \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/tmp \
                            -e TEST_DB_URL="jdbc:postgresql://localhost:5432/${CI_TEST_DB}" \
                            -e TEST_DB_USERNAME="${TEST_DB_USERNAME}" \
                            -e TEST_DB_PASSWORD="${TEST_DB_PASSWORD}" \
                            -e JWT_SECRET="${JWT_SECRET}" \
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

            Check the failed pipeline stage and
            console output for details.

            ==========================================
            '''
        }
    }
}