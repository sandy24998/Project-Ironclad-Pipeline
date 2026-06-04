#!/bin/bash

set -e

IMAGE_TAG=$1

kubectl set image deployment/ironclad-app \
ironclad-app=sandy541998/ironclad-app:${IMAGE_TAG} \
-n dev

kubectl rollout status deployment/ironclad-app -n dev