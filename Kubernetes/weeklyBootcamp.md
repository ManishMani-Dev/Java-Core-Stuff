# Kubernetes Hands-On Bootcamp — The 9-Week Roadmap, In Full Detail

This guide restructures the complete Kubernetes curriculum as a **do-it-yourself bootcamp** following the learning roadmap. Every week has: concepts → hands-on lab with full YAML → "break it on purpose" exercises → checkpoint questions. Type every command yourself; muscle memory is the goal.

```mermaid
flowchart TD
    A[Week 1-2: kubectl, Pods, Deployments] --> B[Week 3: Services, Endpoints, Ingress, DNS]
    B --> C[Week 4: Config, Secrets, Probes, Rollouts]
    C --> D[Week 5: Storage & StatefulSets]
    D --> E[Week 6: HPA + Load Testing]
    E --> F[Week 7: KEDA Event-Driven Scaling]
    F --> G[Week 8: Monitoring & Alerts]
    G --> H[Week 9+: Helm, GitOps, RBAC, Hardening]
    H --> I[Capstone: Full Order System]
```

---

# Week 1–2: kubectl, Pods & Deployments

**Goal:** Stand up a cluster, internalize the declarative model, and get fluent with pod lifecycle and Deployments.

## Day 1: Environment Setup

```bash
# Install kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install kubectl /usr/local/bin/

# Install minikube and start a cluster
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
minikube start --cpus=4 --memory=6g

# Sanity checks
kubectl cluster-info
kubectl get nodes -o wide
kubectl get pods -n kube-system     # meet the system pods: coredns, kube-proxy, etcd...
```

**Quality of life — do this now, thank yourself later:**

```bash
echo 'alias k=kubectl' >> ~/.bashrc
echo 'source <(kubectl completion bash)' >> ~/.bashrc
echo 'complete -o default -F __start_kubectl k' >> ~/.bashrc
source ~/.bashrc
```

## Day 2: Understand What You Just Built

```mermaid
flowchart TB
    subgraph CP[Control Plane - in minikube, one VM runs everything]
        API[kube-apiserver]
        ETCD[(etcd)]
        SCH[scheduler]
        CM[controller-manager]
    end
    subgraph N[Node]
        KL[kubelet] --> CRT[containerd]
        KP[kube-proxy]
    end
    YOU[kubectl] --> API
    API <--> KL
```

Explore each component yourself:

```bash
kubectl get pods -n kube-system -o wide
kubectl describe node minikube | less     # capacity, allocatable, conditions
kubectl explain pod.spec | less           # built-in API docs — use this constantly
kubectl api-resources | less              # every object type the cluster knows
```

**Checkpoint question:** When you run `kubectl apply`, which component does kubectl talk to? (Answer: only ever the API server. Scheduler, controllers, and kubelets all watch the API server too — nothing talks to each other directly.)

## Day 3–4: Pods

**Lab 1 — your first pod, imperative then declarative:**

```bash
# Imperative (fine for experiments)
kubectl run nginx --image=nginx:1.27
kubectl get pods -w                    # watch it go Pending → ContainerCreating → Running
kubectl describe pod nginx             # READ THE EVENTS SECTION — bottom of output
kubectl logs nginx
kubectl exec -it nginx -- sh
  # inside: hostname; ip addr; exit
kubectl delete pod nginx
```

Now declarative — the way you'll work forever after:

```yaml
# pod.yaml
apiVersion: v1
kind: Pod
metadata:
  name: web
  labels:
    app: web
    tier: frontend
spec:
  containers:
    - name: nginx
      image: nginx:1.27
      ports:
        - containerPort: 80
          name: http
      resources:
        requests: { cpu: 100m, memory: 128Mi }
        limits:   { cpu: 500m, memory: 256Mi }
```

```bash
kubectl apply -f pod.yaml
kubectl get pod web -o yaml | less     # see everything k8s added: status, IP, node, QoS
kubectl get pod web -o jsonpath='{.status.podIP}'
kubectl port-forward pod/web 8080:80   # http://localhost:8080 in your browser
```

**Pod lifecycle — know this cold:**

```mermaid
stateDiagram-v2
    [*] --> Pending: accepted, not yet scheduled/pulled
    Pending --> Running: containers up
    Running --> Succeeded: exit 0 (Jobs)
    Running --> Failed: exit non-zero, restartPolicy Never
    Running --> Running: crash → restart (backoff 10s, 20s, 40s... = CrashLoopBackOff)
    Pending --> Failed: ImagePullBackOff / unschedulable
```

**Break it on purpose (the most important habit in this guide):**

```bash
# 1. Bad image tag
kubectl run broken --image=nginx:99.99
kubectl get pod broken                  # ImagePullBackOff
kubectl describe pod broken             # events tell you exactly why
kubectl delete pod broken

# 2. Crashing container
kubectl run crasher --image=busybox -- sh -c "echo dying; exit 1"
kubectl get pod crasher -w              # watch CrashLoopBackOff develop, restarts count up
kubectl logs crasher --previous         # logs of the LAST crashed attempt — memorize this flag
kubectl delete pod crasher

# 3. Impossible resource request
kubectl run greedy --image=nginx --overrides='{"spec":{"containers":[{"name":"greedy","image":"nginx","resources":{"requests":{"cpu":"100"}}}]}}'
kubectl describe pod greedy             # Pending: "0/1 nodes available: Insufficient cpu"
kubectl delete pod greedy
```

**Multi-container pod — shared network and volumes:**

```yaml
# sidecar.yaml — app writes logs, sidecar reads them
apiVersion: v1
kind: Pod
metadata:
  name: two-containers
spec:
  volumes:
    - name: shared-logs
      emptyDir: {}
  initContainers:
    - name: init-banner
      image: busybox
      command: ['sh', '-c', 'echo "initialized at $(date)" > /logs/app.log']
      volumeMounts: [{ name: shared-logs, mountPath: /logs }]
  containers:
    - name: writer
      image: busybox
      command: ['sh', '-c', 'while true; do echo "$(date) tick" >> /logs/app.log; sleep 5; done']
      volumeMounts: [{ name: shared-logs, mountPath: /logs }]
    - name: reader
      image: busybox
      command: ['sh', '-c', 'tail -f /logs/app.log']
      volumeMounts: [{ name: shared-logs, mountPath: /logs }]
```

```bash
kubectl apply -f sidecar.yaml
kubectl logs two-containers -c reader -f    # -c selects which container
```

Notice: the init container ran first and finished; both main containers share the volume and the same IP.

## Day 5–7: Deployments

You never run bare pods in production — if the node dies, the pod is gone forever. A **Deployment** keeps the desired number alive and gives you rolling updates.

```mermaid
flowchart TB
    D[Deployment 'web'<br/>desired: image v1, replicas 3] --> RS[ReplicaSet web-7d4b9<br/>maintains exactly 3 pods]
    RS --> P1[pod web-7d4b9-x1] & P2[pod web-7d4b9-x2] & P3[pod web-7d4b9-x3]
```

**Lab 2 — Deployment + self-healing:**

```yaml
# deploy.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web
spec:
  replicas: 3
  selector:
    matchLabels: { app: web }
  template:
    metadata:
      labels: { app: web }
    spec:
      containers:
        - name: web
          image: nginx:1.27
          ports: [{ containerPort: 80 }]
          resources:
            requests: { cpu: 100m, memory: 128Mi }
            limits:   { cpu: 300m, memory: 256Mi }
```

```bash
kubectl apply -f deploy.yaml
kubectl get deploy,rs,pods               # see the 3-layer hierarchy with your own eyes

# THE self-healing demo — run these in two terminals
kubectl get pods -w                      # terminal 1: watch
kubectl delete pod <one-web-pod>         # terminal 2: kill one
# Terminal 1 shows: Terminating... and a NEW pod appearing within seconds.
# You didn't restart anything. The ReplicaSet controller reconciled desired vs actual.
```

**Lab 3 — rolling update & rollback:**

```bash
# Watch in one terminal:
kubectl get pods -w

# Update the image (or edit deploy.yaml and apply):
kubectl set image deployment/web web=nginx:1.27.3
kubectl rollout status deployment/web

# History and rollback:
kubectl rollout history deployment/web
kubectl set image deployment/web web=nginx:broken-tag    # deliberately break it
kubectl get pods                          # new pods ImagePullBackOff, OLD PODS STILL SERVING
kubectl rollout undo deployment/web       # instant recovery
```

That experiment teaches the deepest Deployment lesson: **a failed rollout doesn't take down the old version** — the old ReplicaSet keeps serving until new pods are Ready.

**Scaling:**

```bash
kubectl scale deployment/web --replicas=6
kubectl get rs                            # same ReplicaSet, more pods
```

## Week 1–2 Checkpoint

You should be able to answer without looking:
1. What's the difference between `requests` and `limits`, and what happens when each is exceeded?
2. Why does `kubectl logs --previous` exist?
3. A Deployment creates a ReplicaSet which creates Pods — why the middle layer? (Hint: each *revision* is a separate ReplicaSet; rollback = scale old RS up, new RS down.)
4. What keeps pod count at 3 when you delete one — and where does that logic run? (controller-manager's reconciliation loop)

---

# Week 3: Services, Endpoints, DNS & Ingress — Deploy a 2-Tier App

**Goal:** Build `web → api → postgres`, understand exactly how traffic finds pods, and expose it externally.

## The Problem Services Solve

Run `kubectl get pods -o wide` after a few deletions: pod IPs change constantly. Hardcoding them is impossible. A **Service** is a stable virtual IP + DNS name that load-balances over whichever pods currently match its **label selector**.

```mermaid
flowchart LR
    C[client pod] -->|"http://api.demo"| S[Service: api<br/>ClusterIP 10.96.x.x:80]
    S --> ES[EndpointSlice — live list of READY pod IPs]
    ES --> P1[pod 10.244.1.5:8080]
    ES --> P2[pod 10.244.2.7:8080]
    ES --> P3[pod 10.244.3.2:8080]
```

## Day 1–2: Build the 2-Tier App

```bash
kubectl create namespace demo
kubectl config set-context --current --namespace=demo
```

```yaml
# api.yaml — a tiny echo API
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api
spec:
  replicas: 3
  selector:
    matchLabels: { app: api }
  template:
    metadata:
      labels: { app: api }
    spec:
      containers:
        - name: api
          image: hashicorp/http-echo:1.0
          args: ["-listen=:8080", "-text=hello from the api"]
          ports: [{ containerPort: 8080, name: http }]
          resources:
            requests: { cpu: 50m, memory: 64Mi }
            limits:   { cpu: 200m, memory: 128Mi }
---
apiVersion: v1
kind: Service
metadata:
  name: api
spec:
  type: ClusterIP
  selector:
    app: api                  # ← the glue. Matches pod labels above.
  ports:
    - port: 80                # service port
      targetPort: http        # container port (by name)
```

```bash
kubectl apply -f api.yaml
kubectl get svc api                                  # note the CLUSTER-IP
kubectl get endpointslices -l kubernetes.io/service-name=api -o yaml | less
# ↑ THIS is the service actually "working": three pod IPs listed as ready:true
```

**Prove the load balancing:**

```bash
kubectl run tmp --rm -it --image=busybox -- sh
/ # wget -qO- http://api          # same-namespace short name
/ # wget -qO- http://api.demo     # <service>.<namespace>
/ # wget -qO- http://api.demo.svc.cluster.local   # full form
/ # nslookup api                  # CoreDNS resolves it to the ClusterIP
```

## Day 3: Endpoints Deep Dive — The Debugging Goldmine

**Experiment 1 — break the selector:**

```bash
kubectl patch svc api -p '{"spec":{"selector":{"app":"apii"}}}'   # typo on purpose
kubectl get endpointslices -l kubernetes.io/service-name=api      # endpoints GONE
kubectl run tmp --rm -it --image=busybox -- wget -qO- --timeout=3 http://api
# → times out. Service exists, DNS resolves, but routes to nothing.
kubectl patch svc api -p '{"spec":{"selector":{"app":"api"}}}'    # fix it
```

**Experiment 2 — readiness controls endpoints (preview of Week 4):**
When a pod's readiness probe fails, its IP is flipped to `ready: false` in the EndpointSlice and kube-proxy stops sending it traffic — *without restarting it*. This is the bridge between health checks and networking. You'll see it live next week.

**Experiment 3 — Service to an external system (manual endpoints):**

```yaml
# external-svc.yaml — give an outside DB a cluster-internal DNS name
apiVersion: v1
kind: Service
metadata:
  name: legacy-db
spec:
  ports: [{ port: 5432 }]          # NO selector → k8s won't manage endpoints
---
apiVersion: discovery.k8s.io/v1
kind: EndpointSlice
metadata:
  name: legacy-db-1
  labels:
    kubernetes.io/service-name: legacy-db
addressType: IPv4
ports: [{ port: 5432, name: "" }]
endpoints:
  - addresses: ["192.168.10.50"]   # any external IP
```

Now pods can use `legacy-db:5432` and you can later migrate the DB into the cluster without changing a single client.

**The flow of one request (what kube-proxy actually does):**

```mermaid
sequenceDiagram
    participant P as client pod
    participant DNS as CoreDNS
    participant IPT as iptables rules (written by kube-proxy)
    participant T as target pod
    P->>DNS: resolve api.demo
    DNS-->>P: 10.96.4.12 (ClusterIP — virtual, owned by no one)
    P->>IPT: packet to 10.96.4.12:80
    IPT->>IPT: DNAT: rewrite dest to a random ready endpoint
    IPT->>T: packet arrives at 10.244.2.7:8080
```

## Day 4: NodePort & LoadBalancer

```bash
# Expose api on every node's IP at a high port
kubectl patch svc api -p '{"spec":{"type":"NodePort"}}'
kubectl get svc api          # e.g. 80:31742/TCP
minikube service api --url   # minikube helper to open it

# LoadBalancer locally (minikube emulates a cloud LB):
minikube tunnel              # run in separate terminal, keep it open
kubectl patch svc api -p '{"spec":{"type":"LoadBalancer"}}'
kubectl get svc api          # EXTERNAL-IP appears
```

Layering to remember: **LoadBalancer ⊃ NodePort ⊃ ClusterIP** — each type includes the ones below it.

## Day 5–6: Ingress — One Door, Many Services

```bash
minikube addons enable ingress          # installs nginx ingress controller
kubectl get pods -n ingress-nginx
```

Add a second service so routing is meaningful:

```yaml
# web.yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: web }
spec:
  replicas: 2
  selector: { matchLabels: { app: web } }
  template:
    metadata: { labels: { app: web } }
    spec:
      containers:
        - name: web
          image: hashicorp/http-echo:1.0
          args: ["-listen=:8080", "-text=hello from the WEB frontend"]
          ports: [{ containerPort: 8080, name: http }]
---
apiVersion: v1
kind: Service
metadata: { name: web }
spec:
  selector: { app: web }
  ports: [{ port: 80, targetPort: http }]
---
# ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: demo
spec:
  ingressClassName: nginx
  rules:
    - host: shop.local
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend: { service: { name: api, port: { number: 80 } } }
          - path: /
            pathType: Prefix
            backend: { service: { name: web, port: { number: 80 } } }
```

```bash
kubectl apply -f web.yaml -f ingress.yaml
echo "$(minikube ip) shop.local" | sudo tee -a /etc/hosts
curl http://shop.local/        # → hello from the WEB frontend
curl http://shop.local/api     # → hello from the api
```

```mermaid
flowchart LR
    U[curl shop.local] --> IC[nginx ingress controller pod]
    IC -- "path /" --> WS[Service web] --> WP[web pods]
    IC -- "path /api" --> AS[Service api] --> AP[api pods]
```

## Day 7: Lock It Down with a NetworkPolicy

By default any pod can reach any pod. Practice default-deny:

```yaml
# netpol.yaml — api only accepts traffic from web pods
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: api-from-web-only
spec:
  podSelector: { matchLabels: { app: api } }
  policyTypes: [Ingress]
  ingress:
    - from:
        - podSelector: { matchLabels: { app: web } }
      ports: [{ port: 8080 }]
```

(Enforcement needs a CNI like Calico/Cilium: `minikube start --cni=calico`. Test that a busybox pod can no longer reach `api`, but web pods can.)

## Week 3 Checkpoint

1. A Service has a valid ClusterIP and DNS resolves, but requests hang. What are the two most likely causes, and which one command checks both? (`kubectl get endpoints` — empty: selector mismatch or pods not Ready)
2. Why does Ingress need a "controller" while Services don't?
3. What does kube-proxy actually do with a ClusterIP? (Programs DNAT rules; the IP is virtual.)

---

# Week 4: ConfigMaps, Secrets, Probes & Zero-Downtime Rollouts

**Goal:** Externalize configuration, master the three probes, and achieve genuinely zero-downtime deployments — verified under load.

## Day 1–2: ConfigMaps & Secrets

```yaml
# config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: api-config
data:
  LOG_LEVEL: "info"
  FEATURE_RECS: "true"
  app.properties: |            # whole files work too
    cache.ttl=300
    pool.size=20
---
apiVersion: v1
kind: Secret
metadata:
  name: api-secrets
type: Opaque
stringData:                    # plain text here; stored base64 in etcd
  DB_PASSWORD: "s3cr3t-pw"
  API_KEY: "abc123"
```

Consume both ways and understand the difference:

```yaml
# in the Deployment pod template:
spec:
  containers:
    - name: api
      envFrom:
        - configMapRef: { name: api-config }     # every key → env var
        - secretRef:    { name: api-secrets }
      volumeMounts:
        - name: props
          mountPath: /etc/app                    # app.properties appears as a file
  volumes:
    - name: props
      configMap:
        name: api-config
        items: [{ key: app.properties, path: app.properties }]
```

**Critical behavior difference — test it:**

```bash
kubectl exec deploy/api -- env | grep LOG_LEVEL          # info
kubectl patch configmap api-config -p '{"data":{"LOG_LEVEL":"debug"}}'
kubectl exec deploy/api -- env | grep LOG_LEVEL          # STILL info!
# Env vars are frozen at container start. Mounted files update in ~a minute,
# but env vars need:
kubectl rollout restart deploy/api
```

**Secrets reality check:** base64 is encoding, not encryption — anyone with etcd or `get secret` access reads them. Production answer: RBAC-restrict secrets, enable etcd encryption-at-rest, and prefer **External Secrets Operator** syncing from Vault / AWS Secrets Manager.

## Day 3–4: The Three Probes

```mermaid
flowchart TB
    ST[startupProbe<br/>has the app finished booting?] -->|passes once| BOTH[liveness + readiness take over]
    BOTH --> RD[readinessProbe<br/>can I serve traffic NOW?]
    BOTH --> LV[livenessProbe<br/>am I wedged beyond repair?]
    RD -- fail --> OUT[pulled from Service endpoints<br/>NO restart — traffic shed]
    RD -- pass --> IN[receives traffic]
    LV -- fail × threshold --> RST[container restarted]
```

| Probe | Failure consequence | Should check dependencies? |
|---|---|---|
| startup | restart | no |
| readiness | removed from endpoints | yes, critical ones |
| liveness | restart | **NEVER** |

**Why liveness must never check the DB:** if the database blips for 60s and every pod's liveness probe checks the DB, Kubernetes restarts *your entire fleet simultaneously* — cold caches, connection storms, full outage. Readiness failing is the right response: pods shed traffic, DB recovers, traffic resumes. Liveness should only detect "this process is wedged and a restart fixes it" (deadlock, stuck event loop).

**Lab — watch readiness control endpoints in real time.** Use a probe-controllable demo app:

```yaml
# probes-demo.yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: probed }
spec:
  replicas: 3
  selector: { matchLabels: { app: probed } }
  template:
    metadata: { labels: { app: probed } }
    spec:
      containers:
        - name: app
          image: nginx:1.27
          ports: [{ containerPort: 80, name: http }]
          readinessProbe:
            httpGet: { path: /, port: http }
            periodSeconds: 3
            failureThreshold: 2
          livenessProbe:
            httpGet: { path: /, port: http }
            periodSeconds: 10
            failureThreshold: 3
---
apiVersion: v1
kind: Service
metadata: { name: probed }
spec:
  selector: { app: probed }
  ports: [{ port: 80, targetPort: http }]
```

```bash
kubectl apply -f probes-demo.yaml
kubectl get endpoints probed          # 3 IPs

# Break readiness in ONE pod by deleting nginx's index page:
POD=$(kubectl get pod -l app=probed -o name | head -1)
kubectl exec $POD -- rm /usr/share/nginx/html/index.html

# ~6 seconds later:
kubectl get endpoints probed          # only 2 IPs — pod still Running, restarts=0
kubectl get pods                      # READY column shows 0/1 for that pod
# Fix it:
kubectl exec $POD -- sh -c 'echo ok > /usr/share/nginx/html/index.html'
kubectl get endpoints probed          # back to 3
```

You just watched the readiness ↔ endpoints link with your own eyes. This mechanism is also exactly how rolling updates avoid sending traffic to pods that aren't ready yet.

**Tuning template for a typical app:**

```yaml
startupProbe:                       # tolerate up to 150s boot, then probes take over
  httpGet: { path: /healthz/live, port: http }
  failureThreshold: 30
  periodSeconds: 5
readinessProbe:
  httpGet: { path: /healthz/ready, port: http }
  periodSeconds: 5
  failureThreshold: 3
  timeoutSeconds: 2
livenessProbe:
  httpGet: { path: /healthz/live, port: http }
  periodSeconds: 10
  failureThreshold: 3
```

## Day 5–6: Zero-Downtime Rollouts — Proven Under Load

The full recipe = surge settings + readiness + graceful shutdown:

```yaml
spec:
  strategy:
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0            # never dip below desired ready count
  template:
    spec:
      terminationGracePeriodSeconds: 30
      containers:
        - name: api
          lifecycle:
            preStop:
              exec:
                command: ["sh", "-c", "sleep 5"]   # let endpoint removal propagate
```

```mermaid
sequenceDiagram
    participant K as Kubernetes
    participant New as new pod v2
    participant Old as old pod v1
    participant EP as Endpoints
    K->>New: create (surge)
    New->>New: startup + readiness pass
    EP->>EP: add v2 pod
    K->>Old: SIGTERM + remove from endpoints (parallel, async!)
    Note over Old: preStop sleep 5 bridges the race:<br/>traffic stops arriving BEFORE process exits
    Old->>Old: drain in-flight requests, exit 0
    Note over K: repeat per pod. Failed readiness on v2?<br/>Rollout stalls, v1 keeps serving.
```

**Prove it with continuous traffic:**

```bash
# Terminal 1 — hammer the service, count failures:
kubectl run loader --rm -it --image=busybox -- sh -c \
  'while true; do wget -qO- --timeout=1 http://probed >/dev/null 2>&1 || echo "FAIL $(date +%T)"; sleep 0.2; done'

# Terminal 2 — trigger a rollout:
kubectl set image deploy/probed app=nginx:1.27.3
kubectl rollout status deploy/probed
# Terminal 1 should print ZERO fails. Then remove the preStop hook + set
# maxUnavailable: 1, redo it, and watch FAILs appear. Lesson learned forever.
```

## Day 7: Pod Disruption Budget

Voluntary disruptions (node drain, cluster upgrade) respect PDBs:

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata: { name: probed-pdb }
spec:
  minAvailable: 2
  selector: { matchLabels: { app: probed } }
```

```bash
kubectl apply -f pdb.yaml
kubectl drain minikube --ignore-daemonsets --delete-emptydir-data --dry-run=client
# On multi-node clusters, drain evicts pods one at a time, waiting for
# replacements to go Ready elsewhere, never violating minAvailable.
kubectl uncordon minikube
```

## Week 4 Checkpoint

1. ConfigMap changed, pods still show old env value — why, and what fixes it?
2. A pod is Running with 0 restarts but receives no traffic. Which probe is involved and how do you confirm? (readiness; `kubectl get endpoints`)
3. Why is `preStop: sleep 5` not a hack but a necessity? (endpoint removal and SIGTERM race each other)
4. Liveness probe checking the database: explain the failure cascade.

---

# Week 5: Storage & StatefulSets — Run Postgres Properly

**Goal:** Understand the PV/PVC/StorageClass machinery and run a database whose data survives anything short of disk deletion.

## Day 1–2: The Storage Model

```mermaid
flowchart LR
    POD[Pod] -->|mounts| PVC[PVC: 'I need 5Gi RWO'<br/>the REQUEST]
    PVC -->|bound 1:1| PV[PV: the actual disk<br/>the SUPPLY]
    SC[StorageClass 'standard'<br/>the RECIPE: which provisioner,<br/>disk type, reclaim policy] -.dynamic provisioning.-> PV
```

- App teams write **PVCs**; they never think about disks.
- **StorageClass** + CSI driver create PVs on demand (`kubectl get sc` — minikube ships `standard`).
- **accessModes:** `ReadWriteOnce` (one node — block storage like EBS), `ReadWriteMany` (many nodes — NFS/EFS), `ReadOnlyMany`.
- **reclaimPolicy:** `Delete` (PV + disk destroyed with PVC) vs `Retain` (disk survives for manual recovery) — check this before storing anything important.

**Lab — watch dynamic provisioning happen:**

```bash
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: PersistentVolumeClaim
metadata: { name: scratch }
spec:
  accessModes: [ReadWriteOnce]
  resources: { requests: { storage: 1Gi } }
EOF
kubectl get pvc scratch          # Bound (or Pending until first consumer)
kubectl get pv                   # a PV appeared out of nowhere — that's the provisioner
```

**Prove data survives pod death:**

```bash
kubectl run keeper --image=busybox --overrides='
{"spec":{"containers":[{"name":"keeper","image":"busybox","command":["sleep","9999"],
"volumeMounts":[{"name":"d","mountPath":"/data"}]}],
"volumes":[{"name":"d","persistentVolumeClaim":{"claimName":"scratch"}}]}}'
kubectl exec keeper -- sh -c 'echo precious > /data/file.txt'
kubectl delete pod keeper
# recreate the same pod, then:
kubectl exec keeper -- cat /data/file.txt     # → precious
```

## Day 3–5: StatefulSet — Postgres with Identity

Why not a Deployment for a DB? Deployment pods are interchangeable cattle with random names and (if naively shared) one PVC. Databases need **stable identity** (`db-0`), **per-replica storage**, and **ordered startup**. That's a StatefulSet.

| | Deployment | StatefulSet |
|---|---|---|
| Pod names | random hash | ordinal: `pg-0`, `pg-1` |
| Storage | shared/none | `volumeClaimTemplates` → one PVC per pod, reattached on reschedule |
| Start/stop | parallel | ordered (0, then 1, ...) |
| DNS | via Service only | per-pod: `pg-0.pg-hl.demo.svc` (via headless Service) |

```yaml
# postgres.yaml
apiVersion: v1
kind: Secret
metadata: { name: pg-secret }
type: Opaque
stringData:
  POSTGRES_PASSWORD: "devpassword"
---
apiVersion: v1
kind: Service                      # headless: clusterIP None → DNS returns POD IPs
metadata: { name: pg-hl }
spec:
  clusterIP: None
  selector: { app: pg }
  ports: [{ port: 5432 }]
---
apiVersion: apps/v1
kind: StatefulSet
metadata: { name: pg }
spec:
  serviceName: pg-hl
  replicas: 1
  selector: { matchLabels: { app: pg } }
  template:
    metadata: { labels: { app: pg } }
    spec:
      containers:
        - name: postgres
          image: postgres:16
          ports: [{ containerPort: 5432, name: pg }]
          envFrom: [{ secretRef: { name: pg-secret } }]
          env:
            - name: PGDATA
              value: /var/lib/postgresql/data/pgdata
          volumeMounts:
            - { name: data, mountPath: /var/lib/postgresql/data }
          readinessProbe:
            exec: { command: ["pg_isready", "-U", "postgres"] }
            periodSeconds: 5
          livenessProbe:
            exec: { command: ["pg_isready", "-U", "postgres"] }
            initialDelaySeconds: 30
            periodSeconds: 10
          resources:
            requests: { cpu: 250m, memory: 256Mi }
            limits:   { cpu: "1",  memory: 512Mi }
  volumeClaimTemplates:            # ← the StatefulSet superpower
    - metadata: { name: data }
      spec:
        accessModes: [ReadWriteOnce]
        resources: { requests: { storage: 5Gi } }
```

```bash
kubectl apply -f postgres.yaml
kubectl get pods -w                          # pg-0 (always ordinal names)
kubectl get pvc                              # data-pg-0 created from the template

# Write data:
kubectl exec -it pg-0 -- psql -U postgres -c \
  "CREATE TABLE orders(id serial, item text); INSERT INTO orders(item) VALUES ('book'),('pen');"

# The ultimate test — destroy the pod:
kubectl delete pod pg-0
kubectl get pods -w                          # pg-0 comes back (same name!)
kubectl exec -it pg-0 -- psql -U postgres -c "SELECT * FROM orders;"
# → book, pen. The PVC data-pg-0 was reattached to the new pod.
```

```mermaid
flowchart TB
    SS[StatefulSet pg] --> P0[pod pg-0]
    SS --> PVT[volumeClaimTemplates]
    PVT --> PVC0[PVC data-pg-0] --> PV0[(PV / disk)]
    P0 --- PVC0
    HL[Headless Service pg-hl] -. "DNS: pg-0.pg-hl.demo.svc" .-> P0
    note["delete pg-0 → new pg-0 reattaches data-pg-0.<br/>Scale to 3 → pg-1, pg-2 each get their OWN PVC."]
```

**Connect the Week 3 api to it:** add `DATABASE_URL: postgres://postgres:devpassword@pg-hl.demo:5432/postgres` to the api's Secret. Your 2-tier app is now 3-tier with real persistence.

**Production notes:** a single StatefulSet replica ≠ HA — for real Postgres HA use an operator (**CloudNativePG**, Zalando) that manages replication and failover; take scheduled backups (operator-native or Velero + volume snapshots); set reclaimPolicy `Retain` for DB StorageClasses.

## Week 5 Checkpoint

1. PVC stuck `Pending` — name three causes. (no StorageClass / no default SC, no capacity, RWX requested on RWO-only storage)
2. Why does a StatefulSet need a headless Service?
3. You scale `pg` 1→3→1. What happens to `data-pg-1` and `data-pg-2`? (PVCs are retained, not deleted — by design, to protect data)

---

# Week 6: HPA + Load Testing — Watch Your App Scale Itself

**Goal:** Install metrics-server, configure an HPA with sane scaling behavior, generate load with k6, and watch the whole feedback loop operate.

## Day 1: Metrics Pipeline

```bash
minikube addons enable metrics-server        # (plain clusters: apply the components.yaml)
kubectl top nodes
kubectl top pods -n demo                     # if this works, HPA can work
```

```mermaid
flowchart LR
    KL[kubelet cAdvisor<br/>per-container cpu/mem] --> MS[metrics-server<br/>aggregates, in-memory]
    MS --> MAPI[metrics.k8s.io API]
    MAPI --> TOP[kubectl top]
    MAPI --> HPA[HPA controller<br/>checks every 15s]
    HPA -->|scale| D[Deployment]
```

## Day 2–3: A CPU-Burning Target + HPA

```yaml
# scaler-demo.yaml — php-apache does real CPU work per request
apiVersion: apps/v1
kind: Deployment
metadata: { name: scaler-demo }
spec:
  replicas: 2
  selector: { matchLabels: { app: scaler-demo } }
  template:
    metadata: { labels: { app: scaler-demo } }
    spec:
      containers:
        - name: app
          image: registry.k8s.io/hpa-example
          ports: [{ containerPort: 80 }]
          resources:
            requests: { cpu: 200m, memory: 64Mi }   # ← HPA % is computed vs THIS
            limits:   { cpu: 500m, memory: 128Mi }
---
apiVersion: v1
kind: Service
metadata: { name: scaler-demo }
spec:
  selector: { app: scaler-demo }
  ports: [{ port: 80 }]
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata: { name: scaler-demo }
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: scaler-demo
  minReplicas: 2
  maxReplicas: 12
  metrics:
    - type: Resource
      resource:
        name: cpu
        target: { type: Utilization, averageUtilization: 60 }
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 0
      policies: [{ type: Percent, value: 100, periodSeconds: 30 }]   # ≤ double per 30s
    scaleDown:
      stabilizationWindowSeconds: 300                                # 5 min cooldown
      policies: [{ type: Pods, value: 2, periodSeconds: 60 }]
```

**The math you must know:** `desired = ceil(current × currentUtil / targetUtil)`.
2 pods at 150% of their 200m request (i.e., 300m each), target 60% → ceil(2 × 150/60) = **5 pods**.

Why the asymmetric behavior block: scale **up fast** (users are waiting), scale **down slowly** (avoid flapping when load oscillates). The 300s down-stabilization means "only scale down to the max desired count seen over the last 5 minutes."

## Day 4–5: Load Test with k6 and Observe

```javascript
// load.js — ramp to 60 virtual users, hold, ramp down
import http from 'k6/http';
export const options = {
  stages: [
    { duration: '2m', target: 60 },
    { duration: '5m', target: 60 },
    { duration: '2m', target: 0 },
  ],
  thresholds: { http_req_duration: ['p(95)<500'] },   // SLO: 95% under 500ms
};
export default function () {
  http.get('http://scaler-demo.demo.svc.cluster.local');
}
```

Run it inside the cluster and watch from three terminals:

```bash
# Terminal 1
kubectl get hpa scaler-demo -w
# NAME          TARGETS         MINPODS  MAXPODS  REPLICAS
# scaler-demo   cpu: 23%/60%    2        12       2
# scaler-demo   cpu: 178%/60%   2        12       2      ← load hits
# scaler-demo   cpu: 178%/60%   2        12       6      ← ceil(2*178/60)=6
# scaler-demo   cpu: 71%/60%    2        12       8
# scaler-demo   cpu: 52%/60%    2        12       8      ← stable
# ... 5 min after load ends ...
# scaler-demo   cpu: 4%/60%     2        12       2      ← slow ride down

# Terminal 2
kubectl get pods -l app=scaler-demo -w

# Terminal 3 — run k6 as a one-off pod
kubectl run k6 --rm -it --image=grafana/k6 --restart=Never -- run - < load.js
```

```mermaid
sequenceDiagram
    participant K6 as k6 load
    participant P as pods
    participant MS as metrics-server
    participant H as HPA controller
    K6->>P: 60 VUs of traffic
    P->>MS: cpu usage spikes to ~350m vs 200m request
    H->>MS: poll (every 15s): 178% vs target 60%
    H->>P: scale 2 → 6 → 8
    Note over P: per-pod load drops as replicas absorb traffic
    K6->>P: ramp down
    Note over H: 5-min stabilization window...
    H->>P: scale 8 → 2
```

**Experiments to cement understanding:**
1. Set `maxReplicas: 4` and rerun — watch the HPA pin at max while CPU stays >60%. *This is exactly what the "HPA at max" production alert catches: you've hit your capacity ceiling.*
2. Delete the `resources.requests` and watch the HPA report `<unknown>` targets — no request, no percentage.
3. Check `kubectl describe hpa scaler-demo` — read the Events and Conditions sections; this is how you debug scaling decisions.

**HPA's blind spots (your motivation for Week 7):** can't scale below 1 (min is 1, costly for idle workers), only sees CPU/memory natively, and CPU is a *lagging, indirect* signal — by the time CPU is high, users already waited. For queue-driven work, the queue length *is* the demand. Enter KEDA.

## Week 6 Checkpoint

1. 3 pods at 90% CPU, target 45%. How many pods does HPA want? (ceil(3×90/45)=6)
2. HPA shows `<unknown>/60%` — two possible causes? (metrics-server broken; no resource requests set)
3. Why is scale-down deliberately slower than scale-up?

---

# Week 7: KEDA — Event-Driven Autoscaling, 0 → N → 0

**Goal:** Build a RabbitMQ-backed worker that sleeps at 0 replicas when idle, wakes when messages arrive, scales with queue depth, and drains back to zero.

## Day 1: How KEDA Actually Works

KEDA doesn't replace the HPA — it **extends** it:
- The **keda-operator** watches `ScaledObject` CRDs and polls event sources (RabbitMQ, Kafka, Prometheus, AWS SQS, cron... 60+ scalers).
- For each ScaledObject it **creates and owns an HPA** wired to an external metric served by KEDA's **metrics adapter**.
- **0 ↔ 1** ("activation") is done by KEDA itself — HPA can't go to zero. **1 ↔ N** is the generated HPA doing normal HPA math on the external metric.

```mermaid
flowchart LR
    Q[(RabbitMQ<br/>queue: orders)] -->|poll every 15s| OP[keda-operator]
    OP -->|0 ↔ 1 activation| D[Deployment: worker]
    OP -->|creates + manages| H[HPA keda-hpa-worker]
    MA[keda metrics adapter] -->|external metric:<br/>queue length| H
    H -->|1 ↔ N| D
    D --> W1[worker pods consume messages]
```

```bash
helm repo add kedacore https://kedacore.github.io/charts
helm repo update
helm install keda kedacore/keda -n keda --create-namespace
kubectl get pods -n keda     # keda-operator + keda-operator-metrics-apiserver
```

## Day 2: Stand Up RabbitMQ + a Worker

```yaml
# rabbitmq.yaml (dev-grade single node)
apiVersion: apps/v1
kind: Deployment
metadata: { name: rabbitmq }
spec:
  replicas: 1
  selector: { matchLabels: { app: rabbitmq } }
  template:
    metadata: { labels: { app: rabbitmq } }
    spec:
      containers:
        - name: rabbitmq
          image: rabbitmq:3.13-management
          ports:
            - { containerPort: 5672, name: amqp }
            - { containerPort: 15672, name: mgmt }
          resources:
            requests: { cpu: 200m, memory: 256Mi }
            limits:   { cpu: 500m, memory: 512Mi }
---
apiVersion: v1
kind: Service
metadata: { name: rabbitmq }
spec:
  selector: { app: rabbitmq }
  ports:
    - { port: 5672, name: amqp }
    - { port: 15672, name: mgmt }
---
apiVersion: v1
kind: Secret
metadata: { name: rabbitmq-conn }
type: Opaque
stringData:
  host: "amqp://guest:guest@rabbitmq.demo.svc.cluster.local:5672/"
```

```yaml
# worker.yaml — consumes one message every ~2s (simulated work)
apiVersion: apps/v1
kind: Deployment
metadata: { name: order-worker }
spec:
  replicas: 0                       # KEDA owns the replica count — start asleep
  selector: { matchLabels: { app: order-worker } }
  template:
    metadata: { labels: { app: order-worker } }
    spec:
      containers:
        - name: worker
          image: python:3.12-slim
          command: ["sh", "-c"]
          args:
            - pip install pika -q &&
              python -c "
              import pika, time
              conn = pika.BlockingConnection(pika.URLParameters('amqp://guest:guest@rabbitmq.demo.svc.cluster.local:5672/'))
              ch = conn.channel(); ch.queue_declare(queue='orders', durable=True)
              ch.basic_qos(prefetch_count=1)
              def work(c, m, p, body):
                  print('processing', body.decode(), flush=True); time.sleep(2); c.basic_ack(m.delivery_tag)
              ch.basic_consume(queue='orders', on_message_callback=work)
              print('worker ready', flush=True); ch.start_consuming()"
          resources:
            requests: { cpu: 100m, memory: 128Mi }
            limits:   { cpu: 300m, memory: 256Mi }
```

## Day 3: The ScaledObject

```yaml
# scaledobject.yaml
apiVersion: keda.sh/v1alpha1
kind: TriggerAuthentication
metadata: { name: rabbitmq-auth }
spec:
  secretTargetRef:
    - parameter: host
      name: rabbitmq-conn
      key: host
---
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata: { name: order-worker }
spec:
  scaleTargetRef:
    name: order-worker              # the Deployment
  minReplicaCount: 0                # ← sleep at zero
  maxReplicaCount: 20
  pollingInterval: 10               # check queue every 10s
  cooldownPeriod: 60                # 60s of zero activity before N→0
  triggers:
    - type: rabbitmq
      metadata:
        queueName: orders
        mode: QueueLength
        value: "5"                  # target ≈ 5 messages per replica
      authenticationRef:
        name: rabbitmq-auth
```

```bash
kubectl apply -f rabbitmq.yaml -f worker.yaml -f scaledobject.yaml
kubectl get scaledobject            # READY=True, ACTIVE=False (queue empty)
kubectl get hpa                     # keda-hpa-order-worker — created FOR you
kubectl get pods -l app=order-worker   # No resources found. Zero. Sleeping. Free.
```

## Day 4: The Show — 0 → N → 0

Flood the queue with 200 messages:

```bash
kubectl run publisher --rm -it --image=python:3.12-slim --restart=Never -- sh -c '
pip install pika -q && python -c "
import pika
conn = pika.BlockingConnection(pika.URLParameters(\"amqp://guest:guest@rabbitmq.demo.svc.cluster.local:5672/\"))
ch = conn.channel(); ch.queue_declare(queue=\"orders\", durable=True)
for i in range(200):
    ch.basic_publish(exchange=\"\", routing_key=\"orders\", body=f\"order-{i}\".encode())
print(\"published 200\")"'
```

Watch the lifecycle:

```bash
kubectl get pods -l app=order-worker -w
# (no pods) → 1 pod appears within ~10s  ← KEDA activation 0→1
# → 4, 8... pods                          ← HPA: 200 msgs / 5 per replica, ramped by policy
kubectl get hpa keda-hpa-order-worker -w  # watch the external metric value fall as queue drains
kubectl logs -l app=order-worker -f --max-log-requests=10
# queue hits 0 → ACTIVE=False → 60s cooldown → pods → 0
kubectl get scaledobject order-worker -w
```

```mermaid
sequenceDiagram
    participant PUB as publisher
    participant Q as orders queue
    participant K as KEDA
    participant D as worker Deployment
    Note over D: replicas = 0 (idle, costs nothing)
    PUB->>Q: 200 messages
    K->>Q: poll: depth=200 > 0 → ACTIVE
    K->>D: activate 0 → 1
    K->>D: HPA: 200/5 = 40 → capped at max 20, ramped up
    D->>Q: consume, ack, depth falls
    K->>Q: poll: depth=0 → inactive
    Note over K: cooldownPeriod 60s
    K->>D: scale → 0
```

**Day 5 — try the other trigger types** (swap into the same ScaledObject):

```yaml
# Prometheus: scale web on requests/sec (pairs with Week 8)
- type: prometheus
  metadata:
    serverAddress: http://kps-kube-prometheus-stack-prometheus.monitoring:9090
    query: sum(rate(http_requests_total{app="web"}[2m]))
    threshold: "100"
# Cron: pre-warm for business hours
- type: cron
  metadata: { timezone: Asia/Kolkata, start: "0 9 * * *", end: "0 21 * * *", desiredReplicas: "5" }
# Kafka consumer lag
- type: kafka
  metadata: { bootstrapServers: kafka:9092, consumerGroup: order-processors, topic: orders, lagThreshold: "50" }
```

**Rules of the road:** never attach a hand-written HPA *and* a ScaledObject to the same Deployment (they fight); for long jobs that shouldn't be killed mid-message on scale-down, look at **ScaledJob** (one Job per work batch) instead of a Deployment.

## Week 7 Checkpoint

1. Which component handles 0→1, and which handles 1→N? Why the split? (KEDA operator activates; the KEDA-generated HPA scales — HPA cannot reach 0)
2. `value: "5"` with 80 queued messages → how many replicas does the HPA target? (16)
3. When would you choose ScaledJob over ScaledObject?

---

# Week 8: Monitoring — Prometheus, Grafana & Alerts

**Goal:** Install the kube-prometheus-stack, understand every component's job, build a dashboard, and write the alerts a production cluster actually needs.

## Day 1: Install the Stack

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm install kps prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace

kubectl get pods -n monitoring
# prometheus-...            the time-series DB + scraper
# kps-grafana-...           dashboards
# kps-kube-state-metrics    object-state metrics (deployments, pods, PVCs as metrics)
# kps-prometheus-node-exporter (DaemonSet)  node-level CPU/mem/disk
# alertmanager-...          alert routing/dedup/silencing
# kps-...-operator          manages it all via CRDs
```

```mermaid
flowchart LR
    NE[node-exporter DaemonSet<br/>node cpu/mem/disk/net] --> PR[Prometheus<br/>scrapes every 30s, stores TSDB]
    KSM[kube-state-metrics<br/>kube_pod_status_phase,<br/>kube_deployment_replicas...] --> PR
    CAD[kubelet cAdvisor<br/>container_cpu_usage_seconds...] --> PR
    APP[your app /metrics<br/>via ServiceMonitor] --> PR
    PR --> GF[Grafana]
    PR -->|alert rules evaluated| AM[Alertmanager] --> SL[Slack / PagerDuty / email]
```

**Key distinction:** metrics-server (Week 6) is a tiny in-memory pipe feeding HPA/`kubectl top` only. Prometheus is full historical storage + query language + alerting. You run both.

```bash
# Access the UIs
kubectl port-forward -n monitoring svc/kps-grafana 3000:80 &
kubectl port-forward -n monitoring svc/kps-kube-prometheus-stack-prometheus 9090:9090 &
# Grafana: http://localhost:3000  (user: admin)
kubectl get secret -n monitoring kps-grafana -o jsonpath='{.data.admin-password}' | base64 -d
```

## Day 2–3: PromQL — The 10 Queries That Matter

Open Prometheus (`:9090`) → Graph, and run each:

```promql
# 1. Pod CPU (cores) by pod, your namespace
sum(rate(container_cpu_usage_seconds_total{namespace="demo"}[5m])) by (pod)

# 2. Pod memory working set
sum(container_memory_working_set_bytes{namespace="demo"}) by (pod)

# 3. Pods NOT in Running/Succeeded — the "something's wrong" list
kube_pod_status_phase{phase=~"Pending|Failed|Unknown"} == 1

# 4. Restart counter increasing = crash-looping
increase(kube_pod_container_status_restarts_total{namespace="demo"}[15m]) > 0

# 5. Deployment availability gap
kube_deployment_spec_replicas - kube_deployment_status_replicas_available

# 6. Node CPU utilization %
100 * (1 - avg by(instance)(rate(node_cpu_seconds_total{mode="idle"}[5m])))

# 7. Node disk free %
node_filesystem_avail_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"} * 100

# 8. Container memory vs its limit (OOM early warning, >90%)
container_memory_working_set_bytes / on(pod,container)
  kube_pod_container_resource_limits{resource="memory"} > 0.9

# 9. HPA pinned at max (capacity ceiling!)
kube_horizontalpodautoscaler_status_current_replicas
  >= kube_horizontalpodautoscaler_spec_max_replicas

# 10. PVC almost full
kubelet_volume_stats_used_bytes / kubelet_volume_stats_capacity_bytes > 0.85
```

Grasp the pattern `rate(counter[5m])`: counters only go up; `rate()` turns them into per-second speed. 90% of PromQL is `sum(rate(...)) by (label)`.

## Day 4: Dashboards & Scraping Your Own App

Grafana ships preloaded dashboards — explore **Kubernetes / Compute Resources / Namespace (Pods)** while re-running the Week 6 k6 test; watch the HPA scale-out on the graphs.

To scrape your own app, expose `/metrics` (Prometheus client library in your language) and declare a **ServiceMonitor**:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: api
  namespace: demo
  labels:
    release: kps                 # must match the Helm release label so Prometheus picks it up
spec:
  selector:
    matchLabels: { app: api }    # finds the Service (not pods)
  endpoints:
    - port: http
      path: /metrics
      interval: 30s
```

Now your app metrics flow into the same Prometheus — and become available to **KEDA's prometheus trigger** (Week 7) and Grafana. The loop closes: app metric → KEDA → scale.

## Day 5: Alerts That Matter

```yaml
# alerts.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: demo-alerts
  namespace: monitoring
  labels: { release: kps }
spec:
  groups:
    - name: workload.rules
      rules:
        - alert: PodCrashLooping
          expr: increase(kube_pod_container_status_restarts_total{namespace="demo"}[15m]) > 3
          for: 5m
          labels: { severity: critical }
          annotations:
            summary: "{{ $labels.pod }} restarting repeatedly"
        - alert: DeploymentReplicasUnavailable
          expr: kube_deployment_status_replicas_unavailable{namespace="demo"} > 0
          for: 10m
          labels: { severity: warning }
        - alert: HpaAtMaxCapacity
          expr: kube_horizontalpodautoscaler_status_current_replicas >= kube_horizontalpodautoscaler_spec_max_replicas
          for: 15m
          labels: { severity: warning }
          annotations:
            summary: "HPA {{ $labels.horizontalpodautoscaler }} pinned at max — raise capacity"
        - alert: NodeDiskAlmostFull
          expr: node_filesystem_avail_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"} < 0.15
          for: 10m
          labels: { severity: critical }
        - alert: ContainerNearMemoryLimit
          expr: container_memory_working_set_bytes / on(pod,container) kube_pod_container_resource_limits{resource="memory"} > 0.9
          for: 10m
          labels: { severity: warning }
```

```bash
kubectl apply -f alerts.yaml
# Trigger one: kubectl run crasher --image=busybox -- sh -c "exit 1"
# Watch it fire: Prometheus UI → Alerts (Pending → Firing) → Alertmanager UI routes it
```

The `for:` clause is your flap-protection: condition must hold that long before firing. Route severity=critical to PagerDuty and warning to Slack via Alertmanager's config (receivers + routes in the Helm values).

**Logs (the other pillar):** pods write to stdout; a DaemonSet collector ships them centrally:

```bash
helm repo add grafana https://grafana.github.io/helm-charts
helm install loki grafana/loki-stack -n monitoring --set promtail.enabled=true
# Grafana → Explore → Loki → query: {namespace="demo"} |= "error"
```

## Week 8 Checkpoint

1. metrics-server vs Prometheus — different jobs, what are they?
2. Where do `kube_deployment_*` metrics come from vs `container_cpu_*`? (kube-state-metrics watches the API; cAdvisor in kubelet measures cgroups)
3. Why is "HPA at max for 15m" one of the most valuable alerts you can have?

---

# Week 9+: Helm, GitOps with ArgoCD, RBAC & Hardening

**Goal:** Stop `kubectl apply`-ing by hand. Package with Helm, let ArgoCD sync from Git, lock down access, and pass the production checklist.

## Day 1–2: Helm — Package Your App

You've accumulated a dozen YAML files with values hardcoded. Helm templates them:

```bash
helm create myapp && cd myapp
# Chart.yaml   - name/version metadata
# values.yaml  - the knobs (image, replicas, resources...)
# templates/   - your YAML with {{ .Values.* }} placeholders
```

Move your Week 3–4 api into the chart: in `values.yaml` set image/tag/replicas/resources/probe paths; templates reference them:

```yaml
# templates/deployment.yaml (excerpt)
spec:
  replicas: {{ .Values.replicaCount }}
  template:
    spec:
      containers:
        - name: {{ .Chart.Name }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          resources: {{- toYaml .Values.resources | nindent 12 }}
```

```bash
helm template myapp .                          # render locally, inspect output
helm install api . -n demo
helm upgrade api . -n demo --set image.tag=1.4.3
helm rollback api 1 -n demo
helm list -n demo
# Per-environment: values-prod.yaml overrides → helm upgrade api . -f values-prod.yaml
```

## Day 3–4: GitOps with ArgoCD

The shift: **Git becomes the only way to change the cluster.** An in-cluster agent pulls and reconciles; humans stop running apply.

```mermaid
flowchart LR
    DEV[You] -- PR + merge --> GIT[(Git repo:<br/>Helm chart / manifests)]
    ARGO[ArgoCD] -- watches every 3 min --> GIT
    ARGO -- sync + continuous reconcile --> K8S[Cluster]
    K8S -- manual kubectl change = DRIFT --> ARGO
    ARGO -- auto-heals drift back to Git state --> K8S
```

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl port-forward -n argocd svc/argocd-server 8080:443 &
kubectl get secret -n argocd argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d
```

```yaml
# application.yaml — point ArgoCD at your chart repo
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: api
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/<you>/myapp-chart
    targetRevision: main
    path: .
    helm:
      valueFiles: [values.yaml]
  destination:
    server: https://kubernetes.default.svc
    namespace: demo
  syncPolicy:
    automated:
      prune: true          # delete resources removed from Git
      selfHeal: true       # revert manual drift
    syncOptions: [CreateNamespace=true]
```

**The two demos that sell GitOps forever:**
1. Edit `replicaCount` in Git, push → watch ArgoCD UI sync the cluster within minutes. Deploy = git commit; rollback = git revert; audit trail = git log.
2. `kubectl scale deploy/api --replicas=10` by hand → watch ArgoCD flag drift and **snap it back** to Git's truth. Console-cowboy changes are now impossible to keep.

## Day 5: RBAC

```mermaid
flowchart LR
    SA[ServiceAccount ci-bot] --> RB[RoleBinding] --> R[Role: get/list pods in demo]
    U[group: dev-team] --> CRB[ClusterRoleBinding] --> CR[ClusterRole: view]
```

```yaml
apiVersion: v1
kind: ServiceAccount
metadata: { name: ci-bot, namespace: demo }
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata: { name: deployer, namespace: demo }
rules:
  - apiGroups: ["apps"]
    resources: [deployments]
    verbs: [get, list, watch, update, patch]
  - apiGroups: [""]
    resources: [pods, pods/log]
    verbs: [get, list, watch]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata: { name: ci-bot-deployer, namespace: demo }
subjects: [{ kind: ServiceAccount, name: ci-bot, namespace: demo }]
roleRef: { kind: Role, name: deployer, apiGroup: rbac.authorization.k8s.io }
```

```bash
kubectl auth can-i update deployments -n demo --as=system:serviceaccount:demo:ci-bot   # yes
kubectl auth can-i delete secrets    -n demo --as=system:serviceaccount:demo:ci-bot   # no
```

Principle of least privilege: namespace-scoped Roles by default; ClusterRoles only when truly cluster-wide; never bind `cluster-admin` to workloads.

## Day 6–7: Hardening Pass

Apply to everything you built in weeks 1–8:

```yaml
# Per-container security context (add to every Deployment)
securityContext:
  runAsNonRoot: true
  runAsUser: 10001
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities: { drop: [ALL] }
```

```yaml
# Namespace guardrails
apiVersion: v1
kind: ResourceQuota
metadata: { name: demo-quota, namespace: demo }
spec:
  hard:
    requests.cpu: "8"
    requests.memory: 16Gi
    limits.cpu: "16"
    limits.memory: 32Gi
    persistentvolumeclaims: "10"
---
apiVersion: v1
kind: LimitRange                      # defaults for containers that forget
metadata: { name: demo-defaults, namespace: demo }
spec:
  limits:
    - type: Container
      default:        { cpu: 300m, memory: 256Mi }
      defaultRequest: { cpu: 100m, memory: 128Mi }
```

Plus: default-deny NetworkPolicy per namespace (allow explicitly), Pod Security Admission labels on namespaces (`pod-security.kubernetes.io/enforce: restricted`), External Secrets Operator instead of raw Secrets, image digest pinning, and `kubectl drain` rehearsals with PDBs in place.

---

# Capstone: The Order System — Everything, Together

Build this start to finish without copy-pasting from above. If you can debug it end-to-end, you know Kubernetes.

```mermaid
flowchart TB
    NET[Internet] --> ING[Ingress + TLS via cert-manager]
    ING -- "/" --> WEB[web Deployment<br/>HPA 2-10 on CPU<br/>probes + PDB]
    ING -- "/api" --> API[api Deployment<br/>probes + PDB + ServiceMonitor]
    API --> PG[(postgres StatefulSet<br/>PVC + Secret + pg_isready probes)]
    API -- publish order events --> MQ[(RabbitMQ)]
    MQ --> WK[order-worker Deployment<br/>KEDA ScaledObject 0-20<br/>on queue depth]
    WK --> PG
    PROM[Prometheus + Grafana<br/>alerts: crashloop, HPA@max,<br/>PVC full, replicas unavailable] -.scrapes everything.-> WEB & API & WK
    GIT[(Git repo: Helm charts)] --> ARGO[ArgoCD auto-sync] --> WEB & API & WK & PG & MQ
```

**Definition of done:**
1. `git push` deploys; `git revert` rolls back; manual `kubectl` drift self-heals.
2. k6 load on `/api` scales web/api via HPA with **zero failed requests** during a simultaneous rolling update.
3. Publishing 500 orders wakes the worker from 0, scales it out, drains, returns to 0.
4. Killing `pg-0` loses no data; killing any node (multi-node kind cluster) keeps the app serving thanks to anti-affinity + PDBs.
5. Every failure above shows up in Grafana, and the right alerts fire.

**Where to go next:** CKA/CKAD certification (this guide covers most of the practical surface), service mesh (Linkerd → Istio), Karpenter for node autoscaling, CloudNativePG for real database HA, and Velero for backup/restore drills.