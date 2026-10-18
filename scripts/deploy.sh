
#!/usr/bin/env bash
set -euo pipefail

ENVIRONMENT="${1:?Usage: deploy.sh <environment> <image-tag>}"
IMAGE_TAG="${2:?Usage: deploy.sh <environment> <image-tag>}"

IMAGE_NAME="sandy541998/ironclad-app"

case "$ENVIRONMENT" in
  dev|qa|uat|prod) ;;
  *)
    echo "ERROR: Invalid environment: $ENVIRONMENT"
    exit 1
    ;;
esac

# Validate the tag before using it in shell commands.
if [[ ! "$IMAGE_TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; then
  echo "ERROR: Invalid image tag: $IMAGE_TAG"
  exit 1
fi

OVERLAY="k8s/overlays/${ENVIRONMENT}"

if [[ ! -f "$OVERLAY/kustomization.yaml" ]]; then
  echo "ERROR: Kustomization file not found: $OVERLAY"
  exit 1
fi

IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
RENDERED_FILE="$(mktemp)"

trap 'rm -f "$RENDERED_FILE"' EXIT

echo "=================================="
echo "Environment : $ENVIRONMENT"
echo "Image       : $IMAGE"
echo "=================================="

# Render the environment overlay.
kubectl kustomize "$OVERLAY" |
  sed "s|${IMAGE_NAME}:IMAGE_TAG_PLACEHOLDER|${IMAGE}|g" \
  > "$RENDERED_FILE"

# Ensure the intended image was rendered.
if ! grep -Fq "image: ${IMAGE}" "$RENDERED_FILE"; then
  echo "ERROR: Expected image was not found in rendered manifests"
  exit 1
fi

if grep -q 'IMAGE_TAG_PLACEHOLDER' "$RENDERED_FILE"; then
  echo "ERROR: Unresolved image placeholder"
  exit 1
fi

# Apply the rendered manifests.
kubectl apply -f "$RENDERED_FILE"

# Wait for the Deployment rollout.
kubectl rollout status deployment/ironclad-app \
  -n "$ENVIRONMENT" \
  --timeout=180s

# Verify the configured Deployment image.
DEPLOYED_IMAGE="$(
  kubectl get deployment ironclad-app \
    -n "$ENVIRONMENT" \
    -o jsonpath='{.spec.template.spec.containers[?(@.name=="ironclad-app")].image}'
)"

if [[ "$DEPLOYED_IMAGE" != "$IMAGE" ]]; then
  echo "ERROR: Expected $IMAGE but found $DEPLOYED_IMAGE"
  exit 1
fi

echo "SUCCESS: $ENVIRONMENT is running $DEPLOYED_IMAGE"
