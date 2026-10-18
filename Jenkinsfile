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
        sshagent(credentials: ['github-ssh']) {
            sh '''
                set -eu

                echo "====================================="
                echo "Updating DEV GitOps Image Tag"
                echo "IMAGE_TAG: ${IMAGE_TAG}"
                echo "====================================="

                # Verify required tools and variables
                test -n "${IMAGE_TAG}"
                command -v git
                command -v kustomize

                # Clone the GitOps branch
                rm -rf gitops-work

                git clone --branch gitops-dev --single-branch \
                    git@github.com:sandy24998/Project-Ironclad-Pipeline.git \
                    gitops-work

                cd gitops-work/k8s/overlays/dev

                # Update the image tag in DEV overlay
                kustomize edit set image \
                    sandy541998/ironclad-app=sandy541998/ironclad-app:${IMAGE_TAG}

                echo "Updated DEV kustomization.yaml:"
                cat kustomization.yaml

                # Verify the rendered image
                cd ../../../

                echo "Rendered DEV image:"
                kustomize build k8s/overlays/dev | grep 'image:'

                if kustomize build k8s/overlays/dev | \
                    grep -q 'IMAGE_TAG_PLACEHOLDER'; then
                    echo "ERROR: Image placeholder still exists!"
                    exit 1
                fi

                # Configure Git identity
                git config user.name "Sandeep Pandit"
                git config user.email "54panditsandeep@gmail.com"

                # Stage the GitOps change
                git add k8s/overlays/dev/kustomization.yaml
                git diff --cached --check

                # Skip commit if nothing changed
                if git diff --cached --quiet; then
                    echo "GitOps image tag is already up to date."
                    exit 0
                fi

                # Commit and push to GitOps branch
                git commit -m "chore(gitops): update DEV image to ${IMAGE_TAG}"

                git push origin HEAD:gitops-dev

                echo "DEV GitOps branch updated successfully!"
            '''
        }
    }
}


stage('Deploy DEV') {
    steps {
        sh '''
        chmod +x scripts/deploy.sh
        ./scripts/deploy.sh dev ${IMAGE_TAG}
        '''
    }
}

stage('Verify DEV Deployment') {
    steps {
        sh '''
        kubectl rollout status deployment/ironclad-app -n dev
        '''
    }
}

stage('QA Approval') {
    steps {
        input(
            message: 'Promote build to QA?',
            ok: 'Deploy QA'
        )
    }
}

stage('Deploy QA') {
    steps {
        sh '''
        chmod +x scripts/deploy.sh
        ./scripts/deploy.sh qa ${IMAGE_TAG}
        '''
    }
}

stage('Verify QA Deployment') {
    steps {
        sh '''
        kubectl rollout status deployment/ironclad-app -n qa
        '''
    }
}

stage('UAT Approval') {
    steps {
        input(
            message: 'Promote build to UAT?',
            ok: 'Deploy UAT'
        )
    }
}

stage('Deploy UAT') {
    steps {
        sh '''
        chmod +x scripts/deploy.sh
        ./scripts/deploy.sh uat ${IMAGE_TAG}
        '''
    }
}

stage('Verify UAT Deployment') {
    steps {
        sh '''
        kubectl rollout status deployment/ironclad-app -n uat
        '''
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

stage('Deploy PROD') {
    steps {
        sh '''
        chmod +x scripts/deploy.sh
        ./scripts/deploy.sh prod ${RELEASE_VERSION}
        '''
    }
}

stage('Verify PROD Deployment') {
    steps {
        sh '''
        kubectl rollout status deployment/ironclad-app -n prod
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