#!/bin/bash

set -e

RELEASE_VERSION=$1

kubectl set image deployment/ironclad-app \
ironclad-app=sandy541998/ironclad-app:${RELEASE_VERSION} \
-n prod

kubectl rollout status deployment/ironclad-app -n prod