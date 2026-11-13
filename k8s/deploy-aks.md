# EpitomeJob W4 — deploying to AKS later

> LATER STEPS ONLY. Nothing in this file is run in W4 — the user has no Azure
> subscription and no Stripe account, and W4 runs on kind only
> (W4-API-CONTRACT.md §0/§4). Do not run any command below until Azure access
> exists and the kind stack is proven (contract §8 gate item 10).

## 0. Prerequisites

- Azure subscription + `az` CLI signed in (`az login`), with permission to
  create resource groups, ACR, and AKS.
- A DNS zone / domain you own for the web ingress host.
- The kind stack green first: `bash k8s/deploy-kind.sh` → all pods ready,
  web + backend reachable via port-forward, Coach answering with Ollama up.

## 1. ACR — build + push the images

kind builds images locally (`epitomejob/<service>:w4`) and loads them with
`kind load docker-image`. AKS nodes cannot see your laptop's Docker daemon,
so the same images go to Azure Container Registry instead:

```bash
ACR=epitomejobacr            # must be globally unique, lowercase
RG=epitomejob-rg
az acr create --resource-group "$RG" --name "$ACR" --sku Basic
az acr login --name "$ACR"

docker build -t "$ACR.azurecr.io/epitomejob/backend:w4" ./backend
docker build -t "$ACR.azurecr.io/epitomejob/agent-brain:w4" ./agent-brain
docker build -t "$ACR.azurecr.io/epitomejob/web:w4" ./web

docker push "$ACR.azurecr.io/epitomejob/backend:w4"
docker push "$ACR.azurecr.io/epitomejob/agent-brain:w4"
docker push "$ACR.azurecr.io/epitomejob/web:w4"
```

Then change the `image:` fields in `k8s/backend.yaml`,
`k8s/agent-brain.yaml`, `k8s/mcp-server.yaml`, `k8s/temporal-worker.yaml`,
and `k8s/web.yaml` from `epitomejob/<service>:w4` to
`$ACR.azurecr.io/epitomejob/<service>:w4`. The infra images
(`pgvector/pgvector:pg16`, `rustfs/rustfs:1.0.1`,
`temporalio/auto-setup:1.29.7`, `temporalio/ui:2.56.1`) stay as-is — they
are pulled from Docker Hub by AKS. (`mcp-server` and `temporal-worker`
keep reusing the `agent-brain` image, as in kind.)

## 2. AKS cluster

```bash
az aks create --resource-group "$RG" --name epitomejob-aks \
  --node-count 2 --node-vm-size Standard_B2s \
  --attach-acr "$ACR" --generate-ssh-keys
az aks get-credentials --resource-group "$RG" --name epitomejob-aks
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/
```

Notes:

- `Standard_B2s` (2 vCPU / 4 GiB) is a starting size for this dev stack;
  size up if pods stay Pending. The manifests request ~1.2 CPU / ~1.9 Gi
  total; AKS system pods need headroom on top.
- `kind/cluster.yaml` is NOT used for AKS — it is kind-only. Delete nothing;
  just don't apply it.
- The `standard` StorageClass in `postgres.yaml` / `rustfs.yaml` is the
  kind default. On AKS the default StorageClass is `managed` (Azure Disk)
  or `managed-csi`; change `storageClassName` to the AKS default (or drop
  the field to take the cluster default) before applying.

## 3. Ingress — ingress-nginx + DNS

kind reaches services with `kubectl port-forward` (see `deploy-kind.sh`).
AKS needs a real ingress instead:

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace
```

Then add an `Ingress` (new file, not in W4) routing your domain to the
`web` Service (port 3000) — and, if you want it public, `temporal-ui`
(8233) behind auth. Point your DNS A record at the ingress controller's
external IP (`kubectl -n ingress-nginx get svc ingress-nginx-controller`).
Add TLS with cert-manager + Let's Encrypt when the domain is live; do not
expose the backend, postgres, or rustfs Services publicly.

## 4. Secrets

Same rule as kind (`secret.yaml` header): **never commit real secrets**.
Recreate them in the cluster with `kubectl create secret`:

```bash
kubectl -n epitomejob create secret generic epitomejob-secrets \
  --from-literal=POSTGRES_PASSWORD='<real password>' \
  --from-literal=APP_JWT_SECRET='<real 64-hex-char secret>' \
  --from-literal=MINIO_SECRET_KEY='<real minio secret>' \
  --from-literal=STRIPE_SECRET_KEY='<sk_...>' \
  --from-literal=STRIPE_WEBHOOK_SECRET='<whsec_...>' \
  --dry-run=client -o yaml | kubectl apply -f -
```

The manifests already read these exact keys, so no YAML changes are needed
— just restart the Deployments after replacing the Secret. The placeholder
`secret.yaml` must NOT be applied on AKS (it would overwrite the real
Secret with dev defaults; delete it from the apply set or keep it out of
the AKS workflow entirely).

Production option: Azure Key Vault + the Secrets Store CSI driver
(`secrets-store.csi.k8s.io`) so pods mount secrets from the vault instead
of etcd. That is a later step, not part of W4.

## 5. What changes vs kind

| Area | kind (W4) | AKS (later) |
|---|---|---|
| Image registry | Local Docker builds + `kind load docker-image` | ACR build + push; `image:` fields repointed |
| Cluster | `kind/cluster.yaml` (1 control-plane + 1 worker, kindest/node v1.37.0) | `az aks create`; kind config unused |
| Access | `kubectl port-forward` per Service | ingress-nginx + DNS (+ TLS via cert-manager) |
| Secrets | Placeholder `secret.yaml` (dev defaults) | `kubectl create secret` with real values; Key Vault as the production option |
| Postgres | In-cluster `pgvector/pgvector:pg16` + PVC | Managed Postgres: Azure Database for PostgreSQL Flexible Server (with the `pgvector` extension enabled). Point `SPRING_DATASOURCE_URL` / `DATABASE_URL` at it and delete the in-cluster `postgres` Deployment + PVC — do NOT run two databases. Same for the Temporal DBs: create `temporal` + `temporal_visibility` on the managed server. |
| Object storage | In-cluster RustFS + PVC | Azure Blob Storage (or keep RustFS in-cluster if S3-compat is needed); update `MINIO_ENDPOINT` / keys accordingly |
| Temporal | In-cluster auto-setup | Keep in-cluster to start; a managed Temporal Cloud namespace is the later option |
| Ollama | Mac host via `host.docker.internal:11434` | **Does not exist on AKS.** Host a GPU node + Ollama Deployment, or point `OLLAMA_BASE_URL` at a hosted LLM endpoint. This is the biggest later decision — the Coach needs a model somewhere. |
| Volumes | kind `standard` StorageClass, data deleted with the cluster | AKS `managed`/`managed-csi`; PVCs survive node replacement, but back up managed Postgres + Blob Storage on Azure's terms |

## 6. Billing on AKS

Same env-gated rule as everywhere (contract §1): billing stays OFF
(`BILLING_ENABLED=false`) until Stripe is linked. When it is, set the real
`STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` in the Secret above and set
`BILLING_ENABLED=true` in `configmap.yaml`. The webhook (`POST
/billing/webhook`) must be reachable from Stripe — expose the backend
through the ingress with a public host and register that URL in the Stripe
dashboard. No other code change is needed.
