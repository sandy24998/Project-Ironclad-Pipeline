pipeline {

    agent any

    environment {
        IMAGE_NAME = "sandy541998/ironclad-app"
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
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

                    echo "Build Number : ${BUILD_NUMBER}"
                    echo "Git Commit   : ${GIT_COMMIT_SHORT}"
                    echo "Image Tag    : ${IMAGE_TAG}"
                }
            }
        }

        stage('Run Unit Tests') {
            steps {

                sh '''
                python3 -m venv venv

                . venv/bin/activate

                pip install --upgrade pip

                pip install -r app/requirements.txt

                cd app

                pytest -v
                '''
            }
        }

        stage('Build Docker Image') {
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

        stage('Docker Hub Login') {
            steps {

                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {

                    sh '''
                    echo "$DOCKER_PASS" | docker login \
                    -u "$DOCKER_USER" \
                    --password-stdin
                    '''
                }
            }
        }

        stage('Push Artifact') {
            steps {

                sh '''
                docker push ${IMAGE_NAME}:${IMAGE_TAG}
                '''
            }
        }
    }

    post {

        success {

            echo '====================================='
            echo 'BUILD SUCCESSFUL'
            echo "Artifact Published:"
            echo "${IMAGE_NAME}:${IMAGE_TAG}"
            echo '====================================='
        }

        failure {

            echo '====================================='
            echo 'BUILD FAILED'
            echo '====================================='
        }

        always {

            sh '''
            docker logout || true
            '''
        }
    }
}