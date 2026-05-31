# Project-Ironclad-Pipeline
Automated GitOps CI/CD pipeline featuring PR validation, immutable Docker image promotion (DEV/QA/UAT), semantic versioning, and zero-downtime rolling updates.

End-to-End DevOps CI/CD Project using:

- GitHub
- Jenkins
- Docker
- Docker Hub
- Kubernetes (Minikube)

## Architecture

Developer
↓
Feature Branch
↓
Pull Request
↓
Jenkins Validation
↓
Merge Main
↓
Docker Build
↓
Docker Hub
↓
DEV
↓
QA
↓
UAT
↓
Git Tag Release
↓
PROD

## Environments

- dev
- qa
- uat
- prod

## Branch Strategy

main

feature/*