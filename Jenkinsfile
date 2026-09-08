pipeline {

    agent any

    environment {
        DB_URL = 'jdbc:postgresql://localhost:5433/leap_trading_test'
        DB_USERNAME = 'postgres'
        DB_PASSWORD = 'n3u3da!'
        CI_DB_CONTAINER = 'leap-postgres-ci'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Start CI Database') {
            steps {
                sh '''
                    docker rm -f ${CI_DB_CONTAINER} || true

                    docker run -d \
                        --name ${CI_DB_CONTAINER} \
                        -e POSTGRES_DB=leap_trading_test \
                        -e POSTGRES_USER=${DB_USERNAME} \
                        -e POSTGRES_PASSWORD=${DB_PASSWORD} \
                        -p 5433:5432 \
                        postgres:17

                    echo "Waiting for PostgreSQL..."

                    until docker exec ${CI_DB_CONTAINER} \
                        pg_isready \
                        -U ${DB_USERNAME} \
                        -d leap_trading_test;
                    do
                        sleep 1
                    done

                    echo "PostgreSQL is ready."
                '''
            }
        }

        stage('Build & Test Backend') {
            steps {
                dir('backend/trading-platform') {
                    sh '''
                        ./mvnw clean verify
                    '''
                }
            }
        }
    }

    post {

        always {
            sh '''
                docker rm -f ${CI_DB_CONTAINER} || true
            '''
        }

        success {
            echo 'LEAP backend CI pipeline passed.'
        }

        failure {
            echo 'LEAP backend CI pipeline failed.'
        }
    }
}