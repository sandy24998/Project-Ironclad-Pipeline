# Git Workflow

This document outlines the standard Git workflow for contributing to the Project Ironclad Pipeline.

## Branch Strategy

### Branch Types

- **`main`**: Production-ready code, protected branch
  - All code merged to `main` triggers CI/CD pipeline
  - Requires PR review and passing automated tests
  - Deployments to all environments (DEV → QA → UAT → PROD)

- **`feature/*`**: Feature development branches
  - Created from `main` for new features or bug fixes
  - Naming: `feature/descriptive-name` (e.g., `feature/user-auth`, `feature/api-v2`)
  - Should be short-lived (ideally < 1 week)

## Step-by-Step Workflow

### 1. Update Main Branch

Before creating a new feature branch, ensure you have the latest code:

```bash
git checkout main
git pull origin main
```

### 2. Create Feature Branch

Create a new branch for your feature or bug fix:

```bash
git checkout -b feature/<feature-name>
```

**Examples:**
```bash
git checkout -b feature/add-health-endpoint
git checkout -b feature/fix-deployment-config
git checkout -b feature/optimize-container-build
```

**Naming Convention:**
- Use lowercase
- Use hyphens to separate words (not underscores or camelCase)
- Be descriptive but concise
- Avoid generic names like `feature/update` or `feature/fix`

### 3. Make Changes

Edit files as needed for your feature or fix.

### 4. Commit Changes

Commit your work with clear, descriptive commit messages:

```bash
git add .
git commit -m "add health check endpoint"
```

**Commit Message Best Practices:**
- Start with a verb (add, fix, update, remove, refactor)
- Be specific about what changed
- Keep the first line under 50 characters
- Add detailed explanation in the body if needed

**Examples:**
```bash
git commit -m "add health check endpoint for k8s probes"
git commit -m "fix docker build metadata extraction"
git commit -m "update deployment strategy for zero-downtime"
```

### 5. Push Branch

Push your feature branch to the remote repository:

```bash
git push origin feature/<feature-name>
```

**Example:**
```bash
git push origin feature/add-health-endpoint
```

**First Push:**
If this is the first push of a new branch, Git may prompt you to set the upstream branch:

```bash
git push -u origin feature/add-health-endpoint
```

### 6. Create Pull Request

On GitHub, create a Pull Request from your feature branch to `main`:

1. Go to the repository on GitHub
2. Click "Pull requests" tab
3. Click "New pull request"
4. Select your feature branch as the source
5. Ensure `main` is the target branch
6. Add a descriptive title and description
7. Click "Create pull request"

**PR Description Template:**

```markdown
## Description
Brief description of the changes made

## Changes
- Change 1
- Change 2
- Change 3

## Related Issue
Closes #123 (if applicable)

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Documentation update
- [ ] Configuration change

## How to Test
Steps to verify the changes work correctly

## Checklist
- [ ] Code follows the project style guidelines
- [ ] Tests have been added/updated
- [ ] All tests pass locally
- [ ] Documentation has been updated
```

## 7. Automated Testing

Once a PR is created, automated tests run automatically:

```
Pull Request Created
    ↓
GitHub Actions/Jenkins PR Validation Triggers
    ↓
Tests Run
    ↓
Results Posted to PR
    ↓
PR Status Updated (Success/Failure)
```

### What Gets Tested:
- Unit tests in `app/test_app.py`
- Code quality checks
- Build validation

If tests fail:
1. Review the failing test output
2. Make necessary fixes in your feature branch
3. Push the changes - PR automatically updates
4. Tests re-run automatically

### 8. Code Review

After automated tests pass:
- Team members review your code
- Request changes if needed
- Approval required before merge

**During review, be ready to:**
- Explain your implementation choices
- Make requested modifications
- Push additional commits if needed

### 9. Merge to Main

Once approved and all checks pass:

1. Click "Squash and merge" or "Create a merge commit" on GitHub
2. Confirm the merge
3. Delete the feature branch (GitHub offers this option)

Alternatively, merge from command line:

```bash
git checkout main
git pull origin main
git merge feature/<feature-name>
git push origin main
git branch -d feature/<feature-name>  # Delete local branch
git push origin --delete feature/<feature-name>  # Delete remote branch
```

### 10. CI/CD Pipeline Triggers

After merging to `main`, the full CI/CD pipeline automatically executes:

```
Code Merged to Main
    ↓
Build Metadata Generated
    ↓
Tests Run
    ↓
Docker Image Built & Pushed
    ↓
DEV Deployment
    ↓
QA Deployment
    ↓
UAT Deployment
    ↓
Ready for PROD (Manual Trigger)
```

## Workflow Visualization

```
main (Protected Branch)
    ↑
    │ (PR Review & Tests)
    │
feature/new-feature
│
├─ Commit: "add new endpoint"
├─ Commit: "fix configuration"
└─ Commit: "update tests"
```

## Common Commands Reference

```bash
# View current branch
git branch

# List all branches (local and remote)
git branch -a

# Switch to main
git checkout main

# Pull latest changes
git pull origin main

# View commit history
git log --oneline -10

# View changes before committing
git status
git diff

# Undo changes (before commit)
git checkout -- <file>

# Delete local branch
git branch -d feature/<feature-name>

# Delete remote branch
git push origin --delete feature/<feature-name>

# Sync feature branch with main
git fetch origin
git rebase origin/main
```

## Best Practices

✅ **DO:**
- Create a new branch for each feature or bug fix
- Keep branches short-lived (< 1 week)
- Write clear, descriptive commit messages
- Test changes locally before pushing
- Request reviews from team members
- Merge via GitHub (maintains history)

❌ **DON'T:**
- Commit directly to `main`
- Create branches from other feature branches
- Mix multiple features in one PR
- Force push to `main`
- Ignore failing tests before merging
- Leave feature branches unmerged for extended periods

## Troubleshooting

### PR shows merge conflict

1. Update your local main:
   ```bash
   git fetch origin
   git checkout main
   git pull origin main
   ```

2. Rebase your feature branch:
   ```bash
   git checkout feature/<feature-name>
   git rebase main
   ```

3. Resolve conflicts in your editor
4. Complete the rebase:
   ```bash
   git add .
   git rebase --continue
   ```

5. Force push to update the PR:
   ```bash
   git push origin feature/<feature-name> --force-with-lease
   ```

### Accidentally committed to main

```bash
git reset --soft HEAD~1  # Undo last commit, keep changes
git checkout -b feature/my-feature
git commit -m "descriptive message"
git push origin feature/my-feature
```

### Need to sync with latest main

```bash
git fetch origin
git rebase origin/main
git push origin feature/<feature-name> --force-with-lease
```

## Additional Resources

- [Project README](../README.md)
- GitHub Flow: https://guides.github.com/introduction/flow/
- Commit Message Guidelines: https://www.conventionalcommits.org/
