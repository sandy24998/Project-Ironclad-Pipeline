# Git Workflow

## Create Feature Branch

```bash
git checkout -b feature/<feature-name>
```

Example:

```bash
git checkout -b feature/api
```

## Commit Changes

```bash
git add .
git commit -m "add api endpoint"
```

## Push Branch

```bash
git push origin feature/api
```

## Create Pull Request

Feature Branch
↓
Pull Request
↓
Code Review
↓
Merge Main
