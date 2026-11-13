#!/usr/bin/env bash
# EpitomeJob W4 — deploy the stack to a local kind cluster.
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

for f in backend/Dockerfile agent-brain/Dockerfile web/Dockerfile k8s kind/cluster.yaml; do
  [ -f "${f}" ] || { echo "ERROR: missing ${f} — apply the W4 stack zip first (it adds the Dockerfiles)." >&2; exit 1; }
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

# --- 3. Load images into kind (mcp-server + temporal-worker reuse agent-brain)
echo "Loading images into kind..."
kind load docker-image epitomejob/backend:w4 --name "${CLUSTER}"
kind load docker-image epitomejob/agent-brain:w4 --name "${CLUSTER}"
kind load docker-image epitomejob/web:w4 --name "${CLUSTER}"

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
echo "  kubectl port-forward -n epitomejob svc/backend 8080:8080      # backend: http://localhost:8080/actuator/health"
echo "  kubectl port-forward -n epitomejob svc/temporal-ui 8233:8233  # Temporal UI: http://localhost:8233"
echo "  kubectl port-forward -n epitomejob svc/agent-brain 8000:8000  # agent-brain: http://localhost:8000/health"
echo "  kubectl port-forward -n epitomejob svc/mcp-server 8001:8001   # MCP Inspector: http://localhost:8001/sse"
echo "  kubectl port-forward -n epitomejob svc/rustfs 9000:9000 9001:9001  # S3 API + console"
echo "  kubectl port-forward -n epitomejob svc/postgres 5432:5432      # psql (db epitomejob)"
echo ""
echo "Tear down:  kind delete cluster --name epitomejob"
echo "  (PVC data is deleted with the cluster — kind is for dev, not for keeps.)"
