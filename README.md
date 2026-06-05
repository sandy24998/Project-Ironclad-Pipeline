# Project-Ironclad-Pipeline

Automated GitOps CI/CD pipeline featuring PR validation, immutable Docker image promotion (DEV/QA/UAT), semantic versioning, and zero-downtime rolling updates using Kubernetes.

## 📋 Overview

Project Ironclad is an end-to-end DevOps CI/CD pipeline implementation demonstrating enterprise-grade practices for continuous integration, delivery, and deployment. It showcases automated testing, Docker containerization, Docker Hub image management, and Kubernetes-based orchestration across multiple environments.

## 🛠 Technology Stack

- **Version Control**: GitHub
- **CI/CD Automation**: Jenkins
- **Containerization**: Docker
- **Container Registry**: Docker Hub
- **Orchestration**: Kubernetes (Minikube)
- **Application**: Flask (Python 3.12)
- **Testing**: pytest

## 🏗 Architecture

```
Developer
    ↓
Feature Branch
    ↓
Pull Request
    ↓
PR Validation (Automated Tests)
    ↓
Code Review & Merge to Main
    ↓
Docker Build & Push
    ↓
DEV Deployment
    ↓
QA Deployment
    ↓
UAT Deployment
    ↓
Git Tag Release
    ↓
PROD Deployment (Zero-Downtime Rolling Updates)
```

## 📁 Project Structure

```
Project-Ironclad-Pipeline/
├── app/                           # Flask application
│   ├── app.py                     # Main Flask application
│   ├── test_app.py               # Unit tests
│   ├── requirements.txt           # Python dependencies
│   └── Dockerfile                # Application container image
├── jenkins/                       # Jenkins pipeline definitions
│   ├── pr-validation.groovy      # Pull request validation pipeline
│   ├── build-pipeline.groovy     # Main build pipeline
│   ├── build-and-push-pipeline.groovy  # Build and push to Docker Hub
│   ├── deploy-dev-pipeline.groovy     # DEV environment deployment
│   ├── deploy-qa-pipeline.groovy      # QA environment deployment
│   ├── deploy-uat-pipeline.groovy     # UAT environment deployment
│   └── deploy-prod-pipeline.groovy    # PROD environment deployment
├── k8s/                          # Kubernetes manifests
│   ├── deployment.yaml           # Base deployment configuration
│   ├── service.yaml              # Service configuration
│   ├── dev/deployment.yaml       # DEV environment override
│   ├── prod/deployment.yaml      # PROD environment override
│   ├── qa/deployment.yaml        # QA environment override
│   └── uat/deployment.yaml       # UAT environment override
├── scripts/                       # Deployment helper scripts
│   ├── deploy-dev.sh            # DEV deployment script
│   ├── deploy-qa.sh             # QA deployment script
│   ├── deploy-uat.sh            # UAT deployment script
│   └── deploy-prod.sh           # PROD deployment script
├── docs/                         # Documentation
│   └── git-workflow.md          # Git workflow documentation
├── Jenkinsfile                  # Main Jenkins pipeline definition
├── Jenkinsfile.pr-validation    # PR validation pipeline definition
└── README.md                    # This file
```

## 🚀 Getting Started

### Prerequisites

- GitHub account with repository access
- Jenkins server configured with Git and Docker plugins
- Docker and Docker Hub account
- kubectl configured to access Kubernetes cluster (Minikube for local development)
- Python 3.12+ (for local testing)

### Local Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/<your-org>/Project-Ironclad-Pipeline.git
   cd Project-Ironclad-Pipeline
   ```

2. **Set up Python environment**
   ```bash
   python3 -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

3. **Install dependencies**
   ```bash
   pip install -r app/requirements.txt
   ```

4. **Run tests locally**
   ```bash
   cd app
   pytest -v
   cd ..
   ```

5. **Run the application locally**
   ```bash
   python app/app.py
   ```
   The application will be available at `http://localhost:5000`

## 🔄 CI/CD Pipeline Workflows

### 1. Pull Request Validation
- **Trigger**: When a PR is created against `main` branch
- **Pipeline**: `Jenkinsfile.pr-validation`
- **Steps**:
  - Checkout code
  - Run automated tests
  - Generate build metadata
  - Validate code quality
  - Post results to PR

### 2. Main Build Pipeline
- **Trigger**: When changes are merged to `main` branch
- **Pipeline**: `Jenkinsfile`
- **Steps**:
  - Checkout code
  - Get build metadata (build number, git commit SHA)
  - Run test suite
  - Build Docker image
  - Push to Docker Hub
  - Deploy to DEV environment

### 3. Progressive Deployment
After successful build and DEV deployment:
- **QA Deployment**: Automated deployment to QA environment
- **UAT Deployment**: Automated deployment to UAT environment
- **PROD Deployment**: Triggered after UAT sign-off with zero-downtime rolling updates

## 🌳 Git Workflow

### Branch Strategy

- **`main`**: Production-ready code, protected branch
- **`feature/<feature-name>`**: Feature branches for development

### Creating a Feature Branch

```bash
git checkout -b feature/new-endpoint
```

### Committing Changes

```bash
git add .
git commit -m "add new API endpoint"
```

### Pushing and Creating PR

```bash
git push origin feature/new-endpoint
```
Then create a Pull Request on GitHub for code review.

### Merging

After PR approval and tests pass, merge to `main` branch. CI/CD pipeline automatically triggers.

## 📊 Environments

| Environment | Purpose | Deployment Type | Users |
|-------------|---------|-----------------|-------|
| **DEV** | Development & testing | Automatic on build | Developers |
| **QA** | Quality assurance | Automatic after DEV | QA Team |
| **UAT** | User acceptance testing | Automatic after QA | Business Users |
| **PROD** | Production/Live | Manual approval | End Users |

## 🐳 Docker

### Building Locally

```bash
docker build -t ironclad-app:latest app/
```

### Running Docker Container

```bash
docker run -p 5000:5000 -e APP_ENV=dev ironclad-app:latest
```

### Image Details

- **Base Image**: `python:3.12-slim`
- **Registry**: Docker Hub (`sandy541998/ironclad-app`)
- **Tagging**: `{BUILD_NUMBER}-{GIT_COMMIT_SHORT}`

## ☸️ Kubernetes

### Application Endpoints

- **Health Check**: `GET /health`
- **Status**: `GET /`

### Key Features

- **Replicas**: 2 pods for high availability
- **Rolling Updates**: Zero-downtime deployments (maxUnavailable: 1, maxSurge: 1)
- **Revision History**: Maintains last 5 deployments for quick rollback
- **Termination Grace Period**: 30 seconds for graceful shutdown

### Deploying via kubectl

```bash
# DEV environment
kubectl apply -f k8s/dev/deployment.yaml
kubectl apply -f k8s/service.yaml

# PROD environment
kubectl apply -f k8s/prod/deployment.yaml
kubectl apply -f k8s/service.yaml
```

## 🧪 Testing

### Unit Tests

```bash
cd app
pytest -v
```

### Test Coverage

Tests are automatically run in Jenkins during:
- PR validation
- Main branch builds

## 📝 API Endpoints

### Home Endpoint
```
GET /
```
Returns application metadata:
- Application name
- Version
- Environment
- Hostname
- Status

### Health Check
```
GET /health
```
Returns health status (used by Kubernetes liveness/readiness probes)

## 🔐 Security Practices

- Non-root user in Docker container (`appuser`)
- Read-only filesystem where applicable
- Proper secret management through environment variables
- Network policies via Kubernetes service definitions

## 📈 Versioning

- **Docker Image Tag**: `{BUILD_NUMBER}-{GIT_COMMIT_SHORT}`
- **Application Version**: Semantic versioning in source code
- **Kubernetes Revision History**: Automatic tracking for rollbacks

## 🐛 Troubleshooting

### Jenkins Pipeline Fails
1. Check Jenkins logs for detailed error messages
2. Verify Docker Hub credentials are configured
3. Ensure Kubernetes cluster is accessible

### Deployment Issues
1. Check pod status: `kubectl get pods`
2. View logs: `kubectl logs <pod-name>`
3. Describe pod: `kubectl describe pod <pod-name>`

### Application Not Responding
1. Verify service is running: `kubectl get svc`
2. Check port forwarding is correct
3. Review application logs for errors

## 📚 Additional Documentation

- [Git Workflow Documentation](docs/git-workflow.md)

## 👨‍💻 Contributing

1. Create a feature branch from `main`
2. Make your changes
3. Submit a Pull Request
4. Wait for automated tests to pass and code review
5. Merge to `main` after approval

## 📄 License

This project is part of the DevOps learning curriculum.

## 👥 Contact & Support

For questions or issues, please create an issue in the GitHub repository.