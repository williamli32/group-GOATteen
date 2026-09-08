pipeline {

    agent any

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test Backend') {
            steps {
                dir('backend/trading-platform') {
                    sh './mvnw clean verify'
                }
            }
        }
    }

    post {

        success {
            echo 'LEAP backend CI pipeline passed.'
        }

        failure {
            echo 'LEAP backend CI pipeline failed.'
        }
    }
}