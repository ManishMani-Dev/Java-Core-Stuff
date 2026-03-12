# KEDA — Kubernetes Event-Driven Autoscaling
## Complete Guide: RabbitMQ Scaling, Scale Speed, Cooldown & Scale-Down

---

## Table of Contents

1. [What is KEDA?](#1-what-is-keda)
2. [Core Architecture & Components](#2-core-architecture--components)
3. [How KEDA Works — End-to-End Flow](#3-how-keda-works--end-to-end-flow)
4. [RabbitMQ Scaler — Deep Dive](#4-rabbitmq-scaler--deep-dive)
5. [Scaling from 0 → N → 0 (Zero-Scale)](#5-scaling-from-0--n--0-zero-scale)
6. [Controlling Scale-Up Speed](#6-controlling-scale-up-speed)
7. [Cooldown Period — Preventing Thrashing](#7-cooldown-period--preventing-thrashing)
8. [Scale-Down Configuration](#8-scale-down-configuration)
9. [Full Production Example](#9-full-production-example)
10. [Tuning Reference Table](#10-tuning-reference-table)
11. [Common Pitfalls & Troubleshooting](#11-common-pitfalls--troubleshooting)

---

## 1. What is KEDA?

**KEDA (Kubernetes Event-Driven Autoscaling)** is a CNCF project that extends Kubernetes' native Horizontal Pod Autoscaler (HPA) to scale workloads based on **external event sources** — not just CPU/memory.

| Feature | Native HPA | KEDA |
|---|---|---|
| Scale based on CPU/Memory | ✅ | ✅ |
| Scale based on queue length | ❌ | ✅ |
| Scale to **zero** | ❌ | ✅ |
| Scale from **zero** | ❌ | ✅ |
| 50+ event sources (Kafka, RabbitMQ, etc.) | ❌ | ✅ |

KEDA does **not replace** the HPA — it **drives** it by feeding custom metrics.

---

## 2. Core Architecture & Components

```
┌──────────────────────────────────────────────────────────────┐
│                        KEDA Architecture                      │
│                                                              │
│  ┌─────────────────┐        ┌──────────────────────────────┐ │
│  │   ScaledObject  │──────▶│   KEDA Operator (Controller) │ │
│  │   (CRD)         │        │   - Watches ScaledObjects    │ │
│  └─────────────────┘        │   - Creates/manages HPA      │ │
│                              └──────────────┬───────────────┘ │
│  ┌─────────────────┐                        │                 │
│  │  ScaledJob      │        ┌───────────────▼───────────────┐ │
│  │   (CRD)         │        │   Metrics Adapter (Server)    │ │
│  └─────────────────┘        │   - Exposes custom metrics    │ │
│                              │   - Feeds data to HPA        │ │
│  ┌─────────────────┐        └──────────────┬───────────────┘ │
│  │  TriggerAuth    │                        │                 │
│  │   (CRD)         │        ┌───────────────▼───────────────┐ │
│  └─────────────────┘        │   Scalers (50+ integrations)  │ │
│                              │   - RabbitMQ, Kafka, SQS...   │ │
│  ┌─────────────────┐        └──────────────┬───────────────┘ │
│  │ClusterTriggerAuth│                       │                 │
│  │   (CRD)         │        ┌───────────────▼───────────────┐ │
│  └─────────────────┘        │   Kubernetes HPA              │ │
│                              │   - Actually scales the pods  │ │
│                              └───────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### 2.1 KEDA Operator

The **central controller** of KEDA. It runs as a Deployment in the `keda` namespace.

**Responsibilities:**
- Watches for `ScaledObject` and `ScaledJob` custom resources
- Creates and manages an HPA for each `ScaledObject`
- Handles zero-scale logic (activating/deactivating workloads)
- Configures the polling interval for each scaler

```bash
# Verify KEDA Operator is running
kubectl get pods -n keda

# NAME                                      READY   STATUS
# keda-operator-5d45d98d8c-xh8j9            1/1     Running
# keda-operator-metrics-apiserver-xxx-yyy   1/1     Running
```

---

### 2.2 Metrics Adapter (Metrics API Server)

This is a **custom Kubernetes API server** registered under the `external.metrics.k8s.io` API group.

**Responsibilities:**
- Queries scalers on each polling cycle
- Converts raw scaler data (e.g., queue depth) into a Kubernetes-compatible metric
- Serves these metrics to the HPA

```bash
# See external metrics being served
kubectl get --raw "/apis/external.metrics.k8s.io/v1beta1" | jq .
```

---

### 2.3 ScaledObject (CRD)

The **primary configuration resource** you create. It defines:
- What workload to scale (Deployment, StatefulSet, etc.)
- What event source to monitor (RabbitMQ, Kafka, etc.)
- Min/max replicas
- Scale speed, cooldown, and thresholds

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: my-scaledobject
spec:
  scaleTargetRef:
    name: my-deployment          # Target Deployment
  minReplicaCount: 0             # Can scale to zero
  maxReplicaCount: 20
  pollingInterval: 15            # Check every 15 seconds
  cooldownPeriod: 60             # Wait 60s before scaling down
  triggers:
    - type: rabbitmq
      metadata:
        queueName: my-queue
        value: "10"              # Target messages per pod
```

---

### 2.4 ScaledJob (CRD)

Similar to `ScaledObject` but designed for **short-lived Jobs** (batch workloads) instead of long-running Deployments.

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledJob
metadata:
  name: my-scaled-job
spec:
  jobTargetRef:
    template:
      spec:
        containers:
          - name: worker
            image: my-worker:latest
  triggers:
    - type: rabbitmq
      metadata:
        queueName: batch-queue
        value: "1"               # 1 job per message
```

---

### 2.5 TriggerAuthentication (CRD)

Stores **credentials** for connecting to external systems. Separates auth config from scaler config so the same credentials can be reused across multiple `ScaledObjects`.

```yaml
apiVersion: keda.sh/v1alpha1
kind: TriggerAuthentication
metadata:
  name: rabbitmq-auth
  namespace: default
spec:
  secretTargetRef:
    - parameter: host            # Maps to trigger's "host" param
      name: rabbitmq-secret      # Kubernetes Secret name
      key: connectionString      # Key inside the Secret
```

---

### 2.6 ClusterTriggerAuthentication (CRD)

Same as `TriggerAuthentication` but **cluster-scoped** — usable from any namespace.

```yaml
apiVersion: keda.sh/v1alpha1
kind: ClusterTriggerAuthentication
metadata:
  name: rabbitmq-cluster-auth
spec:
  secretTargetRef:
    - parameter: host
      name: rabbitmq-secret
      key: connectionString
```

---

### 2.7 Scalers

Scalers are the **plugins** inside KEDA that know how to query a specific external system. Each scaler:
1. Connects to its target (e.g., RabbitMQ Management API)
2. Returns the current metric value (e.g., queue depth = 450)
3. Returns the target metric value (e.g., 10 messages per pod)
4. The HPA divides: `ceil(450 / 10)` = **45 pods needed**

KEDA ships with 50+ built-in scalers: RabbitMQ, Kafka, AWS SQS, Azure Service Bus, Redis, Prometheus, Cron, and many more.

---

## 3. How KEDA Works — End-to-End Flow

```
Every pollingInterval seconds:

  RabbitMQ ──► Scaler ──► Metrics Adapter ──► HPA
     │             │              │              │
  queue_depth   raw metric    k8s metric      desired
   = 450        = 450         = 45.0          replicas
                              (450/10)         = 45
                                               │
                                         Kubernetes
                                         scales Deployment
                                         to 45 pods
```

**Step-by-step:**

1. **KEDA Operator** reads your `ScaledObject`
2. Every `pollingInterval` seconds, the **RabbitMQ Scaler** calls the RabbitMQ Management HTTP API
3. It fetches `messages_ready` from the target queue
4. The **Metrics Adapter** converts this to a Kubernetes external metric
5. The **HPA** computes: `desiredReplicas = ceil(currentMessages / targetMessagesPerPod)`
6. Kubernetes **scales the Deployment** up or down accordingly
7. If `messages_ready == 0` and `minReplicaCount == 0`, KEDA directly sets replicas to **0** (bypassing HPA minimum of 1)

---

## 4. RabbitMQ Scaler — Deep Dive

### 4.1 Authentication Modes

KEDA supports two connection modes for RabbitMQ:

**Mode A: AMQP (direct queue connection)**
```
amqp://user:password@rabbitmq-host:5672/vhost
```

**Mode B: HTTP Management API (recommended — more metrics)**
```
http://user:password@rabbitmq-host:15672/vhost
```

The HTTP mode gives access to more queue statistics.

---

### 4.2 Full RabbitMQ Scaler Parameters

```yaml
triggers:
  - type: rabbitmq
    metadata:
      # --- Connection ---
      protocol: http                    # "amqp" or "http" (default: auto-detect)
      hostFromEnv: RABBITMQ_URL         # Read host from env var (alternative to TriggerAuth)
      vhostName: "/"                    # Virtual host (default: "/")

      # --- Queue targeting ---
      queueName: my-queue              # Queue to monitor
      useRegex: "false"                # "true" = treat queueName as regex
      excludeUnacknowledged: "false"   # "true" = only count ready messages (not unacked)

      # --- Scaling threshold ---
      value: "10"                      # Target messages per pod replica (REQUIRED)

      # --- Activation threshold (scale from 0) ---
      activationValue: "0"             # Min messages before scaling from 0 (default: 0)

      # --- Mode (what to measure) ---
      mode: QueueLength                # "QueueLength" or "MessageRate" (msg/sec)

      # --- TLS (for AMQPS) ---
      tls: "false"
      ca: /path/to/ca.crt
      cert: /path/to/client.crt
      key: /path/to/client.key
    authenticationRef:
      name: rabbitmq-auth              # Reference to TriggerAuthentication
```

### 4.3 Mode: QueueLength vs MessageRate

| Mode | Metric | Use Case |
|---|---|---|
| `QueueLength` | `messages_ready` (backlog) | Workers that drain a queue |
| `MessageRate` | `message_stats.publish_rate` msg/sec | Stream processors, throughput-based |

**QueueLength example:** 300 messages in queue, `value: "10"` → scale to 30 pods  
**MessageRate example:** 100 msg/sec incoming, `value: "5"` → scale to 20 pods

---

## 5. Scaling from 0 → N → 0 (Zero-Scale)

This is KEDA's most powerful feature. Native HPA cannot scale below 1.

### 5.1 How Zero-Scale Works

```
Queue empty (messages = 0):
  KEDA Operator sees activationValue not exceeded
  → Bypasses HPA
  → Directly patches Deployment replicas to 0
  → HPA is paused/disabled

Queue has messages (messages > activationValue):
  KEDA Operator re-enables HPA
  → Patches Deployment replicas to at least 1
  → HPA takes over and scales to desired count
```

### 5.2 Configuration

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: rabbitmq-consumer
spec:
  scaleTargetRef:
    name: consumer-deployment
  minReplicaCount: 0             # ← Enable scale-to-zero
  maxReplicaCount: 50
  triggers:
    - type: rabbitmq
      metadata:
        queueName: work-queue
        value: "10"
        activationValue: "5"     # ← Only wake up when > 5 messages exist
                                 #   (avoids scaling for 1-2 stray messages)
```

### 5.3 activationValue — Preventing Flapping at Zero

Without `activationValue`, a single message arriving wakes up your pods. Set a minimum threshold:

```yaml
activationValue: "5"   # Only scale from 0 if queue has > 5 messages
```

---

## 6. Controlling Scale-Up Speed

KEDA uses Kubernetes HPA scaling policies to control **how fast** pods are added.

### 6.1 The `advanced` Block

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
spec:
  advanced:
    horizontalPodAutoscalerConfig:
      behavior:
        scaleUp:
          stabilizationWindowSeconds: 0    # React immediately to scale up
          selectPolicy: Max                # Pick the policy that adds the most pods
          policies:
            - type: Percent               # Add up to 100% of current pods per period
              value: 100
              periodSeconds: 15
            - type: Pods                  # OR add up to 10 pods per period
              value: 10
              periodSeconds: 15
```

### 6.2 Scale-Up Policy Types

**Type: `Pods`** — Add a fixed number of pods per time window
```yaml
policies:
  - type: Pods
    value: 4           # Add at most 4 pods every 60 seconds
    periodSeconds: 60
```

**Type: `Percent`** — Add a percentage of current replicas per time window
```yaml
policies:
  - type: Percent
    value: 50          # Add at most 50% of current pods every 30 seconds
    periodSeconds: 30
```

### 6.3 selectPolicy — How Multiple Policies Combine

| Value | Behavior |
|---|---|
| `Max` (default) | Use whichever policy allows the **most** pods (aggressive scale-up) |
| `Min` | Use whichever policy allows the **fewest** pods (conservative) |
| `Disabled` | Do NOT scale in this direction at all |

### 6.4 stabilizationWindowSeconds for Scale-Up

Controls how long to look back at metric history before scaling up.

```yaml
scaleUp:
  stabilizationWindowSeconds: 0    # React instantly (recommended for scale-up)
```

Setting this to `0` means: "if the metric says scale up RIGHT NOW, do it immediately."  
Setting to `120` means: "only scale up if the metric has been high for the last 2 minutes."

---

## 7. Cooldown Period — Preventing Thrashing

Cooldown prevents rapid scale-down after a spike — giving pods time to drain their work before being terminated.

### 7.1 KEDA-Level `cooldownPeriod`

This applies specifically to **scale-to-zero** transitions. It's the time KEDA waits after the queue empties before terminating the last pods.

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
spec:
  cooldownPeriod: 300       # Wait 5 minutes after queue empties before scaling to 0
  minReplicaCount: 0
```

**Timeline:**
```
T=0:00  Queue drains to 0 messages
T=0:00  KEDA notes queue is empty — starts cooldown timer
T=5:00  Cooldown expires → KEDA scales Deployment to 0 replicas
```

### 7.2 HPA-Level `stabilizationWindowSeconds` for Scale-Down

This is the HPA's own cooldown — it looks back over a window and uses the **highest** desired replica count seen, preventing premature scale-down.

```yaml
advanced:
  horizontalPodAutoscalerConfig:
    behavior:
      scaleDown:
        stabilizationWindowSeconds: 300   # Use highest replica count from last 5 min
```

**Why this matters:**
```
T=0:00   Queue spike: HPA wants 30 pods → scales to 30
T=0:30   Queue drops: HPA wants 5 pods
T=1:00   Queue drops: HPA wants 5 pods
...
T=5:00   Stabilization window: max over last 5 min = 30 → stays at 30
T=5:01   Window shifts: max is now lower → gradual scale-down begins
```

### 7.3 KEDA vs HPA Cooldown — Which to Use?

| Scenario | Use |
|---|---|
| Controlling scale-to-**zero** timing | `cooldownPeriod` on ScaledObject |
| Slowing down partial scale-down (e.g., 30→10) | `scaleDown.stabilizationWindowSeconds` in `advanced.behavior` |
| Both | Use both together |

---

## 8. Scale-Down Configuration

### 8.1 Scale-Down Policies

```yaml
advanced:
  horizontalPodAutoscalerConfig:
    behavior:
      scaleDown:
        stabilizationWindowSeconds: 180    # Look-back window
        selectPolicy: Min                  # Be conservative — remove fewest pods
        policies:
          - type: Percent
            value: 25                      # Remove at most 25% of pods per period
            periodSeconds: 60
          - type: Pods
            value: 5                       # OR at most 5 pods per period
            periodSeconds: 60
```

### 8.2 Gradual Scale-Down Example

To prevent sudden removal of many pods (which can cause dropped messages):

```yaml
scaleDown:
  stabilizationWindowSeconds: 300        # 5 min stabilization
  policies:
    - type: Percent
      value: 10                          # Remove only 10% of pods per minute
      periodSeconds: 60                  # Very gentle scale-down
```

**Effect:**
```
Current: 50 pods, work done
Min: 1 pod

Minute 1: 50 → 45  (10% = 5 pods removed)
Minute 2: 45 → 40
Minute 3: 40 → 36
...
Minute 15: ~12 → 11 → ... → 1
```

### 8.3 Preventing Scale-Down Entirely During Business Hours

Use the `Disabled` policy:

```yaml
scaleDown:
  selectPolicy: Disabled     # Never scale down (useful during peak hours)
```

Combine with a Cron scaler to re-enable at off-peak:

```yaml
triggers:
  - type: rabbitmq
    metadata:
      queueName: orders
      value: "10"
  - type: cron
    metadata:
      timezone: "Asia/Kolkata"
      start: "0 9 * * 1-5"      # 9 AM on weekdays
      end: "0 18 * * 1-5"       # 6 PM on weekdays
      desiredReplicas: "5"       # Keep at least 5 during business hours
```

---

## 9. Full Production Example

### 9.1 Directory Structure

```
k8s/
├── rabbitmq-secret.yaml
├── trigger-auth.yaml
├── consumer-deployment.yaml
└── scaledobject.yaml
```

### 9.2 Step 1 — Create the RabbitMQ Connection Secret

```yaml
# rabbitmq-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: rabbitmq-secret
  namespace: default
type: Opaque
stringData:
  host: "http://admin:password@rabbitmq-service:15672/vhost"
```

### 9.3 Step 2 — Create TriggerAuthentication

```yaml
# trigger-auth.yaml
apiVersion: keda.sh/v1alpha1
kind: TriggerAuthentication
metadata:
  name: rabbitmq-trigger-auth
  namespace: default
spec:
  secretTargetRef:
    - parameter: host
      name: rabbitmq-secret
      key: host
```

### 9.4 Step 3 — Deploy the Consumer

```yaml
# consumer-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-consumer
  namespace: default
spec:
  replicas: 0              # Start at 0 — KEDA will manage this
  selector:
    matchLabels:
      app: order-consumer
  template:
    metadata:
      labels:
        app: order-consumer
    spec:
      containers:
        - name: consumer
          image: my-org/order-consumer:1.0
          env:
            - name: RABBITMQ_URL
              valueFrom:
                secretKeyRef:
                  name: rabbitmq-secret
                  key: host
          resources:
            requests:
              cpu: "100m"
              memory: "128Mi"
            limits:
              cpu: "500m"
              memory: "256Mi"
      terminationGracePeriodSeconds: 60   # Give pods time to finish processing
```

### 9.5 Step 4 — Create the ScaledObject

```yaml
# scaledobject.yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: order-consumer-scaler
  namespace: default
spec:
  # ── Target ──────────────────────────────────────────────────
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-consumer

  # ── Replica Bounds ───────────────────────────────────────────
  minReplicaCount: 0             # Scale to zero when idle
  maxReplicaCount: 30            # Hard ceiling

  # ── Polling ──────────────────────────────────────────────────
  pollingInterval: 10            # Check RabbitMQ every 10 seconds

  # ── Zero-Scale Cooldown ──────────────────────────────────────
  cooldownPeriod: 120            # Wait 2 min before scaling to 0

  # ── Trigger ──────────────────────────────────────────────────
  triggers:
    - type: rabbitmq
      metadata:
        protocol: http
        queueName: order-queue
        mode: QueueLength
        value: "5"               # 1 pod per 5 messages in queue
        activationValue: "3"     # Only wake up when > 3 messages exist
      authenticationRef:
        name: rabbitmq-trigger-auth

  # ── Scale-Up and Scale-Down Behavior ─────────────────────────
  advanced:
    restoreToOriginalReplicaCount: false
    horizontalPodAutoscalerConfig:
      name: order-consumer-hpa  # Optional: give HPA a custom name
      behavior:

        # Scale-Up: Aggressive — respond fast to queue growth
        scaleUp:
          stabilizationWindowSeconds: 0    # React immediately
          selectPolicy: Max                # Use the more aggressive policy
          policies:
            - type: Percent
              value: 100                   # Can double pod count every 15s
              periodSeconds: 15
            - type: Pods
              value: 5                     # Or add 5 pods every 15s (whichever is larger)
              periodSeconds: 15

        # Scale-Down: Conservative — drain gracefully
        scaleDown:
          stabilizationWindowSeconds: 300  # Look back 5 min before deciding to scale down
          selectPolicy: Min                # Use the more conservative policy
          policies:
            - type: Percent
              value: 25                    # Remove at most 25% of pods per minute
              periodSeconds: 60
            - type: Pods
              value: 3                     # Or at most 3 pods per minute
              periodSeconds: 60
```

### 9.6 Apply Everything

```bash
kubectl apply -f rabbitmq-secret.yaml
kubectl apply -f trigger-auth.yaml
kubectl apply -f consumer-deployment.yaml
kubectl apply -f scaledobject.yaml
```

### 9.7 Verify

```bash
# Check ScaledObject status
kubectl get scaledobject order-consumer-scaler

# NAME                    SCALETARGETKIND   SCALETARGETNAME   MIN   MAX   READY   ACTIVE
# order-consumer-scaler   Deployment        order-consumer    0     30    True    False

# Check the HPA KEDA created
kubectl get hpa

# Watch scaling in real time
kubectl get pods -l app=order-consumer -w

# Check KEDA logs
kubectl logs -n keda -l app=keda-operator --tail=50
```

---

## 10. Tuning Reference Table

### ScaledObject Top-Level Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `pollingInterval` | int (seconds) | 30 | How often KEDA queries the scaler |
| `cooldownPeriod` | int (seconds) | 300 | Time to wait before scaling **to zero** |
| `minReplicaCount` | int | 0 | Minimum replicas (0 enables zero-scale) |
| `maxReplicaCount` | int | 100 | Maximum replicas |
| `idleReplicaCount` | int | - | Alternative to 0; sets "idle" pod count |
| `fallback.failureThreshold` | int | - | Failures before using fallback replicas |
| `fallback.replicas` | int | - | Replica count if scaler fails |

### RabbitMQ Trigger Parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `queueName` | ✅ | - | Name of the queue to monitor |
| `value` | ✅ | - | Target messages per pod |
| `activationValue` | ❌ | `"0"` | Messages needed to activate from 0 |
| `protocol` | ❌ | auto | `"amqp"` or `"http"` |
| `mode` | ❌ | `QueueLength` | `"QueueLength"` or `"MessageRate"` |
| `vhostName` | ❌ | `"/"` | RabbitMQ virtual host |
| `useRegex` | ❌ | `"false"` | Regex-match multiple queues |
| `excludeUnacknowledged` | ❌ | `"false"` | Exclude in-flight messages |

### HPA Behavior Fields

| Field | Scope | Description |
|---|---|---|
| `stabilizationWindowSeconds` | scaleUp / scaleDown | Look-back window for metric history |
| `selectPolicy` | scaleUp / scaleDown | `Max`, `Min`, or `Disabled` |
| `policies[].type` | scaleUp / scaleDown | `Pods` or `Percent` |
| `policies[].value` | scaleUp / scaleDown | Numeric limit per period |
| `policies[].periodSeconds` | scaleUp / scaleDown | Time window for the policy |

---

## 11. Common Pitfalls & Troubleshooting

### Problem: Pods not scaling up

```bash
# Check ScaledObject conditions
kubectl describe scaledobject order-consumer-scaler

# Check if HPA sees the metric
kubectl describe hpa keda-hpa-order-consumer-scaler

# Check Metrics Adapter
kubectl get --raw "/apis/external.metrics.k8s.io/v1beta1/namespaces/default/s0-rabbitmq-order-queue"
```

**Common causes:**
- Wrong RabbitMQ connection string
- TriggerAuthentication referencing wrong Secret key
- `activationValue` too high — queue never crosses the threshold
- RabbitMQ management plugin not enabled

### Problem: Pods scaling down too aggressively (message loss)

**Fix:** Increase `stabilizationWindowSeconds` and reduce `Percent` in scale-down policy:
```yaml
scaleDown:
  stabilizationWindowSeconds: 600    # 10 minutes
  policies:
    - type: Percent
      value: 10                      # Very slow drain
      periodSeconds: 60
```

Also ensure your consumer handles `SIGTERM` gracefully:
```python
import signal

def graceful_shutdown(sig, frame):
    # Finish processing current message, then exit
    consumer.stop()
    sys.exit(0)

signal.signal(signal.SIGTERM, graceful_shutdown)
```

### Problem: Constant scale-up/scale-down flapping

**Fix:** Set `stabilizationWindowSeconds` for both directions and tune `activationValue`:
```yaml
scaleUp:
  stabilizationWindowSeconds: 30    # Don't react to momentary spikes
scaleDown:
  stabilizationWindowSeconds: 300   # Don't scale down too eagerly
```

### Problem: ScaledObject shows READY=False

```bash
kubectl describe scaledobject order-consumer-scaler | grep -A5 "Conditions"
```
Check that:
- The target Deployment exists
- The trigger can authenticate to RabbitMQ
- The queue name is correct

### Problem: KEDA not scaling to zero even when queue is empty

- Ensure `minReplicaCount: 0` is set
- Check that `cooldownPeriod` has elapsed
- Verify no other trigger is keeping the scaler active

---

## Summary Flow Diagram

```
Messages arrive in RabbitMQ queue
        │
        ▼
KEDA polls every pollingInterval (e.g., 10s)
        │
        ├─ messages == 0 AND replicas > 0
        │         │
        │    cooldownPeriod timer starts
        │         │
        │    timer expires → scale to 0 replicas
        │
        ├─ messages > 0 AND replicas == 0
        │         │
        │    activationValue exceeded?
        │         ├─ No  → stay at 0
        │         └─ Yes → scale to 1, hand off to HPA
        │
        └─ messages > 0 AND replicas >= 1
                  │
             HPA computes:
             desiredReplicas = ceil(messages / value)
                  │
             Apply scaleUp policy (fast) ──► if growing
             Apply scaleDown policy (slow) ─► if shrinking
                  │
             Kubernetes adjusts pod count
```

---

*Documentation generated for KEDA v2.x — compatible with Kubernetes 1.24+*