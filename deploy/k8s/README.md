# Kubernetes Deployment

`deploy/k8s/` 现在提供两套内容：

- 正式支持的 GHCR + Kustomize 交付包：`base/` 和 `overlays/`
- 仅用于历史参考的 legacy flat YAML：`00-namespace.yaml` 到 `08-postgres.yaml`

默认成功路径是 `deploy/k8s/overlays/demo`。它只包含：

- `kb-postgres`
- `kb-embedder`
- `kb-index-service`
- `kb-doc-service`
- `kb-ai-service`
- `kb-gateway`

默认路径**不包含**：

- `kb-ocr-service`
- `kb-eureka-server`

OCR 和 Eureka 都是显式 opt-in。

## 目录结构

```text
deploy/k8s/
├─ base/                  # 公共 Deployment / Service / PVC
├─ overlays/
│  ├─ demo/               # 默认 overlay，GHCR published images + secretGenerator
│  ├─ ocr/                # 在 demo 基础上追加 OCR
│  └─ eureka/             # 在 demo 基础上追加 Eureka
├─ .env.example           # 根级 Secret 键参考
├─ README.md
└─ 00-namespace.yaml ...  # legacy flat YAML，已弃用
```

## 1. 前置条件

- Kubernetes 集群
- `kubectl`（内置 Kustomize 即可）
- 可用的持久卷能力
- 能拉取 GHCR 镜像

默认 overlay 假设：

- 你会使用 GHCR 已发布镜像，而不是本地 `:latest`
- 你接受默认路径中 Eureka 关闭、OCR 关闭
- 你可以通过 `kubectl port-forward` 访问网关

## 2. 准备 Secret 模板

复制 overlay 的 `.env.example`：

```powershell
Copy-Item .\deploy\k8s\overlays\demo\.env.example .\deploy\k8s\overlays\demo\.env
```

然后填写真实值：

- `KB_INTERNAL_TOKEN`
- `KB_DB_USER`
- `KB_DB_PASSWORD`
- `KB_BOOTSTRAP_ADMIN_USER`
- `KB_BOOTSTRAP_ADMIN_PASSWORD`
- `KB_CRYPTO_MASTER_KEY`
- `SILICONFLOW_API_KEY`
- `SILICONFLOW_MODEL`
- `SILICONFLOW_BASE_URL`

规则：

- `.env` 只用于本地 `secretGenerator`，不要提交。
- `SILICONFLOW_*` 可以留空；系统会进入 degraded answer mode，但不会阻止启动。
- `deploy/k8s/.env.example` 仍保留在仓库根下，作为 Secret 键集合参考；真正被 Kustomize 读取的是 `overlays/demo/.env`。

## 3. 选择镜像命名

`deploy/k8s/overlays/demo/kustomization.yaml` 默认使用占位 GHCR 命名：

- `ghcr.io/replace-me-owner/kb-gateway`
- `ghcr.io/replace-me-owner/kb-index-service`
- `ghcr.io/replace-me-owner/kb-doc-service`
- `ghcr.io/replace-me-owner/kb-ai-service`
- `ghcr.io/replace-me-owner/kb-eureka-server`
- `ghcr.io/replace-me-owner/kb-embedder`
- `ghcr.io/replace-me-owner/kb-ocr-service`

部署前只需要改两个信息：

- 把 `replace-me-owner` 换成你的 GitHub owner / org
- 把 `newTag: latest` 换成你要固定的 tag，例如 `sha-<commit>` 或 release tag

建议：

- 开发验证可用 `latest`
- 演示和集群环境优先固定到 `sha-<commit>` 或正式 release tag

## 4. 渲染与部署默认 overlay

先渲染：

```powershell
kubectl kustomize .\deploy\k8s\overlays\demo
```

再部署：

```powershell
kubectl apply -k .\deploy\k8s\overlays\demo
kubectl rollout status statefulset/kb-postgres -n kb-assistant
kubectl rollout status deployment/kb-embedder -n kb-assistant
kubectl rollout status deployment/kb-index-service -n kb-assistant
kubectl rollout status deployment/kb-doc-service -n kb-assistant
kubectl rollout status deployment/kb-ai-service -n kb-assistant
kubectl rollout status deployment/kb-gateway -n kb-assistant
kubectl port-forward svc/kb-gateway 8080:8080 -n kb-assistant
curl.exe http://localhost:8080/api/health
curl.exe http://localhost:8080/api/health/live
```

默认 overlay 的暴露方式是 `ClusterIP + port-forward`，这样对 kind、minikube 和普通单节点集群都更稳。

## 5. 可选模块

### OCR overlay

OCR 不属于默认启动路径。启用时改用：

```powershell
kubectl apply -k .\deploy\k8s\overlays\ocr
```

这个 overlay 会：

- 追加 `kb-ocr-service`
- 把 `kb-doc-service` 的 `KB_OCR_URL` 指向 `http://kb-ocr-service:8070`

验证：

```powershell
kubectl rollout status deployment/kb-ocr-service -n kb-assistant
kubectl port-forward svc/kb-ocr-service 8070:8070 -n kb-assistant
curl.exe http://localhost:8070/health
```

### Eureka overlay

Eureka 只保留给服务发现实验，不是推荐默认路径：

```powershell
kubectl apply -k .\deploy\k8s\overlays\eureka
```

这个 overlay 会：

- 追加 `kb-eureka-server`
- 把 `kb-gateway`、`kb-index-service`、`kb-doc-service`、`kb-ai-service` 的 `EUREKA_ENABLED` 改成 `true`

验证：

```powershell
kubectl rollout status deployment/kb-eureka-server -n kb-assistant
kubectl port-forward svc/kb-eureka-server 8761:8761 -n kb-assistant
curl.exe http://localhost:8761/actuator/health
```

## 6. 功能矩阵

| 部署模式 | 默认组件 | OCR | Eureka | LLM Key 可留空 |
| --- | --- | --- | --- | --- |
| `overlays/demo` | `db + embedder + index + doc + ai + gateway` | 否 | 否 | 是 |
| `overlays/ocr` | `demo + ocr` | 是 | 否 | 是 |
| `overlays/eureka` | `demo + eureka` | 否 | 是 | 是 |

## 7. 上线后验证

推荐至少做以下检查：

```powershell
kubectl get pods -n kb-assistant
kubectl port-forward svc/kb-gateway 8080:8080 -n kb-assistant
curl.exe http://localhost:8080/api/health
curl.exe http://localhost:8080/api/health/live
curl.exe http://localhost:8080/
curl.exe http://localhost:8080/chat
curl.exe http://localhost:8080/admin
```

探针语义：

- `readinessProbe` 使用 `/api/health`，会校验 `kb-ai-service`、`kb-doc-service`、`kb-index-service`
- `livenessProbe` 和 `startupProbe` 使用 `/api/health/live`，只表示网关进程本身存活

## 8. GHCR 镜像发布

镜像发布由 `.github/workflows/publish-images.yml` 负责，输出命名固定为：

- `ghcr.io/<owner>/kb-gateway`
- `ghcr.io/<owner>/kb-index-service`
- `ghcr.io/<owner>/kb-doc-service`
- `ghcr.io/<owner>/kb-ai-service`
- `ghcr.io/<owner>/kb-eureka-server`
- `ghcr.io/<owner>/kb-embedder`
- `ghcr.io/<owner>/kb-ocr-service`

Tag 策略：

- `sha-<commit>`
- branch tag
- release tag
- `latest` 只从默认分支产生

## 9. 外部 PostgreSQL 预留形态

当前正式提供的是内置 `kb-postgres` 的 demo overlay。后续如果要切到外部托管 PostgreSQL，保留的形态是：

- 不再应用 `base/postgres.yaml`
- 把 `KB_DB_URL` 指向外部实例
- Secret 仍通过本地 `.env` 生成

这条路径目前没有单独 overlay，但文档和资源拆分已经为它留出了边界。

## 10. 关于 legacy flat YAML

这些文件仍然保留：

- `00-namespace.yaml`
- `00c-eureka.yaml`
- `01-secrets.yaml`
- `02-embedder.yaml`
- `02-ocr.yaml`
- `03-index.yaml`
- `04-doc.yaml`
- `05-ai.yaml`
- `06-gateway.yaml`
- `07-hpa.yaml`
- `08-postgres.yaml`

它们现在的角色只是：

- 历史参考
- 本地实验对照
- 与新 `base/` / `overlays/` 结构比对

不要再把这些 flat YAML 当成对外推荐入口。
