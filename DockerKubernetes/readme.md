# 🐳 Docker & ☸️ Kubernetes Mastery
### From Containers to Clusters — Architecture-First Guide

---

> **Who is this for?** Someone with basic Docker familiarity who wants to go deep — understanding *why* things work the way they do, not just *how* to type commands. Every concept is tied to system design thinking.

---

# PART 1 — DOCKER DEEP DIVE

---

## 1.1 — The Mental Model: What Docker Actually Is

Before commands, understand the architecture. Docker solves one core problem:

> *"It works on my machine"* → Docker makes **every machine the same machine**.

```mermaid
graph TD
    subgraph "Without Docker"
        A[Your Code] -->|depends on| B[Python 3.9]
        A -->|depends on| C[libssl 1.1]
        A -->|depends on| D[Your OS libs]
        B --> E[Works on Dev ✅]
        B --> F[Breaks on Prod ❌]
    end

    subgraph "With Docker"
        G[Your Code] --> H[Docker Image]
        H -->|contains| I[Python 3.9]
        H -->|contains| J[libssl 1.1]
        H -->|contains| K[All deps]
        H --> L[Works Everywhere ✅]
    end
```

---

## 1.2 — Docker Architecture (The Full Picture)

```mermaid
graph TB
    subgraph "Developer Machine"
        CLI[Docker CLI<br/>docker run, build, push...]
        DC[Docker Compose<br/>docker-compose.yml]
    end

    subgraph "Docker Engine"
        D[dockerd<br/>Docker Daemon]
        API[REST API]
        CR[containerd<br/>Container Runtime]
        SN[snapshotter<br/>Image Layers]
    end

    subgraph "Kernel"
        NS[Namespaces<br/>PID, NET, MNT, UTS]
        CG[cgroups<br/>CPU, Memory limits]
        UF[UnionFS<br/>Overlay Filesystem]
    end

    subgraph "Registry"
        DH[Docker Hub]
        GCR[GCR / ECR / GHCR]
        PR[Private Registry]
    end

    CLI -->|commands| API
    DC -->|orchestrates| CLI
    API --> D
    D --> CR
    CR --> NS
    CR --> CG
    CR --> UF
    D <-->|push/pull images| DH
    D <-->|push/pull images| PR
    SN --> UF
```

**Key insight for system design:** Docker Engine is a client-server architecture. The CLI talks to a daemon over a Unix socket or TCP. This is why you can run Docker CLI on one machine and connect to a remote daemon.

---

## 1.3 — Images vs Containers vs Volumes (The Holy Trinity)

```mermaid
graph LR

    REG[Registry]

    subgraph IMG["Image — Blueprint (Read Only)"]
        L1[Layer: Ubuntu Base]
        L2[Layer: Install Python]
        L3[Layer: Copy app code]
        L4[Layer: CMD python app.py]
        L1 --> L2 --> L3 --> L4
    end

    subgraph CONT["Container — Running Instance"]
        RW[Read-Write Layer<br/>Runtime Changes]
    end

    subgraph VOL["Volume — Persistent Storage"]
        V[/var/lib/docker/volumes/mydata/]
    end

    REG -->|docker pull| L1
    L4 -->|container starts with| RW
    RW -->|mounts| V
```

**System Design Note:** Images are immutable and layered. This is the same concept as Git commits — each layer is a delta. Identical layers are shared across images (dedup). This is why pulling `node:18` after you already have `node:16` is fast — they share base layers.

---

## 1.4 — The Dockerfile: Building Images

### Anatomy of a Production-Grade Dockerfile

```dockerfile
# ── Stage 1: Build ──────────────────────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /app

# Copy dependency manifests FIRST (layer cache optimization)
COPY package*.json ./
RUN npm ci --only=production

# Then copy source
COPY . .
RUN npm run build

# ── Stage 2: Runtime (smaller final image) ───────────────────────────
FROM node:20-alpine AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Only copy what's needed
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules

USER appuser
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:3000/health || exit 1
CMD ["node", "dist/server.js"]
```

### All Dockerfile Instructions

| Instruction | Purpose | Example |
|---|---|---|
| `FROM` | Base image | `FROM python:3.11-slim` |
| `WORKDIR` | Set working directory | `WORKDIR /app` |
| `COPY` | Copy files from host | `COPY src/ ./src/` |
| `ADD` | Like COPY but supports URLs & tar extraction | `ADD https://... /file` |
| `RUN` | Execute command during build | `RUN apt-get install -y curl` |
| `CMD` | Default command (overridable) | `CMD ["python", "app.py"]` |
| `ENTRYPOINT` | Fixed command (args appended) | `ENTRYPOINT ["nginx", "-g"]` |
| `ENV` | Set environment variable | `ENV NODE_ENV=production` |
| `ARG` | Build-time variable | `ARG VERSION=1.0` |
| `EXPOSE` | Document port (doesn't publish) | `EXPOSE 8080` |
| `VOLUME` | Declare mount point | `VOLUME ["/data"]` |
| `USER` | Switch user | `USER nobody` |
| `LABEL` | Metadata | `LABEL maintainer="you@co"` |
| `HEALTHCHECK` | Container health check | `HEALTHCHECK CMD curl -f ...` |
| `STOPSIGNAL` | Signal for stopping container | `STOPSIGNAL SIGTERM` |
| `SHELL` | Override default shell | `SHELL ["/bin/bash", "-c"]` |
| `ONBUILD` | Trigger for child images | `ONBUILD COPY . .` |

### Layer Caching — The Most Important Optimization

```mermaid
graph TD
    A[docker build] --> B{Layer in cache?}
    B -->|Yes| C[Use cached layer ⚡]
    B -->|No| D[Build layer 🐢]
    D --> E{Any layer<br/>changed above?}
    E -->|Yes| F[ALL layers below<br/>rebuild too!]
    E -->|No| G[Continue]
    C --> H[Next Layer]
    G --> H

    style F fill:#ff6b6b,color:#fff
    style C fill:#51cf66,color:#fff
```

**Rule:** Put things that change LEAST at the top, most at the bottom. `package.json` before `COPY . .`

---

## 1.5 — Docker Commands: Complete Reference

### 🏗️ Image Commands

```bash
# Build an image
docker build -t myapp:1.0 .
docker build -t myapp:1.0 -f Dockerfile.prod .    # specific Dockerfile
docker build --no-cache -t myapp .                 # bypass cache
docker build --build-arg VERSION=2.0 -t myapp .   # pass build args
docker build --target builder -t myapp-dev .       # stop at specific stage

# List images
docker images
docker image ls
docker image ls --filter "dangling=true"           # untagged images

# Tag an image
docker tag myapp:1.0 myregistry.com/myapp:1.0
docker tag myapp:latest myapp:backup

# Push / Pull
docker push myregistry.com/myapp:1.0
docker pull nginx:alpine
docker pull --all-tags nginx                       # pull all tags

# Inspect
docker image inspect nginx:alpine
docker image history nginx:alpine                  # show layers

# Remove
docker rmi myapp:1.0
docker image prune                                 # remove dangling
docker image prune -a                              # remove all unused

# Save / Load (for air-gapped environments)
docker save myapp:1.0 | gzip > myapp.tar.gz
docker load < myapp.tar.gz
```

### 🚀 Container Commands

```bash
# Run containers
docker run nginx                                   # foreground
docker run -d nginx                                # detached (background)
docker run -it ubuntu bash                         # interactive terminal
docker run --name webserver nginx                  # named container
docker run --rm nginx                              # auto-remove on exit

# Port mapping
docker run -p 8080:80 nginx                        # host:container
docker run -p 127.0.0.1:8080:80 nginx             # bind to specific interface
docker run -P nginx                                # auto-map all EXPOSE ports

# Environment variables
docker run -e NODE_ENV=production myapp
docker run --env-file .env myapp

# Volume mounts
docker run -v /host/path:/container/path nginx     # bind mount
docker run -v myvolume:/data nginx                 # named volume
docker run --mount type=tmpfs,target=/tmp nginx    # tmpfs (in-memory)

# Resource limits
docker run --memory="512m" --cpus="1.5" myapp
docker run --memory-swap="1g" myapp                # swap limit

# Networking
docker run --network mynetwork nginx
docker run --network host nginx                    # use host network
docker run --network none nginx                    # no network

# Restart policies
docker run --restart=always nginx
docker run --restart=on-failure:3 myapp            # retry 3 times
docker run --restart=unless-stopped nginx

# Lifecycle
docker start mycontainer
docker stop mycontainer                            # graceful (SIGTERM)
docker kill mycontainer                            # immediate (SIGKILL)
docker restart mycontainer
docker pause mycontainer
docker unpause mycontainer

# List containers
docker ps                                          # running
docker ps -a                                       # all (including stopped)
docker ps -q                                       # IDs only
docker ps --filter "status=exited"
docker ps --filter "name=web"

# Exec into running container
docker exec -it mycontainer bash
docker exec -it mycontainer sh                     # if no bash
docker exec mycontainer ls /app                    # one-off command
docker exec -u root mycontainer bash               # as root

# Copy files
docker cp mycontainer:/app/logs/app.log ./app.log
docker cp ./config.json mycontainer:/app/config.json

# Logs
docker logs mycontainer
docker logs -f mycontainer                         # follow (tail -f)
docker logs --tail 100 mycontainer                 # last 100 lines
docker logs --since 2h mycontainer                 # last 2 hours
docker logs --timestamps mycontainer

# Inspect & Stats
docker inspect mycontainer
docker inspect -f '{{.NetworkSettings.IPAddress}}' mycontainer
docker stats                                       # live resource usage
docker stats --no-stream                           # snapshot
docker top mycontainer                             # processes inside

# Cleanup
docker rm mycontainer
docker rm -f mycontainer                           # force remove running
docker container prune                             # remove all stopped
docker rm $(docker ps -aq)                         # remove all containers
```

### 💾 Volume Commands

```bash
# Create & manage
docker volume create mydata
docker volume create --driver local mydata
docker volume ls
docker volume inspect mydata
docker volume rm mydata
docker volume prune                                # remove unused volumes

# Use in run
docker run -v mydata:/app/data myapp
docker run --mount source=mydata,target=/app/data myapp
```

### 🌐 Network Commands

```bash
# Create networks
docker network create mynetwork
docker network create --driver bridge mynet
docker network create --driver overlay mynet       # for Swarm
docker network create --subnet 172.20.0.0/16 mynet

# List & inspect
docker network ls
docker network inspect mynetwork
docker network inspect bridge                      # default bridge

# Connect/disconnect containers
docker network connect mynetwork mycontainer
docker network disconnect mynetwork mycontainer

# Remove
docker network rm mynetwork
docker network prune
```

### 🧹 System Commands

```bash
# Nuclear cleanup
docker system prune                               # remove unused everything
docker system prune -a                            # + unused images
docker system prune --volumes                     # + volumes (CAREFUL)

# Disk usage
docker system df
docker system df -v                               # verbose

# System info
docker info
docker version
docker system events                              # live event stream
docker system events --filter type=container
```

---

## 1.6 — Docker Networking Deep Dive

```mermaid
graph TB
    subgraph "Host Machine"
        subgraph "Bridge Network: myapp_net (172.20.0.0/16)"
            WEB[web container<br/>172.20.0.2:80]
            API[api container<br/>172.20.0.3:3000]
            DB[db container<br/>172.20.0.4:5432]
        end

        subgraph "Host Network"
            MON[monitoring<br/>uses host IP directly]
        end

        BR[docker0 bridge<br/>virtual switch]
        IPTABLES[iptables / NAT rules]
    end

    INTERNET[Internet / Host Apps]
    OUTSIDE[Port 8080 → web:80]

    WEB <-->|by name: 'api'| API
    API <-->|by name: 'db'| DB
    BR --- WEB
    BR --- API
    IPTABLES -->|port mapping| WEB
    INTERNET -->|:8080| IPTABLES
    OUTSIDE -.->|example| IPTABLES

    style WEB fill:#4dabf7
    style API fill:#69db7c
    style DB fill:#ffa94d
```

### Network Drivers

| Driver | Use Case | Scope |
|---|---|---|
| `bridge` | Default. Isolated network on single host | Single host |
| `host` | Container uses host's network stack | Single host |
| `none` | No networking | Single host |
| `overlay` | Multi-host (Docker Swarm / K8s) | Multi-host |
| `macvlan` | Container gets its own MAC address | Single host |
| `ipvlan` | Layer 3 routing control | Single host |

**System Design insight:** Services on the same Docker network can reach each other by **container name** as the hostname. Docker has a built-in DNS server. This is the same concept as Kubernetes Service discovery.

---

## 1.7 — Docker Compose: Multi-Service Architecture

Compose is for running multi-container apps on a **single machine**. Think of it as the local developer's version of Kubernetes.

### Complete docker-compose.yml Reference

```yaml
version: "3.9"

# ── Named volumes ─────────────────────────────────────────────────────
volumes:
  postgres_data:
  redis_data:
  static_files:

# ── Named networks ────────────────────────────────────────────────────
networks:
  frontend:           # web → api
  backend:            # api → db, redis (isolated from frontend)

# ── Services ──────────────────────────────────────────────────────────
services:

  # ── Nginx Reverse Proxy ────────────────────────────────────────────
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - static_files:/usr/share/nginx/html:ro
    networks:
      - frontend
    depends_on:
      api:
        condition: service_healthy
    restart: unless-stopped

  # ── API Service ────────────────────────────────────────────────────
  api:
    build:
      context: ./api
      dockerfile: Dockerfile
      target: runtime
      args:
        - NODE_ENV=production
    environment:
      - NODE_ENV=production
      - DATABASE_URL=postgresql://user:pass@postgres:5432/mydb
      - REDIS_URL=redis://redis:6379
    env_file:
      - .env.production
    networks:
      - frontend
      - backend
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:3000/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    deploy:
      resources:
        limits:
          cpus: "1"
          memory: 512M
    restart: unless-stopped

  # ── PostgreSQL ─────────────────────────────────────────────────────
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: user
      POSTGRES_PASSWORD: pass
      POSTGRES_DB: mydb
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./db/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    networks:
      - backend
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U user -d mydb"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  # ── Redis ──────────────────────────────────────────────────────────
  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis_data:/data
    networks:
      - backend
    restart: unless-stopped

  # ── Background Worker ──────────────────────────────────────────────
  worker:
    build:
      context: ./api
    command: ["node", "dist/worker.js"]
    environment:
      - DATABASE_URL=postgresql://user:pass@postgres:5432/mydb
      - REDIS_URL=redis://redis:6379
    networks:
      - backend
    depends_on:
      - postgres
      - redis
    restart: unless-stopped
    deploy:
      replicas: 2                 # run 2 worker instances
```

### Compose Architecture Diagram

```mermaid
graph TD
    USER[Browser / Client]
    
    subgraph "Docker Compose Stack"
        NGINX[nginx:80, 443<br/>Reverse Proxy]
        
        subgraph "frontend network"
            API[api:3000<br/>Node.js App]
        end
        
        subgraph "backend network"
            PG[(postgres:5432<br/>PostgreSQL)]
            RD[(redis:6379<br/>Redis Cache)]
            WK[worker x2<br/>Background Jobs]
        end
    end

    USER -->|HTTP/HTTPS| NGINX
    NGINX -->|proxy_pass| API
    API --> PG
    API --> RD
    WK --> PG
    WK --> RD

    style NGINX fill:#4dabf7
    style API fill:#69db7c
    style PG fill:#ffa94d
    style RD fill:#ff6b6b,color:#fff
    style WK fill:#cc5de8,color:#fff
```

### Docker Compose Commands

```bash
# Start everything
docker compose up
docker compose up -d                              # detached
docker compose up --build                         # force rebuild
docker compose up api postgres                    # specific services only

# Stop
docker compose down
docker compose down -v                            # also remove volumes
docker compose down --rmi all                     # also remove images

# Scaling
docker compose up --scale worker=4               # run 4 worker instances

# Logs
docker compose logs
docker compose logs -f api                        # follow specific service
docker compose logs --tail 50

# Status
docker compose ps
docker compose top                                # running processes

# Execute command
docker compose exec api bash
docker compose exec postgres psql -U user mydb

# Restart
docker compose restart api
docker compose stop api && docker compose start api

# Config
docker compose config                             # validate & view merged config
docker compose config --services                  # list services

# Pull latest images
docker compose pull

# Run one-off command (new container, removed after)
docker compose run --rm api node scripts/migrate.js
```

---

## 1.8 — Docker Registry & Image Tagging Strategy

```mermaid
flowchart LR
    subgraph "Dev"
        CODE[git push]
    end

    subgraph "CI/CD Pipeline"
        BUILD[docker build]
        TEST[docker run tests]
        TAG[docker tag]
        PUSH[docker push]
    end

    subgraph "Registry"
        LATEST[myapp:latest]
        SHA[myapp:abc1234]
        VER[myapp:v2.1.0]
        ENV[myapp:prod-2024-01]
    end

    subgraph "Environments"
        DEV_ENV[Dev: pull latest]
        STAGE[Staging: pull SHA]
        PROD[Prod: pull v2.1.0]
    end

    CODE --> BUILD --> TEST --> TAG --> PUSH
    PUSH --> LATEST & SHA & VER & ENV
    LATEST --> DEV_ENV
    SHA --> STAGE
    VER --> PROD
```

**Best practice tags:**
- `latest` — always points to most recent build (don't use in prod)
- `git-sha` — immutable, traceable to exact commit
- `semver` (v1.2.3) — human-readable releases
- `env-date` (prod-2024-01-15) — environment + date

---

## 1.9 — Docker Security Essentials

```bash
# Scan image for vulnerabilities
docker scout cves myapp:latest
docker scout quickview myapp:latest

# Run as non-root (in Dockerfile)
RUN useradd -r -u 1001 appuser
USER appuser

# Read-only filesystem
docker run --read-only myapp
docker run --read-only --tmpfs /tmp myapp          # allow writes to /tmp

# Drop capabilities
docker run --cap-drop=ALL --cap-add=NET_BIND_SERVICE nginx

# Limit resources (prevent DoS)
docker run --memory="256m" --cpus="0.5" --pids-limit=100 myapp

# Don't expose secrets in env vars (use secrets instead)
docker secret create db_password ./password.txt
docker run --secret db_password myapp

# Scan for secrets in images
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
    aquasec/trivy image myapp:latest
```

---

# PART 2 — KUBERNETES (K8s) MASTERY

---

## 2.1 — Why Kubernetes? The Problem It Solves

Docker Compose runs on **one machine**. What happens when:
- That machine dies? → **No failover**
- Traffic spikes? → **Can't auto-scale**
- You need zero-downtime deploys? → **Hard to do**
- You have 50 services? → **One Compose file hell**

Kubernetes solves all of this. It's a **container orchestrator** — it decides *where*, *when*, and *how many* containers to run across a **cluster** of machines.

```mermaid
graph TB
    subgraph "Docker Compose World (1 machine)"
        M1[Single Machine]
        M1 --- C1[Container A]
        M1 --- C2[Container B]
        M1 --- C3[Container C]
        M1 -->|dies| DEAD[💀 Everything gone]
    end

    subgraph "Kubernetes World (cluster)"
        subgraph "Node 1"
            P1[Pod A1]
            P2[Pod B1]
        end
        subgraph "Node 2"
            P3[Pod A2]
            P4[Pod C1]
        end
        subgraph "Node 3"
            P5[Pod B2]
            P6[Pod C2]
        end
        N1_DEAD[Node 1 dies 💀] -->|K8s reschedules| P7[Pod A1 on Node 3]
    end

    style DEAD fill:#ff6b6b,color:#fff
    style N1_DEAD fill:#ff6b6b,color:#fff
    style P7 fill:#69db7c
```

---

## 2.2 — Kubernetes Architecture: The Full Picture

```mermaid
graph TB
    subgraph "Control Plane (Master Node)"
        AS[API Server<br/>kube-apiserver<br/>The Front Door]
        ETCD[(etcd<br/>Cluster State DB<br/>Source of Truth)]
        SCH[Scheduler<br/>kube-scheduler<br/>Where to place Pods?]
        CM[Controller Manager<br/>kube-controller-manager<br/>Reconciliation loops]
        CCM[Cloud Controller<br/>cloud-controller-manager<br/>AWS/GCP/Azure]
    end

    subgraph "Worker Node 1"
        KL1[kubelet<br/>Node Agent]
        KP1[kube-proxy<br/>Network Rules]
        CR1[Container Runtime<br/>containerd]
        subgraph "Pods"
            POD1[Pod: api-7d8f]
            POD2[Pod: api-3b2a]
        end
    end

    subgraph "Worker Node 2"
        KL2[kubelet]
        KP2[kube-proxy]
        CR2[containerd]
        subgraph "Pods "
            POD3[Pod: worker-9c1e]
            POD4[Pod: postgres-0]
        end
    end

    KUBECTL[kubectl<br/>CLI Tool] -->|HTTPS| AS
    CI[CI/CD] -->|HTTPS| AS
    AS <--> ETCD
    AS --> SCH
    AS --> CM
    SCH -->|assigns pods| KL1 & KL2
    CM -->|watches & reconciles| AS
    KL1 <-->|reports status| AS
    KL2 <-->|reports status| AS
    KL1 --> CR1 --> POD1 & POD2
    KL2 --> CR2 --> POD3 & POD4
    KP1 & KP2 -->|iptables/IPVS| NETWORK[Cluster Network]

    style AS fill:#4dabf7,color:#000
    style ETCD fill:#ffa94d,color:#000
    style SCH fill:#69db7c,color:#000
    style CM fill:#cc5de8,color:#fff
```

### Control Plane Components Explained

| Component | Role | Analogy |
|---|---|---|
| **API Server** | All requests go through here. Stateless. Can be scaled. | Restaurant front desk |
| **etcd** | Distributed key-value store. ALL cluster state lives here. | Restaurant's order book |
| **Scheduler** | Assigns unscheduled Pods to nodes based on resources, affinity, etc. | Maître d' assigning tables |
| **Controller Manager** | Runs control loops. ReplicaSet controller, Node controller, etc. | Kitchen manager checking orders |
| **kubelet** | Agent on each node. Talks to API Server. Manages pods on its node. | Line cook at each station |
| **kube-proxy** | Maintains network rules (iptables/IPVS) for Service routing | Waiter routing dishes to tables |

---

## 2.3 — Kubernetes Objects: The Building Blocks

```mermaid
graph TD
    subgraph "Workloads"
        DEP[Deployment<br/>Stateless apps]
        STS[StatefulSet<br/>Databases, ordered]
        DS[DaemonSet<br/>One per node]
        JOB[Job / CronJob<br/>Batch tasks]
        POD[Pod<br/>Smallest unit]
    end

    subgraph "Networking"
        SVC[Service<br/>Stable endpoint]
        ING[Ingress<br/>HTTP routing]
        NP[NetworkPolicy<br/>Firewall rules]
    end

    subgraph "Storage"
        PV[PersistentVolume<br/>Actual disk]
        PVC[PersistentVolumeClaim<br/>Request for disk]
        SC[StorageClass<br/>Provisioner]
        CM2[ConfigMap<br/>Config data]
        SEC[Secret<br/>Sensitive data]
    end

    subgraph "Scaling & Policy"
        HPA[HorizontalPodAutoscaler]
        VPA[VerticalPodAutoscaler]
        PDB[PodDisruptionBudget]
        RP[ResourceQuota]
    end

    DEP --> POD
    STS --> POD
    DS --> POD
    JOB --> POD
    SVC -->|routes to| POD
    ING -->|routes to| SVC
    PVC -->|binds to| PV
    SC -->|provisions| PV
    POD -->|mounts| PVC
    POD -->|reads| CM2
    POD -->|reads| SEC
    HPA -->|scales| DEP
```

---

## 2.4 — Pods: The Atomic Unit

A Pod is **one or more containers** that share network & storage. Think of it as a "logical host."

```mermaid
graph TB
    subgraph "Pod: api-7d8f-xk2p"
        direction TB
        subgraph "Shared Network Namespace"
            C1[Main Container<br/>node api :3000]
            C2[Sidecar: log-shipper<br/>reads logs, sends to Elasticsearch]
            C3[Init Container<br/>runs first: wait-for-db]
        end
        subgraph "Shared Volumes"
            LOG_VOL[/var/log volume<br/>shared between C1 & C2]
        end
        NET[localhost:3000<br/>containers share IP]
    end

    C3 -->|runs then exits| C1
    C1 -->|writes logs| LOG_VOL
    C2 -->|reads logs| LOG_VOL
    C1 & C2 --- NET
```

**Key Pod patterns:**
- **Sidecar** — helper container alongside main (log shipper, service mesh proxy)
- **Init Container** — runs before main containers (migrations, wait-for-deps)
- **Ambassador** — proxy local connections to external world

### Pod YAML

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: api-pod
  labels:
    app: api
    version: v2
spec:
  initContainers:
    - name: wait-for-db
      image: busybox
      command: ['sh', '-c', 'until nc -z postgres 5432; do sleep 2; done']

  containers:
    - name: api
      image: myregistry/api:v2.1.0
      ports:
        - containerPort: 3000
      env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
      resources:
        requests:           # guaranteed minimum
          memory: "128Mi"
          cpu: "250m"
        limits:             # hard maximum
          memory: "512Mi"
          cpu: "500m"
      readinessProbe:       # when to send traffic
        httpGet:
          path: /health
          port: 3000
        initialDelaySeconds: 10
        periodSeconds: 5
      livenessProbe:        # when to restart
        httpGet:
          path: /health
          port: 3000
        initialDelaySeconds: 30
        periodSeconds: 10
      volumeMounts:
        - name: app-logs
          mountPath: /var/log/app

    - name: log-shipper
      image: fluent/fluent-bit
      volumeMounts:
        - name: app-logs
          mountPath: /var/log/app

  volumes:
    - name: app-logs
      emptyDir: {}
```

---

## 2.5 — Deployments: Managing Pod Replicas

Deployment = "I want N copies of this Pod, always running, update them this way."

```mermaid
stateDiagram-v2
    [*] --> Desired: kubectl apply
    Desired --> Reconciling: Controller sees drift
    Reconciling --> Scaling: Add/Remove pods
    Reconciling --> RollingUpdate: New image version
    RollingUpdate --> Rollout: Create new ReplicaSet
    Rollout --> Healthy: All new pods ready
    Healthy --> Desired: Watch for drift

    RollingUpdate --> RollbackNeeded: Pods fail readiness
    RollbackNeeded --> Rollback: kubectl rollout undo
    Rollback --> Healthy
```

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-deployment
  labels:
    app: api
spec:
  replicas: 3
  selector:
    matchLabels:
      app: api                    # manages pods with this label
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1                 # create 1 extra pod during update
      maxUnavailable: 0           # never have 0 ready pods (zero-downtime)
  template:
    metadata:
      labels:
        app: api                  # must match selector above
    spec:
      containers:
        - name: api
          image: myregistry/api:v2.1.0
          ports:
            - containerPort: 3000
          resources:
            requests:
              memory: "128Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          readinessProbe:
            httpGet:
              path: /ready
              port: 3000
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /health
              port: 3000
            periodSeconds: 10
```

---

## 2.6 — Services: Stable Networking

Pods are ephemeral — they get new IPs when they restart. Services give you a **stable endpoint** regardless of which pods are running behind them.

```mermaid
graph LR
    CLIENT[Client Pod<br/>or External]

    subgraph "Service: api-service (ClusterIP: 10.96.0.50)"
        EP[Endpoints<br/>10.0.0.2:3000<br/>10.0.0.5:3000<br/>10.0.0.8:3000]
    end

    subgraph "Pods (ephemeral IPs)"
        P1[api pod 1<br/>10.0.0.2]
        P2[api pod 2<br/>10.0.0.5]
        P3[api pod 3<br/>10.0.0.8]
    end

    CLIENT -->|api-service:3000| EP
    EP -->|load balances| P1 & P2 & P3
    P2 -->|dies & restarts| P4[api pod 2<br/>10.0.0.99 new IP]
    EP -->|auto-updates endpoints| P4
```

### Service Types

```yaml
# ── ClusterIP (default) — internal only ──────────────────────────────
apiVersion: v1
kind: Service
metadata:
  name: api-service
spec:
  type: ClusterIP
  selector:
    app: api
  ports:
    - port: 80          # service port
      targetPort: 3000  # container port

---
# ── NodePort — expose on each node's IP ───────────────────────────────
apiVersion: v1
kind: Service
metadata:
  name: api-nodeport
spec:
  type: NodePort
  selector:
    app: api
  ports:
    - port: 80
      targetPort: 3000
      nodePort: 30080   # accessible at <NodeIP>:30080

---
# ── LoadBalancer — cloud load balancer (ELB, GCP LB) ─────────────────
apiVersion: v1
kind: Service
metadata:
  name: api-lb
spec:
  type: LoadBalancer
  selector:
    app: api
  ports:
    - port: 80
      targetPort: 3000

---
# ── ExternalName — DNS alias to external service ──────────────────────
apiVersion: v1
kind: Service
metadata:
  name: external-db
spec:
  type: ExternalName
  externalName: mydb.rds.amazonaws.com
```

### Service Type Comparison

```mermaid
graph TD
    subgraph "ClusterIP"
        CI_C[Internal pods only]
        CI_C --> CI_S[Service IP: 10.96.x.x]
    end

    subgraph "NodePort"
        NP_EXT[External: NodeIP:30080]
        NP_EXT --> NP_S[Service]
        NP_S --> NP_P[Pods]
    end

    subgraph "LoadBalancer"
        LB_EXT[External LB: 52.x.x.x]
        LB_EXT --> LB_S[Service]
        LB_S --> LB_P[Pods]
        CLOUD[Cloud Provider API] -.->|provisions| LB_EXT
    end

    subgraph "Ingress"
        ING_CERT[TLS termination]
        ING_ROUTE[/api → api-svc<br/>/web → web-svc]
        ING_CERT --> ING_ROUTE
        ING_ROUTE --> SVC1[api Service]
        ING_ROUTE --> SVC2[web Service]
    end
```

---

## 2.7 — Ingress: HTTP Routing at Scale

```mermaid
flowchart TD
    INTERNET[Internet]
    LB[Cloud Load Balancer<br/>port 80/443]
    IC[Ingress Controller<br/>nginx/traefik/istio]

    subgraph "Ingress Rules"
        R1["app.example.com/api/*<br/>→ api-service:80"]
        R2["app.example.com/web/*<br/>→ frontend-service:80"]
        R3["admin.example.com/*<br/>→ admin-service:80"]
    end

    SVC1[api Service] --> POD1[api pods]
    SVC2[frontend Service] --> POD2[frontend pods]
    SVC3[admin Service] --> POD3[admin pods]

    INTERNET --> LB --> IC
    IC --> R1 --> SVC1
    IC --> R2 --> SVC2
    IC --> R3 --> SVC3
```

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: app-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - app.example.com
      secretName: app-tls
  rules:
    - host: app.example.com
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: api-service
                port:
                  number: 80
          - path: /
            pathType: Prefix
            backend:
              service:
                name: frontend-service
                port:
                  number: 80
```

---

## 2.8 — ConfigMaps & Secrets

```mermaid
graph LR
    subgraph "Configuration Sources"
        CM[ConfigMap<br/>Non-sensitive config]
        SEC[Secret<br/>base64 encoded<br/>passwords, tokens, certs]
    end

    subgraph "Consumption Methods"
        ENV_VAR[Env Variables]
        VOL_MOUNT[Volume Mount<br/>mounted as files]
    end

    subgraph "Pod"
        APP[Application]
    end

    CM -->|envFrom / env.valueFrom| ENV_VAR
    SEC -->|env.valueFrom.secretKeyRef| ENV_VAR
    CM -->|volumes| VOL_MOUNT
    SEC -->|volumes| VOL_MOUNT
    ENV_VAR --> APP
    VOL_MOUNT --> APP
```

```yaml
# ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  NODE_ENV: production
  LOG_LEVEL: info
  config.json: |
    {
      "maxRetries": 3,
      "timeout": 5000
    }

---
# Secret (base64 encoded values)
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
type: Opaque
data:
  username: dXNlcg==          # echo -n 'user' | base64
  password: c2VjcmV0cGFzcw==  # echo -n 'secretpass' | base64
stringData:                    # plain text (auto base64'd)
  DATABASE_URL: "postgresql://user:pass@postgres:5432/mydb"

---
# Using in a Pod
spec:
  containers:
    - name: api
      envFrom:
        - configMapRef:
            name: app-config  # all keys become env vars
      env:
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: password
      volumeMounts:
        - name: config-vol
          mountPath: /etc/config
  volumes:
    - name: config-vol
      configMap:
        name: app-config
```

---

## 2.9 — StatefulSets: For Databases & Stateful Apps

```mermaid
graph TB
    subgraph "StatefulSet: postgres"
        P0["postgres-0<br/>PRIMARY<br/>PVC: postgres-data-0"]
        P1["postgres-1<br/>REPLICA<br/>PVC: postgres-data-1"]
        P2["postgres-2<br/>REPLICA<br/>PVC: postgres-data-2"]

        P0 -->|replicates to| P1
        P0 -->|replicates to| P2
    end

    subgraph "Headless Service: postgres"
        DNS0[postgres-0.postgres<br/>stable DNS]
        DNS1[postgres-1.postgres<br/>stable DNS]
        DNS2[postgres-2.postgres<br/>stable DNS]
    end

    P0 --- DNS0
    P1 --- DNS1
    P2 --- DNS2

    WRITE[Writes] -->|always to| DNS0
    READ[Reads] -->|any replica| DNS1 & DNS2
```

**StatefulSet vs Deployment:**
| Feature | Deployment | StatefulSet |
|---|---|---|
| Pod names | Random (api-7d8f-xk2p) | Ordered (postgres-0, postgres-1) |
| Scale order | Any order | Ordered (0→1→2) |
| Storage | Shared or ephemeral | Each pod gets its own PVC |
| DNS | Service name only | Per-pod stable DNS |
| Use for | Stateless apps | Databases, Kafka, ZooKeeper |

---

## 2.10 — Storage: PV, PVC, StorageClass

```mermaid
graph TD
    subgraph "Admin creates"
        SC[StorageClass<br/>gp2 / premium-ssd]
        PV[PersistentVolume<br/>or auto-provisioned]
    end

    subgraph "Developer requests"
        PVC[PersistentVolumeClaim<br/>'I need 10Gi of fast storage']
    end

    subgraph "Pod uses"
        POD[Pod] -->|volumeMount| PVC
    end

    SC -->|dynamic provisioner| PV
    PVC -->|binds to| PV
    CLOUD[AWS EBS / GCP PD / Azure Disk] -.->|actual disk| PV

    style PV fill:#ffa94d
    style PVC fill:#4dabf7
    style SC fill:#69db7c
```

```yaml
# StorageClass (admin)
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: fast-ssd
provisioner: ebs.csi.aws.com
parameters:
  type: gp3
  iops: "3000"
reclaimPolicy: Retain           # keep disk when PVC deleted
volumeBindingMode: WaitForFirstConsumer

---
# PersistentVolumeClaim (developer)
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-data
spec:
  accessModes:
    - ReadWriteOnce             # single node read-write
  storageClassName: fast-ssd
  resources:
    requests:
      storage: 20Gi

---
# Access modes:
# ReadWriteOnce (RWO)  — single node, read/write (most block storage)
# ReadOnlyMany  (ROX)  — many nodes, read-only
# ReadWriteMany (RWX)  — many nodes, read/write (NFS, EFS, Ceph)
```

---

## 2.11 — Auto-Scaling

```mermaid
graph TD
    METRICS[Metrics Server<br/>CPU, Memory per pod]
    PROM[Prometheus + Adapter<br/>Custom metrics: req/s, queue depth]

    subgraph "HPA — Horizontal Pod Autoscaler"
        HPA[watches metrics] -->|scale out| DEP[Deployment replicas]
    end

    subgraph "VPA — Vertical Pod Autoscaler"
        VPA[watches usage] -->|adjust requests/limits| POD[Pod resources]
    end

    subgraph "CA — Cluster Autoscaler"
        CA[watches pending pods] -->|add/remove| NODE[Cluster Nodes]
    end

    METRICS --> HPA & VPA
    PROM --> HPA
    HPA & VPA -->|pod unschedulable| CA
```

```yaml
# Horizontal Pod Autoscaler
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: api-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: api-deployment
  minReplicas: 2
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70    # scale when avg CPU > 70%
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
    - type: Pods                    # custom metric
      pods:
        metric:
          name: requests_per_second
        target:
          type: AverageValue
          averageValue: "1000"
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300  # wait 5 min before scaling down
    scaleUp:
      stabilizationWindowSeconds: 30
```

---

## 2.12 — kubectl: Complete Command Reference

### Context & Cluster Management

```bash
# Contexts (which cluster are you talking to?)
kubectl config get-contexts
kubectl config current-context
kubectl config use-context my-cluster
kubectl config set-context --current --namespace=production

# Cluster info
kubectl cluster-info
kubectl get nodes
kubectl get nodes -o wide              # with IPs and OS
kubectl describe node worker-1
kubectl top nodes                      # CPU/memory usage
```

### Resource CRUD

```bash
# Apply (create or update) — preferred
kubectl apply -f deployment.yaml
kubectl apply -f ./k8s/                # all files in directory
kubectl apply -f https://raw.githubusercontent.com/.../file.yaml
kubectl apply -k ./kustomize/          # kustomize

# Create (fails if exists)
kubectl create deployment api --image=myapp:v1
kubectl create namespace production

# Get resources
kubectl get pods
kubectl get pods -n kube-system        # in specific namespace
kubectl get pods -A                    # all namespaces
kubectl get pods -o wide               # extra columns (node, IP)
kubectl get pods -o yaml               # full YAML
kubectl get pods -o json               # JSON
kubectl get pods -w                    # watch live
kubectl get all                        # pods, services, deployments
kubectl get pods --show-labels
kubectl get pods -l app=api            # filter by label
kubectl get pods --field-selector status.phase=Running

# Describe (detailed view + events)
kubectl describe pod api-7d8f-xk2p
kubectl describe deployment api-deployment
kubectl describe node worker-1
kubectl describe service api-service

# Delete
kubectl delete pod api-7d8f-xk2p
kubectl delete -f deployment.yaml
kubectl delete deployment api-deployment
kubectl delete pods --all -n staging
kubectl delete pods -l app=api
```

### Debugging & Troubleshooting

```bash
# Logs
kubectl logs api-7d8f-xk2p
kubectl logs api-7d8f-xk2p -c sidecar          # specific container
kubectl logs -f api-7d8f-xk2p                   # follow
kubectl logs --tail=100 api-7d8f-xk2p
kubectl logs --since=1h api-7d8f-xk2p
kubectl logs -l app=api --all-containers         # all pods with label

# Shell access
kubectl exec -it api-7d8f-xk2p -- bash
kubectl exec -it api-7d8f-xk2p -- sh           # if no bash
kubectl exec -it api-7d8f-xk2p -c sidecar -- sh

# Port forwarding (for local access to services)
kubectl port-forward pod/api-7d8f-xk2p 8080:3000
kubectl port-forward service/api-service 8080:80
kubectl port-forward deployment/api-deployment 8080:3000

# Copy files
kubectl cp api-7d8f-xk2p:/app/logs/app.log ./app.log
kubectl cp ./config.json api-7d8f-xk2p:/app/config.json

# Resource usage
kubectl top pods
kubectl top pods -l app=api
kubectl top nodes

# Events (crucial for debugging)
kubectl get events --sort-by='.lastTimestamp'
kubectl get events -n production

# Debug with ephemeral container (K8s 1.23+)
kubectl debug -it api-7d8f-xk2p --image=busybox --target=api
```

### Deployments & Rollouts

```bash
# Rollout management
kubectl rollout status deployment/api-deployment
kubectl rollout history deployment/api-deployment
kubectl rollout undo deployment/api-deployment          # revert 1 version
kubectl rollout undo deployment/api-deployment --to-revision=3
kubectl rollout restart deployment/api-deployment       # rolling restart
kubectl rollout pause deployment/api-deployment         # pause rollout
kubectl rollout resume deployment/api-deployment

# Scale
kubectl scale deployment api-deployment --replicas=5
kubectl autoscale deployment api-deployment --min=2 --max=10 --cpu-percent=70

# Update image
kubectl set image deployment/api-deployment api=myapp:v2.0.0
kubectl set env deployment/api-deployment NODE_ENV=production

# Patch (surgical update)
kubectl patch deployment api-deployment -p '{"spec":{"replicas":5}}'
kubectl patch pod api-7d8f-xk2p -p '{"spec":{"containers":[{"name":"api","image":"myapp:v2"}]}}'
```

### Namespaces

```bash
kubectl get namespaces
kubectl create namespace staging
kubectl delete namespace staging
kubectl get pods -n staging
kubectl get all -n staging

# Set default namespace for session
kubectl config set-context --current --namespace=production
```

### Labels & Selectors

```bash
# Label resources
kubectl label pod api-7d8f-xk2p version=v2
kubectl label node worker-1 disktype=ssd

# Remove label
kubectl label pod api-7d8f-xk2p version-

# Filter by label
kubectl get pods -l app=api
kubectl get pods -l 'app in (api,worker)'
kubectl get pods -l app=api,version=v2
```

### Useful One-Liners

```bash
# Get all pod images in a namespace
kubectl get pods -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{range .spec.containers[*]}{.image}{"\n"}{end}{end}'

# Delete all evicted pods
kubectl get pods -A | grep Evicted | awk '{print $2 " -n " $1}' | xargs -L1 kubectl delete pod

# Watch pod rollout
watch kubectl get pods -l app=api

# Get secret value decoded
kubectl get secret db-secret -o jsonpath='{.data.password}' | base64 -d

# Force delete stuck pod
kubectl delete pod api-7d8f-xk2p --force --grace-period=0

# Get all resource types
kubectl api-resources

# Explain any resource field
kubectl explain deployment.spec.strategy
kubectl explain pod.spec.containers.resources
```

---

## 2.13 — Namespaces: Multi-Tenancy

```mermaid
graph TB
    subgraph "Kubernetes Cluster"
        subgraph "namespace: kube-system"
            DNS[CoreDNS]
            PROXY[kube-proxy]
            MS[metrics-server]
        end

        subgraph "namespace: production"
            PROD_API[api deployment]
            PROD_DB[(postgres)]
            PROD_SVC[services]
        end

        subgraph "namespace: staging"
            STG_API[api deployment]
            STG_DB[(postgres)]
        end

        subgraph "namespace: monitoring"
            PROM[Prometheus]
            GRAF[Grafana]
        end
    end

    subgraph "Isolation"
        RQ[ResourceQuota<br/>per namespace]
        LR[LimitRange<br/>default limits]
        RBAC[RBAC<br/>who can do what]
    end

    production & staging -->|observed by| monitoring
    RQ & LR & RBAC -.-> production & staging
```

```yaml
# ResourceQuota — cap what a namespace can consume
apiVersion: v1
kind: ResourceQuota
metadata:
  name: production-quota
  namespace: production
spec:
  hard:
    requests.cpu: "10"
    requests.memory: 20Gi
    limits.cpu: "20"
    limits.memory: 40Gi
    pods: "50"
    services: "10"

---
# LimitRange — default limits for pods that don't specify
apiVersion: v1
kind: LimitRange
metadata:
  name: default-limits
  namespace: production
spec:
  limits:
    - type: Container
      default:
        memory: 256Mi
        cpu: 250m
      defaultRequest:
        memory: 128Mi
        cpu: 100m
```

---

## 2.14 — RBAC: Security & Access Control

```mermaid
graph LR
    subgraph "Subjects"
        USER[User<br/>human]
        SA[ServiceAccount<br/>pod identity]
        GROUP[Group<br/>set of users]
    end

    subgraph "Roles"
        R[Role<br/>namespace-scoped]
        CR[ClusterRole<br/>cluster-wide]
    end

    subgraph "Bindings"
        RB[RoleBinding]
        CRB[ClusterRoleBinding]
    end

    subgraph "Resources"
        PODS[pods]
        DEPLOY[deployments]
        SEC[secrets]
        NODES[nodes]
    end

    USER & SA & GROUP -->|bound by| RB & CRB
    RB --> R
    CRB --> CR
    R -->|grants verbs on| PODS & DEPLOY
    CR -->|grants verbs on| NODES & PODS & SEC
```

```yaml
# ServiceAccount for a pod
apiVersion: v1
kind: ServiceAccount
metadata:
  name: api-sa
  namespace: production

---
# Role — what can be done (namespace scoped)
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pod-reader
  namespace: production
rules:
  - apiGroups: [""]
    resources: ["pods", "pods/log"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["apps"]
    resources: ["deployments"]
    verbs: ["get", "list"]

---
# RoleBinding — who gets the role
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: read-pods
  namespace: production
subjects:
  - kind: ServiceAccount
    name: api-sa
    namespace: production
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
```

---

## 2.15 — DaemonSets, Jobs & CronJobs

```yaml
# DaemonSet — run one pod per node (logging agents, monitoring)
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: log-collector
spec:
  selector:
    matchLabels:
      app: log-collector
  template:
    metadata:
      labels:
        app: log-collector
    spec:
      containers:
        - name: fluentd
          image: fluent/fluentd
          volumeMounts:
            - name: varlog
              mountPath: /var/log
      volumes:
        - name: varlog
          hostPath:
            path: /var/log

---
# Job — run to completion
apiVersion: batch/v1
kind: Job
metadata:
  name: db-migration
spec:
  completions: 1
  parallelism: 1
  backoffLimit: 3           # retry 3 times on failure
  template:
    spec:
      restartPolicy: OnFailure
      containers:
        - name: migrate
          image: myapp:v2.0.0
          command: ["node", "dist/migrate.js"]

---
# CronJob — scheduled job
apiVersion: batch/v1
kind: CronJob
metadata:
  name: daily-report
spec:
  schedule: "0 2 * * *"    # 2 AM daily (cron syntax)
  concurrencyPolicy: Forbid # don't run if previous still running
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 1
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure
          containers:
            - name: reporter
              image: myapp:v2.0.0
              command: ["node", "dist/report.js"]
```

---

## 2.16 — Helm: Kubernetes Package Manager

```mermaid
graph LR
    subgraph "Helm Chart"
        CHART_YAML[Chart.yaml<br/>name, version, deps]
        VALUES[values.yaml<br/>defaults]
        TEMPLATES[templates/<br/>k8s YAML with Go templates]
    end

    subgraph "Deployment"
        OVERRIDE[my-values.yaml<br/>overrides]
        HELM_CMD[helm install / upgrade]
        K8S[Kubernetes API]
    end

    CHART_YAML & VALUES & TEMPLATES --> HELM_CMD
    OVERRIDE --> HELM_CMD
    HELM_CMD -->|renders & applies| K8S
```

```bash
# Add chart repository
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# Search charts
helm search repo postgres
helm search hub nginx

# Install
helm install my-postgres bitnami/postgresql
helm install my-postgres bitnami/postgresql \
  --set auth.postgresPassword=secretpass \
  --set primary.persistence.size=20Gi \
  -n production

# Install with values file
helm install my-app ./my-chart -f production-values.yaml

# Upgrade
helm upgrade my-postgres bitnami/postgresql \
  --set primary.persistence.size=50Gi

# Upgrade or install
helm upgrade --install my-app ./my-chart -f values.yaml

# Status & info
helm list
helm list -A                              # all namespaces
helm status my-postgres
helm get values my-postgres
helm get manifest my-postgres             # rendered k8s manifests

# Rollback
helm rollback my-postgres 1               # rollback to revision 1
helm history my-postgres                  # release history

# Uninstall
helm uninstall my-postgres

# Create new chart
helm create my-chart
helm lint my-chart                        # validate
helm template my-chart                    # render without deploying
helm install --dry-run my-release ./my-chart  # dry run
```

---

## 2.17 — Real-World System Design Patterns

### Pattern 1: The Full Web Application Stack

```mermaid
graph TD
    INTERNET[Internet]

    subgraph "Cloud"
        DNS[Route 53 / Cloud DNS]
        CDN[CloudFront / CDN<br/>Static assets]
        ALB[Application Load Balancer]

        subgraph "Kubernetes Cluster"
            IC[Ingress Controller<br/>nginx]

            subgraph "production namespace"
                subgraph "Frontend"
                    FE_DEP[frontend Deployment<br/>replicas: 3]
                    FE_SVC[frontend Service]
                end

                subgraph "API"
                    API_DEP[api Deployment<br/>replicas: 5]
                    API_SVC[api Service]
                    API_HPA[HPA: 5-20 pods]
                end

                subgraph "Workers"
                    WK_DEP[worker Deployment<br/>replicas: 3]
                end
            end

            subgraph "data namespace"
                PG_STS[postgres StatefulSet<br/>Primary + 2 Replicas]
                RD_STS[redis StatefulSet<br/>Sentinel mode]
                MQ_STS[rabbitmq StatefulSet]
            end

            subgraph "monitoring namespace"
                PROM[Prometheus]
                GRAF[Grafana]
                ALERT[AlertManager]
            end
        end

        S3[S3 / GCS<br/>Object Storage]
        RDS[RDS / Cloud DB<br/>Managed DB option]
    end

    INTERNET --> DNS
    DNS --> CDN
    DNS --> ALB
    ALB --> IC
    IC --> FE_SVC --> FE_DEP
    IC --> API_SVC --> API_DEP
    API_HPA -.->|scales| API_DEP
    API_DEP --> PG_STS
    API_DEP --> RD_STS
    API_DEP --> MQ_STS
    MQ_STS --> WK_DEP
    WK_DEP --> PG_STS
    PROM -->|scrapes metrics| API_DEP & PG_STS & WK_DEP
    GRAF -->|visualizes| PROM
    ALERT -->|alerts on| PROM
```

### Pattern 2: Zero-Downtime Deployment Flow

```mermaid
sequenceDiagram
    participant DEV as Developer
    participant CI as CI/CD Pipeline
    participant REG as Container Registry
    participant K8S as Kubernetes
    participant LB as Load Balancer

    DEV->>CI: git push v2.1.0
    CI->>CI: Build & Test
    CI->>REG: docker push myapp:v2.1.0
    CI->>K8S: kubectl set image deployment/api api=myapp:v2.1.0
    K8S->>K8S: Create new ReplicaSet (v2.1.0)
    K8S->>K8S: Start pod v2.1.0 #1
    K8S->>K8S: Wait for readinessProbe ✅
    K8S->>LB: Add v2.1.0 pod to endpoints
    K8S->>LB: Remove v1.0.0 pod from endpoints
    K8S->>K8S: Terminate v1.0.0 pod #1 (gracefully)
    Note over K8S: Repeat for each pod
    K8S->>CI: Rollout complete ✅
    Note over LB: Zero downtime achieved
```

### Pattern 3: Microservices Communication

```mermaid
graph TD
    GW[API Gateway<br/>Kong / Traefik]

    subgraph "Synchronous (HTTP/gRPC)"
        USER_SVC[User Service]
        ORDER_SVC[Order Service]
        PRODUCT_SVC[Product Service]
        PAYMENT_SVC[Payment Service]
    end

    subgraph "Asynchronous (Message Queue)"
        MQ[RabbitMQ / Kafka]
        NOTIF_SVC[Notification Service]
        ANALYTICS_SVC[Analytics Service]
        INVENTORY_SVC[Inventory Service]
    end

    subgraph "Data (each service owns its DB)"
        USER_DB[(User DB)]
        ORDER_DB[(Order DB)]
        PRODUCT_DB[(Product DB)]
    end

    GW --> USER_SVC & ORDER_SVC & PRODUCT_SVC
    ORDER_SVC -->|gRPC| USER_SVC
    ORDER_SVC -->|gRPC| PRODUCT_SVC
    ORDER_SVC -->|gRPC| PAYMENT_SVC
    ORDER_SVC -->|event: order.created| MQ
    MQ --> NOTIF_SVC & ANALYTICS_SVC & INVENTORY_SVC
    USER_SVC --- USER_DB
    ORDER_SVC --- ORDER_DB
    PRODUCT_SVC --- PRODUCT_DB
```

---

## 2.18 — Kubernetes Troubleshooting Guide

### Pod Not Starting — Decision Tree

```mermaid
flowchart TD
    START[Pod not running] --> STATUS{kubectl get pod<br/>What status?}

    STATUS -->|Pending| PEND{kubectl describe pod<br/>Check Events}
    PEND -->|Insufficient CPU/Memory| FIX1[Lower resource requests<br/>or add nodes]
    PEND -->|No nodes match nodeSelector| FIX2[Check node labels<br/>kubectl get nodes --show-labels]
    PEND -->|PVC not bound| FIX3[kubectl get pvc<br/>Check StorageClass]

    STATUS -->|ImagePullBackOff| IMG{Check image}
    IMG -->|Wrong image name| FIX4[Fix image tag in deployment]
    IMG -->|Private registry| FIX5[Add imagePullSecret to pod]
    IMG -->|Registry down| FIX6[Check registry connectivity]

    STATUS -->|CrashLoopBackOff| CRASH{kubectl logs pod<br/>Check logs}
    CRASH -->|App error| FIX7[Fix application bug]
    CRASH -->|Missing env var| FIX8[Add to ConfigMap/Secret]
    CRASH -->|Port conflict| FIX9[Fix containerPort]
    CRASH -->|OOMKilled| FIX10[Increase memory limit]

    STATUS -->|Running but not ready| PROBE{Readiness probe failing}
    PROBE --> FIX11[Check /health endpoint<br/>Check probe config]
```

### Common Issues Quick Reference

```bash
# Pod stuck in Pending
kubectl describe pod <pod-name>                   # look at Events section
kubectl get events --field-selector involvedObject.name=<pod-name>

# OOMKilled — out of memory
kubectl describe pod <pod-name> | grep -A5 "Last State"
# Increase memory limit in deployment

# ImagePullBackOff
kubectl describe pod <pod-name> | grep -A10 "Events"
# Check: image name, tag, registry auth

# CrashLoopBackOff
kubectl logs <pod-name> --previous                # logs from before crash

# Service not reachable
kubectl get endpoints api-service                 # are pods listed?
kubectl describe service api-service              # check selector matches labels

# DNS not resolving
kubectl exec -it debug-pod -- nslookup api-service
kubectl exec -it debug-pod -- nslookup api-service.production.svc.cluster.local

# Network connectivity
kubectl exec -it pod-a -- curl http://api-service:80/health
kubectl exec -it pod-a -- wget -O- http://api-service

# Resource pressure on node
kubectl top nodes
kubectl describe node worker-1 | grep -A10 "Allocated resources"
```

---

## 2.19 — Production Best Practices

### Security Checklist

```yaml
# Pod Security — production template
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 1001
    fsGroup: 2000
    seccompProfile:
      type: RuntimeDefault
  containers:
    - name: api
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities:
          drop: ["ALL"]
      volumeMounts:
        - name: tmp
          mountPath: /tmp              # allow writes to /tmp only
  volumes:
    - name: tmp
      emptyDir: {}
```

### Reliability Checklist

```yaml
# Always set:
# ✅ resources.requests and limits
# ✅ readinessProbe (controls traffic)
# ✅ livenessProbe (controls restarts)
# ✅ PodDisruptionBudget

apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: api-pdb
spec:
  minAvailable: 2           # always keep 2 pods during disruptions
  # OR:
  # maxUnavailable: 1
  selector:
    matchLabels:
      app: api

# ✅ Anti-affinity (spread pods across nodes)
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchLabels:
              app: api
          topologyKey: kubernetes.io/hostname
```

---

## 2.20 — The Full Architecture: Docker → K8s → Production

```mermaid
flowchart TD
    subgraph "Development"
        CODE[Code]
        DF[Dockerfile]
        DC[docker-compose.yml<br/>local dev]
        CODE & DF --> DC
        DC -->|dev environment| LOCAL[localhost:3000]
    end

    subgraph "CI/CD"
        GIT[Git Push / PR]
        BUILD[docker build<br/>multi-stage]
        TEST[docker run tests]
        SCAN[trivy security scan]
        PUSH_REG[docker push<br/>registry]
        GIT --> BUILD --> TEST --> SCAN --> PUSH_REG
    end

    subgraph "Kubernetes Cluster"
        subgraph "Control Plane"
            APISERV[API Server]
            SCHED[Scheduler]
            CTRLMGR[Controller Manager]
            ETCDSTORE[(etcd)]
        end

        subgraph "Workloads"
            DEPLOY[Deployments]
            STS_SET[StatefulSets]
            DS_SET[DaemonSets]
        end

        subgraph "Networking"
            SVCS[Services]
            INGRESSC[Ingress]
        end

        subgraph "Configuration"
            CFGMAP[ConfigMaps]
            SECR[Secrets]
            PVC_SET[PersistentVolumeClaims]
        end

        subgraph "Autoscaling"
            HPA_AUTO[HPA]
            CA_AUTO[Cluster Autoscaler]
        end
    end

    subgraph "Observability"
        LOGS[Log Aggregation<br/>ELK / Loki]
        METR[Metrics<br/>Prometheus + Grafana]
        TRACE[Tracing<br/>Jaeger / Zipkin]
        ALERT_SYS[Alerting<br/>PagerDuty / Slack]
    end

    CODE -.->|same image| GIT
    PUSH_REG -->|image ref| DEPLOY
    DEPLOY & STS_SET --> SVCS --> INGRESSC
    CFGMAP & SECR --> DEPLOY
    PVC_SET --> STS_SET
    HPA_AUTO -.->|scales| DEPLOY
    CA_AUTO -.->|scales| Cluster[Cluster Nodes]
    DEPLOY & STS_SET --> LOGS & METR & TRACE
    METR --> ALERT_SYS

    style APISERV fill:#4dabf7
    style ETCDSTORE fill:#ffa94d
    style INGRESSC fill:#69db7c
    style ALERT_SYS fill:#ff6b6b,color:#fff
```

---

## Quick Reference: Docker vs Kubernetes Equivalents

| Concept | Docker / Compose | Kubernetes |
|---|---|---|
| Run a container | `docker run` | Pod |
| Multiple replicas | `--scale` | Deployment / ReplicaSet |
| Networking | Docker network | Service + ClusterIP |
| External access | Port mapping `-p` | LoadBalancer Service / Ingress |
| Persistent data | Volume | PersistentVolumeClaim |
| Config injection | `--env-file` | ConfigMap |
| Sensitive config | `.env` file | Secret |
| Multi-service app | docker-compose.yml | Namespace + multiple YAMLs / Helm |
| Service discovery | container name | Service DNS name |
| Health check | `HEALTHCHECK` in Dockerfile | livenessProbe / readinessProbe |
| Auto-restart | `restart: always` | `restartPolicy` + Deployment |
| Auto-scaling | Manual `--scale` | HorizontalPodAutoscaler |
| Ordered stateful | Not built-in | StatefulSet |
| Run on all nodes | Not built-in | DaemonSet |
| Batch job | `docker run --rm` | Job |
| Scheduled job | External cron + docker | CronJob |
| Package manager | docker-compose | Helm |

---

## Learning Path

```mermaid
graph LR
    A[Docker Basics<br/>build, run, push] --> B[Dockerfile Mastery<br/>multi-stage, caching]
    B --> C[Docker Compose<br/>multi-service local dev]
    C --> D[K8s Concepts<br/>pods, services, deployments]
    D --> E[kubectl fluency<br/>get, describe, logs, exec]
    E --> F[Workload patterns<br/>StatefulSet, DaemonSet, Jobs]
    F --> G[Networking deep dive<br/>Ingress, NetworkPolicy]
    G --> H[Storage<br/>PV, PVC, StorageClass]
    H --> I[Helm<br/>packaging & releasing]
    I --> J[Observability<br/>Prometheus, Grafana, Loki]
    J --> K[GitOps<br/>ArgoCD / Flux]
    K --> L[Service Mesh<br/>Istio / Linkerd]
    L --> M[System Design<br/>ready to tackle anything]

    style A fill:#4dabf7
    style D fill:#69db7c
    style I fill:#ffa94d
    style M fill:#ff6b6b,color:#fff
```

---

*Built with ❤️ for engineers who want to understand the "why" behind the commands.*