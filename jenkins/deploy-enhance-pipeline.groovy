pipeline {

    agent any

     environment {
        IMAGE_NAME = "sandy541998/ironclad-app"
        DEPLOYMENT_NAME = "ironclad-app"
        DOCKER_CREDS = credentials('dockerhub')
        GIT_CREDS = credentials('sandeep-token')
        DEPLOYMENT_TIMEOUT = "600"
    }

     options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
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
                    env.IMAGE_TAG ="${BUILD_NUMBER}-${GIT_COMMIT_SHORT}"
                    echo "Build Number : ${BUILD_NUMBER}"
                    echo "Git Commit   : ${GIT_COMMIT_SHORT}"
                    echo "Image Tag    : ${IMAGE_TAG}"
                }
            }
        }

        stage('Run Unit Tests') {
            steps {
                retry(2){
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
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ./app
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

        stage('Security Scan Image') {
            steps {
                sh '''
                docker run --rm \
                  -v /var/run/docker.sock:/var/run/docker.sock \
                  aquasec/trivy image --severity HIGH,CRITICAL ${IMAGE_NAME}:${IMAGE_TAG}
                '''
            }
        }

        stage('Docker Hub Login') {
            steps {
                 sh 'echo "$DOCKER_CREDS_PSW" | docker login -u "$DOCKER_CREDS_USR" --password-stdin'
            }
        }

        stage('Push Artifact') {
            steps {
                retry(2) {
                    sh '''
                    docker push ${IMAGE_NAME}:${IMAGE_TAG}
                    '''
                }
            }
        }

        stage('Verify Published Image') {
            steps {
                sh '''
                docker pull ${IMAGE_NAME}:${IMAGE_TAG}
                '''
            }
        }

        stage('Deploy DEV') {
            steps {
                sh '''
                chmod +x scripts/deploy-dev.sh
                ./scripts/deploy-dev.sh ${IMAGE_TAG}
                '''
            }
        }

        stage('Verify DEV Deployment') {
            steps {
                sh '''
                kubectl rollout status deployment/${DEPLOYMENT_NAME} -n dev --timeout=${DEPLOYMENT_TIMEOUT}s
                kubectl wait --for=condition=ready pod -l app=${DEPLOYMENT_NAME} -n dev --timeout=${DEPLOYMENT_TIMEOUT}s
                '''
            }
        }

        stage('QA Approval') {
            steps {
                input message: 'Promote build to QA?', ok: 'Deploy QA'
            }
        }

        stage('Deploy QA') {
            steps {
                sh '''
                chmod +x scripts/deploy-qa.sh
                ./scripts/deploy-qa.sh ${IMAGE_TAG}
                '''
            }
        }

        stage('Verify QA Deployment') {
            steps {
                sh '''
                kubectl rollout status deployment/${DEPLOYMENT_NAME} -n qa --timeout=${DEPLOYMENT_TIMEOUT}s
                kubectl wait --for=condition=ready pod -l app=${DEPLOYMENT_NAME} -n qa --timeout=${DEPLOYMENT_TIMEOUT}s
                '''
            }
        }

        stage('UAT Approval') {
            steps {
                input message: 'Promote build to UAT?', ok: 'Deploy UAT'
            }
        }

        stage('Deploy UAT') {
            steps {
                sh '''
                chmod +x scripts/deploy-uat.sh
                ./scripts/deploy-uat.sh ${IMAGE_TAG}
                '''
            }
        }

        stage('Verify UAT Deployment') {
            steps {
                sh '''
                kubectl rollout status deployment/${DEPLOYMENT_NAME} -n uat --timeout=${DEPLOYMENT_TIMEOUT}s
                kubectl wait --for=condition=ready pod -l app=${DEPLOYMENT_NAME} -n uat --timeout=${DEPLOYMENT_TIMEOUT}s
                '''
            }
        }

        stage('Release Approval') {
            steps {
                script {
                    env.RELEASE_VERSION = input(
                        message: 'Approve Production Release',
                        ok: 'Release',
                        parameters: [
                            string(
                                name: 'VERSION',
                                defaultValue: 'v1.0.1',
                                description: 'Production Release Version'
                            )
                        ]
                    )
                }
            }
        }

        stage('Retag Release Image') {
            steps {
                sh '''
                docker pull ${IMAGE_NAME}:${IMAGE_TAG}
                docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:${RELEASE_VERSION}
                '''
            }
        }

        stage('Push Release Tag') {
            steps {
                retry(2) {
                    sh '''
                    docker push ${IMAGE_NAME}:${RELEASE_VERSION}
                    '''
                }
            }
        }

        stage('Verify Release Artifact') {
            steps {
                sh '''
                docker pull ${IMAGE_NAME}:${RELEASE_VERSION}
                '''
            }
        }

        stage('Create Git Release Tag') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'sandeep-token',
                    usernameVariable: 'GIT_USER',
                    passwordVariable: 'GIT_TOKEN'
                )]) {
                    sh '''
                    git config user.name "Sandeep Pandit"
                    git config user.email "54panditsandeep@gmail.com"
                    git tag ${RELEASE_VERSION}
                    git push https://${GIT_USER}:${GIT_TOKEN}@github.com/sandy24998/Project-Ironclad-Pipeline.git ${RELEASE_VERSION}
                    '''
                }
            }
        }        

        stage('Deploy PROD') {
            steps {
                script {
                    try {
                        sh '''
                        chmod +x scripts/deploy-prod.sh
                        ./scripts/deploy-prod.sh ${RELEASE_VERSION}
                        '''
                    } catch (Exception e) {
                        echo "Deployment failed, rolling back..."
                        sh '''
                        kubectl rollout undo deployment/${DEPLOYMENT_NAME} -n prod
                        kubectl rollout status deployment/${DEPLOYMENT_NAME} -n prod --timeout=${DEPLOYMENT_TIMEOUT}s
                        '''
                        throw e
                    }
                }
            }
        }

        stage('Verify PROD Deployment') {
            steps {
                sh '''
                kubectl rollout status deployment/${DEPLOYMENT_NAME} -n prod --timeout=${DEPLOYMENT_TIMEOUT}s
                kubectl wait --for=condition=ready pod -l app=${DEPLOYMENT_NAME} -n prod --timeout=${DEPLOYMENT_TIMEOUT}s
                '''
            }
        }

    }

    post {

        success {

            echo '====================================='
            echo 'BUILD SUCCESSFUL'
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