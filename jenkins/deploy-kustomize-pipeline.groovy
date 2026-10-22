def updateGitOpsTag(String environment, String imageTag) {
    if (!(environment in ['dev', 'qa', 'uat', 'prod'])) {
        error("Unsupported GitOps environment: ${environment}")
    }
    if (!(imageTag ==~ /^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/)) {
        error("Invalid Docker image tag: ${imageTag}")
    }

    dir('gitops-repo') {
        withEnv([
            "GITOPS_ENV=${environment}",
            "GITOPS_IMAGE_TAG=${imageTag}"
        ]) {
            sh '''
                set -eu

                file="k8s/overlays/${GITOPS_ENV}/kustomization.yaml"
                if [ ! -f "$file" ]; then
                    echo "ERROR: GitOps overlay not found: $file"
                    exit 1
                fi

                tag_count="$(grep -Ec '^[[:space:]]*newTag:' "$file" || true)"
                if [ "$tag_count" -ne 1 ]; then
                    echo "ERROR: Expected exactly one newTag in $file; found $tag_count"
                    exit 1
                fi

                sed -i -E \
                    "s|^([[:space:]]*newTag:).*|\\1 \\"${GITOPS_IMAGE_TAG}\\"|" \
                    "$file"
                grep -F "newTag: \"${GITOPS_IMAGE_TAG}\"" "$file"

                git config user.name "Jenkins"
                git config user.email "jenkins@example.com"
                git add "$file"

                if git diff --cached --quiet; then
                    echo "GitOps tag for ${GITOPS_ENV} is already ${GITOPS_IMAGE_TAG}"
                else
                    git commit -m "Deploy ${GITOPS_IMAGE_TAG} to ${GITOPS_ENV}"
                fi
            '''

            withCredentials([
                usernamePassword(
                    credentialsId: 'sandeep-token',
                    usernameVariable: 'GIT_USERNAME',
                    passwordVariable: 'GIT_PASSWORD'
                )
            ]) {
                writeFile(
                    file: 'git-askpass.sh',
                    text: '''#!/bin/sh
case "$1" in
    *Username*) printf '%s\\n' "$GIT_USERNAME" ;;
    *Password*) printf '%s\\n' "$GIT_PASSWORD" ;;
    *) exit 1 ;;
esac
'''
                )
                sh 'chmod 700 git-askpass.sh'
                try {
                    withEnv([
                        "GIT_ASKPASS=${pwd()}/git-askpass.sh",
                        'GIT_TERMINAL_PROMPT=0'
                    ]) {
                        sh 'git push origin HEAD:gitops-dev'
                    }
                } finally {
                    sh 'rm -f git-askpass.sh'
                }
            }
        }
    }
}

pipeline {

    agent any

    environment {
        IMAGE_NAME = "sandy541998/ironclad-app"
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
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

        stage('Verify Published Image') {
            steps {
                sh '''
                docker pull ${IMAGE_NAME}:${IMAGE_TAG}
                '''
            }
        }

        stage('Checkout GitOps Branch') {
            steps {
                dir('gitops-repo') {
                    deleteDir()
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: '*/gitops-dev']],
                        userRemoteConfigs: [[
                            credentialsId: 'sandeep-token',
                            url: 'https://github.com/sandy24998/Project-Ironclad-Pipeline.git'
                        ]]
                    ])
                }
            }
        }

        stage('Update DEV GitOps Tag') {
            steps {
                script {
                    updateGitOpsTag('dev', env.IMAGE_TAG)
                }
            }
        }

        stage('QA Approval') {
            steps {
                input(
                    message: 'Promote build to QA?',
                    ok: 'Promote to QA'
                )
            }
        }

        stage('Update QA GitOps Tag') {
            steps {
                script {
                    updateGitOpsTag('qa', env.IMAGE_TAG)
                }
            }
        }

        stage('UAT Approval') {
            steps {
                input(
                    message: 'Promote build to UAT?',
                    ok: 'Promote to UAT'
                )
            }
        }

        stage('Update UAT GitOps Tag') {
            steps {
                script {
                    updateGitOpsTag('uat', env.IMAGE_TAG)
                }
            }
        }

        stage('Release Approval') {
            steps {
                script {
                    def releaseVersion = input(
                        message: 'Approve Production Release',
                        ok: 'Release',
                        parameters: [
                            string(
                                name: 'VERSION',
                                defaultValue: '',
                                description: 'New release version, e.g. v1.0.2'
                            )
                        ]
                    ).trim()

                    if (!(releaseVersion ==~ /^v[0-9]+\.[0-9]+\.[0-9]+$/)) {
                        error("Invalid release version '${releaseVersion}'. Use vMAJOR.MINOR.PATCH, e.g. v1.0.2.")
                    }

                    def localTagStatus = sh(
                        returnStatus: true,
                        script: "git show-ref --verify --quiet 'refs/tags/${releaseVersion}'"
                    )
                    if (localTagStatus == 0) {
                        error("Release tag '${releaseVersion}' already exists in the Jenkins checkout.")
                    }
                    if (localTagStatus != 1) {
                        error("Could not check local Git tag '${releaseVersion}' (exit ${localTagStatus}).")
                    }

                    def remoteTag = sh(
                        returnStdout: true,
                        script: "git ls-remote --refs origin 'refs/tags/${releaseVersion}'"
                    ).trim()
                    if (remoteTag) {
                        error("Release tag '${releaseVersion}' already exists on origin.")
                    }

                    env.RELEASE_VERSION = releaseVersion
                }
            }
        }

        stage('Retag Release Image') {
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

                    docker pull ${IMAGE_NAME}:${IMAGE_TAG}
                    docker tag \
                    ${IMAGE_NAME}:${IMAGE_TAG} \
                    ${IMAGE_NAME}:${RELEASE_VERSION}
                    '''
                }
            }
        }

        stage('Push Release Tag') {
            steps {
                sh 'docker push ${IMAGE_NAME}:${RELEASE_VERSION}'
            }
        }

        stage('Verify Release Artifact') {
            steps {
                sh 'docker pull ${IMAGE_NAME}:${RELEASE_VERSION}'
            }
        }

        stage('Create Git Release Tag') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'sandeep-token',
                        usernameVariable: 'GIT_USER',
                        passwordVariable: 'GIT_TOKEN'
                    )
                ]) {
                    sh '''
                    git config user.name "Sandeep Pandit"
                    git config user.email "54panditsandeep@gmail.com"
                    git tag ${RELEASE_VERSION}
                    git push \
                    https://${GIT_USER}:${GIT_TOKEN}@github.com/sandy24998/Project-Ironclad-Pipeline.git \
                    ${RELEASE_VERSION}
                    '''
                }
            }
        }

        stage('Update PROD GitOps Tag') {
            steps {
                script {
                    updateGitOpsTag('prod', env.RELEASE_VERSION)
                }
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