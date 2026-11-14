#!/usr/bin/env bash
# EpitomeJob W4 — deploy the stack to a local kind cluster.
# Phase 5A (platform) + 5B (MCP split) — 2026-11-14, from main b59dc45:
# builds/loads gateway-service:5a, eureka-server:5a and mcp-service:5b in
# addition to the W4 images, waits for the new rollouts in dependency
# order, and prints port-forwards for gateway/eureka/kibana/jaeger.
# Resources: the full stack now requests ~2.5 CPU / ~4 Gi — Docker
# Desktop needs at least 6 CPU / 8 GB allocated or pods will stay Pending.
# W4-API-CONTRACT.md §4 + §8 gate item 10.
#
# Run from anywhere:  bash k8s/deploy-kind.sh
# (the script finds the repo root from its own location).
# Idempotent: safe to re-run — it reuses an existing cluster, rebuilds and
# reloads the images, re-applies the manifests, and re-waits for rollouts.
#
# Prerequisites (install once — direct darwin-amd64 binaries, NO Homebrew):
#   kind    v0.33.0 — https://kind.sigs.k8s.io/dl/v0.33.0/kind-darwin-amd64
#   kubectl v1.37.2 — https://dl.k8s.io/release/v1.37.2/bin/darwin/amd64/kubectl
#   Docker Desktop running, with at least 4 CPU / 4 GB allocated
#   (Docker Desktop → Settings → Resources).
#   Ollama running on the Mac host (llama3.1:8b) if you want Coach replies;
#   the stack boots without it, but /health reports ollama: down.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

CLUSTER="epitomejob"
NAMESPACE="epitomejob"

echo "== EpitomeJob W4 — kind deploy =="
echo "Repo root: ${REPO_ROOT}"

# --- Preconditions -----------------------------------------------------------
command -v docker >/dev/null 2>&1 || { echo "ERROR: docker not found — start Docker Desktop first." >&2; exit 1; }
command -v kind >/dev/null 2>&1 || { echo "ERROR: kind not found — install kind v0.33.0 (see INSTALL-W4.md)." >&2; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "ERROR: kubectl not found — install kubectl v1.37.2 (see INSTALL-W4.md)." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "ERROR: Docker daemon is not running — start Docker Desktop first." >&2; exit 1; }

for f in backend/Dockerfile agent-brain/Dockerfile web/Dockerfile gateway-service/Dockerfile eureka-server/Dockerfile mcp-service/Dockerfile k8s kind/cluster.yaml; do
  [ -f "${f}" ] || { echo "ERROR: missing ${f} — apply the W4 stack zip first (it adds the Dockerfiles); gateway-service, eureka-server and mcp-service come from the 5A/5B workers." >&2; exit 1; }
done

# --- 1. kind cluster (create only if missing) --------------------------------
if kind get clusters 2>/dev/null | grep -qx "${CLUSTER}"; then
  echo "kind cluster '${CLUSTER}' already exists — reusing it."
else
  echo "Creating kind cluster '${CLUSTER}'..."
  kind create cluster --config kind/cluster.yaml
fi
kubectl config use-context "kind-${CLUSTER}" >/dev/null

# --- 2. Build the three local images -----------------------------------------
echo "Building epitomejob/backend:w4 ..."
docker build -t epitomejob/backend:w4 ./backend
echo "Building epitomejob/agent-brain:w4 ..."
docker build -t epitomejob/agent-brain:w4 ./agent-brain
echo "Building epitomejob/web:w4 ..."
docker build -t epitomejob/web:w4 ./web
echo "Building epitomejob/gateway-service:5a ..."
docker build -f gateway-service/Dockerfile -t epitomejob/gateway-service:5a .
echo "Building epitomejob/eureka-server:5a ..."
docker build -f eureka-server/Dockerfile -t epitomejob/eureka-server:5a .
echo "Building epitomejob/mcp-service:5b ..."
docker build -t epitomejob/mcp-service:5b ./mcp-service

# --- 3. Load images into kind (temporal-worker still reuses agent-brain;
#     mcp-server is its own image since 5B)
echo "Loading images into kind..."
kind load docker-image epitomejob/backend:w4 --name "${CLUSTER}"
kind load docker-image epitomejob/agent-brain:w4 --name "${CLUSTER}"
kind load docker-image epitomejob/web:w4 --name "${CLUSTER}"
kind load docker-image epitomejob/gateway-service:5a --name "${CLUSTER}"
kind load docker-image epitomejob/eureka-server:5a --name "${CLUSTER}"
kind load docker-image epitomejob/mcp-service:5b --name "${CLUSTER}"

# --- 4. Apply manifests (namespace first: apply -f k8s/ has no order guarantee)
echo "Applying manifests..."
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/

# --- 5. Wait for rollouts, in dependency order -------------------------------
echo "Waiting for rollouts (this can take a few minutes on first run)..."
kubectl rollout status deployment/postgres -n "${NAMESPACE}" --timeout=300s
kubectl rollout status deployment/rustfs -n "${NAMESPACE}" --timeout=300s
kubectl rollout status deployment/temporal -n "${NAMESPACE}" --timeout=600s
kubectl rollout status deployment/temporal-ui -n "${NAMESPACE}" --timeout=300s
# Phase 5A platform, in dependency order (kafka/elasticsearch are slow
# starters — 600s; fluent-bit is a DaemonSet, so it has its own wait).
kubectl rollout status deployment/redis -n "${NAMESPACE}" --timeout=300s
kubectl rollout status deployment/kafka -n "${NAMESPACE}" --timeout=600s
kubectl rollout status deployment/elasticsearch -n "${NAMESPACE}" --timeout=600s
kubectl rollout status deployment/kibana -n "${NAMESPACE}" --timeout=300s
kubectl rollout status deployment/jaeger -n "${NAMESPACE}" --timeout=300s
kubectl rollout status deployment/eureka -n "${NAMESPACE}" --timeout=300s
kubectl rollout status deployment/gateway -n "${NAMESPACE}" --timeout=300s
kubectl rollout status daemonset/fluent-bit -n "${NAMESPACE}" --timeout=300s
kubectl rollout status deployment/backend -n "${NAMESPACE}" --timeout=600s
kubectl rollout status deployment/agent-brain -n "${NAMESPACE}" --timeout=300s
kubectl rollout status deployment/mcp-server -n "${NAMESPACE}" --timeout=300s
kubectl rollout status deployment/temporal-worker -n "${NAMESPACE}" --timeout=300s
kubectl rollout status deployment/web -n "${NAMESPACE}" --timeout=300s

echo ""
echo "== All pods ready =="
kubectl get pods -n "${NAMESPACE}"

# --- 6. Ollama reachability check (NOT a hard gate — contract §8 item 10) -----
echo ""
echo "Checking Ollama reachability from inside kind (host.docker.internal:11434)..."
if kubectl exec -n "${NAMESPACE}" deploy/agent-brain -- curl -s -m 5 http://host.docker.internal:11434/api/tags >/dev/null 2>&1; then
  echo "Ollama reachable from inside kind: YES"
else
  echo "Ollama reachable from inside kind: NO — start Ollama on the Mac host"
  echo "(ollama serve + ollama pull llama3.1:8b) and re-check with:"
  echo "  kubectl exec -n epitomejob deploy/agent-brain -- curl -s -m 5 http://host.docker.internal:11434/api/tags"
  echo "Coach answers need Ollama; everything else works without it."
fi

# --- 7. How to reach the stack ------------------------------------------------
echo ""
echo "== Reach the stack (run each in its own terminal) =="
echo "  kubectl port-forward -n epitomejob svc/web 3000:3000          # web: http://localhost:3000"
echo "  kubectl port-forward -n epitomejob svc/gateway 8080:8080      # gateway: http://localhost:8080/actuator/health (backend is internal-only now)"
echo "  kubectl port-forward -n epitomejob svc/backend 8081:8081      # backend (direct): http://localhost:8081/actuator/health"
echo "  kubectl port-forward -n epitomejob svc/eureka 8761:8761      # Eureka dashboard: http://localhost:8761"
echo "  kubectl port-forward -n epitomejob svc/kibana 5601:5601      # Kibana: http://localhost:5601 (index epitomejob-logs*)"
echo "  kubectl port-forward -n epitomejob svc/jaeger 16686:16686    # Jaeger UI: http://localhost:16686"
echo "  kubectl port-forward -n epitomejob svc/redis 6379:6379        # redis-cli -p 6379 ping"
echo "  kubectl port-forward -n epitomejob svc/kafka 9092:9092        # Kafka (in-network advertised as kafka:9092 — host tools will be redirected)"
echo "  kubectl port-forward -n epitomejob svc/elasticsearch 9200:9200  # ES: http://localhost:9200/_cluster/health"
echo "  kubectl port-forward -n epitomejob svc/temporal-ui 8233:8233  # Temporal UI: http://localhost:8233"
echo "  kubectl port-forward -n epitomejob svc/agent-brain 8000:8000  # agent-brain: http://localhost:8000/health"
echo "  kubectl port-forward -n epitomejob svc/mcp-server 8001:8001   # MCP Inspector: http://localhost:8001/sse"
echo "  kubectl port-forward -n epitomejob svc/rustfs 9000:9000 9001:9001  # S3 API + console"
echo "  kubectl port-forward -n epitomejob svc/postgres 5432:5432      # psql (db epitomejob)"
echo ""
echo "Tear down:  kind delete cluster --name epitomejob"
echo "  (PVC data is deleted with the cluster — kind is for dev, not for keeps.)"
