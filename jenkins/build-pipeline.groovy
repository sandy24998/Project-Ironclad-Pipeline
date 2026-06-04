pipeline {

    agent any

    environment {
        IMAGE_NAME = "sandy541998/ironclad-app"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Metadata') {
            steps {
                script {

                    env.GIT_COMMIT_SHORT = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()

                    env.IMAGE_TAG =
                        "${BUILD_NUMBER}-${GIT_COMMIT_SHORT}"

                    echo "Image Tag = ${IMAGE_TAG}"
                }
            }
        }

        stage('Run Tests') {
            steps {
                sh '''
                python3 -m venv venv

                . venv/bin/activate

                pip install -r app/requirements.txt

                cd app

                pytest -v
                '''
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                docker build \
                -t ${IMAGE_NAME}:${IMAGE_TAG} \
                ./app
                '''
            }
        }

        stage('Verify Image') {
            steps {
                sh '''
                docker images | grep ironclad
                '''
            }
        }
    }

    post {

        success {
            echo "Image Created Successfully"
            echo "${IMAGE_NAME}:${IMAGE_TAG}"
        }

        failure {
            echo "Build Failed"
        }
    }
}