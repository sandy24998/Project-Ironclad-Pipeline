def updateGitOpsTag(String environment, String imageTag) {
    if (!(environment in ['dev', 'qa', 'uat', 'prod'])) {
        error("Unsupported GitOps environment: ${environment}")
    }
    if (!(imageTag ==~ /^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/)) {
        error("Invalid Docker image tag: ${imageTag}")
    }

    dir("gitops-${environment}") {
        deleteDir()
        sshagent(credentials: ['github-ssh']) {
            withEnv([
                "GITOPS_ENV=${environment}",
                "GITOPS_IMAGE_TAG=${imageTag}",
                "GITOPS_IMAGE_NAME=${env.IMAGE_NAME}"
            ]) {
                sh '''
                    set -eu

                    git clone --branch gitops-dev --single-branch \
                        git@github.com:sandy24998/Project-Ironclad-Pipeline.git .

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

                    (
                        cd "k8s/overlays/${GITOPS_ENV}"
                        kustomize edit set image \
                            "${GITOPS_IMAGE_NAME}=${GITOPS_IMAGE_NAME}:${GITOPS_IMAGE_TAG}"
                    )

                    if ! kustomize build "k8s/overlays/${GITOPS_ENV}" | \
                        grep -Fq "image: ${GITOPS_IMAGE_NAME}:${GITOPS_IMAGE_TAG}"; then
                        echo "ERROR: Expected image tag was not rendered for ${GITOPS_ENV}"
                        exit 1
                    fi

                    git config user.name "Jenkins"
                    git config user.email "jenkins@example.com"
                    git add "$file"
                    git diff --cached --check

                    if git diff --cached --quiet; then
                        echo "${GITOPS_ENV} already points to ${GITOPS_IMAGE_TAG}"
                        exit 0
                    fi

                    git commit -m "Deploy ${GITOPS_IMAGE_TAG} to ${GITOPS_ENV}"
                    git push origin HEAD:gitops-dev
                '''
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

        stage('Test GitHub SSH Access') {
    steps {
        sshagent(credentials: ['github-ssh']) {
            sh '''
                set -eu
                ssh -o StrictHostKeyChecking=accept-new \
                    -T git@github.com || test "$?" -eq 1
            '''
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
                                description: 'Enter a new release version, e.g. v1.0.2'
                            )
                        ]
                    ).trim()

            // Accept only stable vMAJOR.MINOR.PATCH release tags.
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

            // A Git/network error fails the shell step; an empty result means no matching remote tag.
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
        sh '''
        docker push ${IMAGE_NAME}:${RELEASE_VERSION}
        '''
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