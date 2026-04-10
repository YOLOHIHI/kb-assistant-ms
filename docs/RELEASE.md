# Release Notes For Deployment Artifacts

## Image Publishing

Deployment images are published by `.github/workflows/publish-images.yml`.

Published repositories:

- `ghcr.io/<owner>/kb-gateway`
- `ghcr.io/<owner>/kb-index-service`
- `ghcr.io/<owner>/kb-doc-service`
- `ghcr.io/<owner>/kb-ai-service`
- `ghcr.io/<owner>/kb-eureka-server`
- `ghcr.io/<owner>/kb-embedder`
- `ghcr.io/<owner>/kb-ocr-service`

## Tag Strategy

Each publish run may emit:

- `sha-<commit>`
- branch tag
- release tag

`latest` is reserved for the repository default branch.

## What Kubernetes Users Should Pin

For `deploy/k8s/overlays/demo` and the optional overlays:

- Prefer `sha-<commit>` for reproducible demos and cluster deployments.
- Prefer release tags for externally documented versions.
- Use `latest` only for quick internal iteration.

## Verification Expectations

Repository-published images are built by `.github/workflows/publish-images.yml`.

Before release or external delivery, at minimum verify:

- Docker Compose config renders: `docker compose -f deploy/docker/docker-compose.yml config`
- Kubernetes manifests render: `kubectl kustomize deploy/k8s/overlays/demo`
- Gateway readiness endpoint returns expected status after deployment: `GET /api/health`
- Gateway liveness endpoint returns `200`: `GET /api/health/live`

## Manual Deployment Checks

- Docker Compose:
  - `docker compose -f deploy/docker/docker-compose.yml up -d --build`
  - `curl http://localhost:8080/api/health`
  - `curl http://localhost:8080/api/health/live`
- Kubernetes:
  - `kubectl apply -k deploy/k8s/overlays/demo`
  - `kubectl rollout status deployment/kb-gateway -n kb-assistant`
  - `kubectl port-forward svc/kb-gateway 8080:8080 -n kb-assistant`
  - `curl http://localhost:8080/api/health`
  - `curl http://localhost:8080/api/health/live`
