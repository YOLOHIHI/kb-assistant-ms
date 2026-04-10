# Deployment Matrix

| Deployment mode | Entry point | Default components | OCR | Eureka | LLM key optional | Best for |
| --- | --- | --- | --- | --- | --- | --- |
| Docker Compose | `deploy/docker/docker-compose.yml` | `db + embedder + index + doc + ai + gateway` | Profile-based | Profile-based | Yes | Local demo, quickest first boot |
| Kubernetes demo | `deploy/k8s/overlays/demo` | `db + embedder + index + doc + ai + gateway` | No | No | Yes | Single-cluster demo from published images |
| Kubernetes + OCR | `deploy/k8s/overlays/ocr` | `demo + ocr` | Yes | No | Yes | Image-heavy deployments that need OCR |
| Kubernetes + Eureka | `deploy/k8s/overlays/eureka` | `demo + eureka` | No | Yes | Yes | Service-discovery experiments only |

## Notes

- OCR and Eureka are both opt-in modules. They are not part of the default success path.
- Missing `SILICONFLOW_*` values must not block boot. The system stays reachable and falls back to degraded answer mode.
- For Kubernetes, prefer immutable tags such as `sha-<commit>` or release tags instead of `latest`.
- Gateway readiness endpoint is `/api/health`; liveness and startup checks should use `/api/health/live`.
