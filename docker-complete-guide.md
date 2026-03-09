# Complete Docker Study Guide: From Basics to PostgreSQL Scaling

## Table of Contents

1. [Docker Fundamentals](#1-docker-fundamentals)
2. [Dockerfile Deep Dive](#2-dockerfile-deep-dive)
3. [Docker Images and Containers](#3-docker-images-and-containers)
4. [Docker Compose](#4-docker-compose)
5. [Docker Networks](#5-docker-networks)
6. [Docker Volumes](#6-docker-volumes)
7. [Multi-Stage Builds](#7-multi-stage-builds)
8. [Docker Security](#8-docker-security)
9. [Docker Orchestration Basics](#9-docker-orchestration-basics)
10. [PostgreSQL with Docker](#10-postgresql-with-docker)
11. [PostgreSQL Partitioning and Scaling](#11-postgresql-partitioning-and-scaling)
12. [Production Best Practices](#12-production-best-practices)

---

## 1. Docker Fundamentals

### What is Docker?

Docker is a containerization platform that packages applications and their dependencies into lightweight, portable containers. Unlike virtual machines, containers share the host OS kernel, making them more efficient.

**Key Concepts:**
- **Image**: A read-only template used to create containers
- **Container**: A running instance of an image
- **Dockerfile**: A text file with instructions to build an image
- **Registry**: A storage and distribution system for Docker images

### Docker Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Docker CLI    │────│   Docker Daemon  │────│   Registries    │
│   (client)      │    │   (dockerd)      │    │   (Docker Hub)  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │
                              │
                    ┌─────────────────┐
                    │   Containers    │
                    │   Images        │
                    │   Networks      │
                    │   Volumes       │
                    └─────────────────┘
```

### Essential Docker Commands

```bash
# Image operations
docker pull <image>          # Download image
docker images               # List images
docker rmi <image>          # Remove image
docker build -t <name> .    # Build image from Dockerfile

# Container operations
docker run <image>          # Create and start container
docker ps                  # List running containers
docker ps -a              # List all containers
docker stop <container>    # Stop container
docker start <container>   # Start stopped container
docker rm <container>      # Remove container
docker exec -it <container> bash  # Execute command in container

# System operations
docker system prune        # Remove unused data
docker logs <container>    # View container logs
docker inspect <container> # Detailed container info
```

---

## 2. Dockerfile Deep Dive

### Dockerfile Structure

A Dockerfile is a series of instructions that Docker uses to build an image. Each instruction creates a new layer.

### Essential Instructions

#### FROM
```dockerfile
# Base image - always first instruction
FROM node:18-alpine
FROM ubuntu:22.04
FROM scratch  # Empty base image
```

#### WORKDIR
```dockerfile
# Sets working directory for subsequent instructions
WORKDIR /app
```

#### COPY vs ADD
```dockerfile
# COPY - simple file/directory copying (preferred)
COPY package.json ./
COPY src/ ./src/

# ADD - has additional features (auto-extraction, URL support)
ADD https://example.com/file.tar.gz /tmp/
ADD archive.tar.gz /opt/  # Auto-extracts
```

#### RUN
```dockerfile
# Execute commands during build
RUN apt-get update && apt-get install -y \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# Use && to chain commands and reduce layers
RUN npm install && npm run build
```

#### ENV
```dockerfile
# Set environment variables
ENV NODE_ENV=production
ENV PORT=3000
ENV DATABASE_URL=postgresql://user:pass@db:5432/mydb
```

#### EXPOSE
```dockerfile
# Documents which ports the container listens on
EXPOSE 3000
EXPOSE 5432
```

#### CMD vs ENTRYPOINT
```dockerfile
# CMD - default command (can be overridden)
CMD ["node", "server.js"]
CMD ["npm", "start"]

# ENTRYPOINT - always executes (cannot be overridden)
ENTRYPOINT ["docker-entrypoint.sh"]

# Combined usage
ENTRYPOINT ["node"]
CMD ["server.js"]  # Default argument to node
```

### Complete Example Dockerfile

```dockerfile
# Multi-stage build example
FROM node:18-alpine AS builder
WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production

# Copy source code
COPY . .

# Build application
RUN npm run build

# Production stage
FROM node:18-alpine AS production
WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 -S nodejs
RUN adduser -S nextjs -u 1001

# Copy built application
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./package.json

# Change ownership
RUN chown -R nextjs:nodejs /app
USER nextjs

# Expose port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:3000/health || exit 1

# Start application
CMD ["node", "dist/server.js"]
```

### Dockerfile Best Practices

1. **Use specific tags**: `FROM node:18-alpine` instead of `FROM node:latest`
2. **Minimize layers**: Chain RUN commands with `&&`
3. **Leverage build cache**: Place frequently changing instructions last
4. **Use .dockerignore**: Exclude unnecessary files
5. **Run as non-root user**: Create and use dedicated user
6. **Use multi-stage builds**: Separate build and runtime environments

### .dockerignore Example

```dockerignore
node_modules
npm-debug.log
Dockerfile*
docker-compose*
.git
.gitignore
README.md
.env
.nyc_output
coverage
.nyc_output
.coverage
.DS_Store
*.log
```

---

## 3. Docker Images and Containers

### Image Layers

Docker images are built using a layered file system. Each instruction in a Dockerfile creates a new layer.

```
┌─────────────────────┐ ← CMD ["node", "app.js"]
├─────────────────────┤ ← COPY . .
├─────────────────────┤ ← RUN npm install
├─────────────────────┤ ← COPY package.json .
├─────────────────────┤ ← WORKDIR /app
└─────────────────────┘ ← FROM node:18-alpine
```

### Image Management

```bash
# Build with different tags
docker build -t myapp:latest -t myapp:v1.0 .

# Build with build arguments
docker build --build-arg NODE_ENV=production -t myapp .

# View image layers
docker history myapp:latest

# Image inspection
docker inspect myapp:latest

# Save and load images
docker save myapp:latest > myapp.tar
docker load < myapp.tar

# Tag and push to registry
docker tag myapp:latest myregistry.com/myapp:latest
docker push myregistry.com/myapp:latest
```

### Container Lifecycle

```bash
# Run container with various options
docker run -d \
  --name mycontainer \
  -p 3000:3000 \
  -e NODE_ENV=production \
  -v /host/data:/container/data \
  --restart unless-stopped \
  myapp:latest

# Container states: Created → Running → Stopped → Removed
docker create myapp:latest    # Create but don't start
docker start mycontainer      # Start created container
docker pause mycontainer      # Pause running container
docker unpause mycontainer    # Unpause container
docker stop mycontainer       # Stop container gracefully
docker kill mycontainer       # Force stop container
docker rm mycontainer         # Remove stopped container
```

### Resource Management

```bash
# Limit CPU and memory
docker run -d \
  --name resource-limited \
  --cpus="1.5" \
  --memory="1g" \
  --memory-swap="2g" \
  myapp:latest

# Monitor resource usage
docker stats mycontainer

# Update resource limits
docker update --memory="2g" mycontainer
```

---

## 4. Docker Compose

Docker Compose is a tool for defining and running multi-container Docker applications using YAML files.

### Basic docker-compose.yml Structure

```yaml
version: '3.8'

services:
  web:
    build: .
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=production
    depends_on:
      - db
      - redis
    
  db:
    image: postgres:15
    environment:
      POSTGRES_DB: myapp
      POSTGRES_USER: user
      POSTGRES_PASSWORD: password
    volumes:
      - postgres_data:/var/lib/postgresql/data
    
  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data

volumes:
  postgres_data:
  redis_data:
```

### Advanced Compose Features

#### Environment Variables

```yaml
# .env file
NODE_ENV=production
DB_PASSWORD=secretpassword
REDIS_URL=redis://redis:6379

# docker-compose.yml
version: '3.8'
services:
  web:
    build: .
    environment:
      - NODE_ENV=${NODE_ENV}
      - DATABASE_URL=postgresql://user:${DB_PASSWORD}@db:5432/myapp
    env_file:
      - .env
```

#### Multiple Compose Files

```yaml
# docker-compose.yml (base)
version: '3.8'
services:
  web:
    build: .
    ports:
      - "3000:3000"

# docker-compose.override.yml (development overrides)
version: '3.8'
services:
  web:
    volumes:
      - .:/app
    environment:
      - NODE_ENV=development

# docker-compose.prod.yml (production overrides)
version: '3.8'
services:
  web:
    image: myapp:latest
    environment:
      - NODE_ENV=production
    deploy:
      replicas: 3
```

#### Health Checks

```yaml
services:
  web:
    build: .
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3000/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
  
  db:
    image: postgres:15
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U user -d myapp"]
      interval: 10s
      timeout: 5s
      retries: 5
```

#### Scaling and Load Balancing

```yaml
version: '3.8'
services:
  web:
    build: .
    ports:
      - "3000-3005:3000"
    deploy:
      replicas: 3
    
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
    depends_on:
      - web
```

### Essential Compose Commands

```bash
# Start services
docker-compose up                    # Start in foreground
docker-compose up -d                 # Start in background
docker-compose up --build            # Rebuild images before starting

# Stop services
docker-compose down                  # Stop and remove containers
docker-compose down -v               # Also remove volumes
docker-compose stop                  # Stop without removing

# Service management
docker-compose restart web           # Restart specific service
docker-compose logs web              # View logs for service
docker-compose exec web bash         # Execute command in service

# Scaling
docker-compose up --scale web=3      # Scale web service to 3 instances

# Multiple compose files
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up
```

---

## 5. Docker Networks

Docker provides several networking options to connect containers.

### Network Types

#### Bridge Network (Default)
```bash
# Create custom bridge network
docker network create mynetwork

# Run containers on custom network
docker run -d --name web --network mynetwork nginx
docker run -d --name db --network mynetwork postgres

# Containers can communicate using container names as hostnames
```

#### Host Network
```bash
# Container uses host's network stack
docker run -d --network host nginx
# Container directly uses host's IP and ports
```

#### None Network
```bash
# Container has no network access
docker run -d --network none myapp
```

### Network Management

```bash
# List networks
docker network ls

# Inspect network
docker network inspect bridge

# Create networks with custom settings
docker network create \
  --driver bridge \
  --subnet=172.20.0.0/16 \
  --ip-range=172.20.240.0/20 \
  mynetwork

# Connect/disconnect containers
docker network connect mynetwork container1
docker network disconnect mynetwork container1

# Remove network
docker network rm mynetwork
```

### Compose Network Configuration

```yaml
version: '3.8'

services:
  web:
    build: .
    networks:
      - frontend
      - backend
    
  db:
    image: postgres:15
    networks:
      - backend
    
  nginx:
    image: nginx
    networks:
      - frontend
    ports:
      - "80:80"

networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
    internal: true  # No external access
```

### Advanced Network Features

#### Custom DNS and Aliases

```yaml
services:
  web:
    image: nginx
    networks:
      mynetwork:
        aliases:
          - webapp
          - frontend
        ipv4_address: 172.20.0.10

networks:
  mynetwork:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/24
```

#### External Networks

```yaml
# Use existing network
services:
  web:
    image: nginx
    networks:
      - existing_network

networks:
  existing_network:
    external: true
```

---

## 6. Docker Volumes

Volumes provide persistent storage for containers and enable data sharing.

### Volume Types

#### Named Volumes
```bash
# Create named volume
docker volume create mydata

# Use in container
docker run -v mydata:/app/data myapp

# List and inspect volumes
docker volume ls
docker volume inspect mydata

# Remove volume
docker volume rm mydata
```

#### Bind Mounts
```bash
# Mount host directory
docker run -v /host/path:/container/path myapp

# Mount current directory
docker run -v $(pwd):/app myapp
```

#### tmpfs Mounts
```bash
# Mount temporary filesystem (Linux only)
docker run --tmpfs /app/temp myapp
```

### Compose Volume Configuration

```yaml
version: '3.8'

services:
  web:
    build: .
    volumes:
      # Named volume
      - app_data:/app/data
      # Bind mount
      - ./config:/app/config:ro  # Read-only
      # Anonymous volume
      - /app/node_modules
  
  db:
    image: postgres:15
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql:ro

volumes:
  app_data:
    driver: local
  postgres_data:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /host/postgres/data
```

### Volume Management Best Practices

```bash
# Backup volume
docker run --rm \
  -v mydata:/data \
  -v $(pwd):/backup \
  ubuntu tar czf /backup/backup.tar.gz -C /data .

# Restore volume
docker run --rm \
  -v mydata:/data \
  -v $(pwd):/backup \
  ubuntu tar xzf /backup/backup.tar.gz -C /data

# Copy data between containers
docker run --rm \
  -v source_volume:/source \
  -v target_volume:/target \
  ubuntu cp -a /source/. /target/
```

---

## 7. Multi-Stage Builds

Multi-stage builds allow you to create smaller, more secure production images.

### Basic Multi-Stage Example

```dockerfile
# Build stage
FROM node:18 AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production stage
FROM node:18-alpine AS production
WORKDIR /app
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nextjs -u 1001
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY package*.json ./
USER nextjs
EXPOSE 3000
CMD ["node", "dist/server.js"]
```

### Advanced Multi-Stage Patterns

#### Development vs Production

```dockerfile
# Base stage
FROM node:18-alpine AS base
WORKDIR /app
COPY package*.json ./

# Development stage
FROM base AS development
RUN npm install
COPY . .
EXPOSE 3000
CMD ["npm", "run", "dev"]

# Build stage
FROM base AS build
RUN npm ci --only=production
COPY . .
RUN npm run build

# Production stage
FROM node:18-alpine AS production
WORKDIR /app
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nextjs -u 1001
COPY --from=build /app/dist ./dist
COPY --from=build /app/node_modules ./node_modules
USER nextjs
EXPOSE 3000
CMD ["node", "dist/server.js"]
```

#### Copying from External Images

```dockerfile
FROM alpine AS base

# Copy from specific image
COPY --from=nginx:alpine /etc/nginx/nginx.conf /etc/nginx/
COPY --from=node:18-alpine /usr/local/bin/node /usr/local/bin/

# Copy from previous build
COPY --from=builder /app/build /usr/share/nginx/html
```

---

## 8. Docker Security

### Security Best Practices

#### Use Non-Root Users

```dockerfile
FROM ubuntu:22.04

# Create user and group
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Create app directory with proper ownership
RUN mkdir /app && chown appuser:appuser /app
WORKDIR /app

# Switch to non-root user
USER appuser

# Install application
COPY --chown=appuser:appuser . .
```

#### Minimize Attack Surface

```dockerfile
# Use distroless or minimal base images
FROM gcr.io/distroless/node:18

# Multi-stage build to exclude build tools
FROM node:18 AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production

FROM gcr.io/distroless/node:18
COPY --from=builder /app/node_modules ./node_modules
COPY . .
EXPOSE 3000
CMD ["server.js"]
```

#### Resource Limits

```yaml
# docker-compose.yml
version: '3.8'
services:
  web:
    build: .
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M
        reservations:
          memory: 256M
    security_opt:
      - no-new-privileges:true
    read_only: true
    tmpfs:
      - /tmp
      - /var/run
```

#### Secrets Management

```yaml
version: '3.8'

services:
  web:
    build: .
    secrets:
      - db_password
      - api_key
    environment:
      - DB_PASSWORD_FILE=/run/secrets/db_password

secrets:
  db_password:
    file: ./secrets/db_password.txt
  api_key:
    external: true
```

### Security Scanning

```bash
# Scan image for vulnerabilities
docker scan myapp:latest

# Use security linting tools
hadolint Dockerfile

# Scan running containers
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  aquasec/trivy image myapp:latest
```

---

## 9. Docker Orchestration Basics

### Docker Swarm

Docker Swarm provides native clustering and orchestration.

#### Initialize Swarm

```bash
# Initialize swarm
docker swarm init --advertise-addr 192.168.1.100

# Join workers
docker swarm join --token <token> 192.168.1.100:2377

# List nodes
docker node ls
```

#### Deploy Stack

```yaml
# docker-stack.yml
version: '3.8'

services:
  web:
    image: myapp:latest
    ports:
      - "80:3000"
    deploy:
      replicas: 3
      placement:
        constraints:
          - node.role == worker
      resources:
        limits:
          memory: 512M
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
    networks:
      - webnet

  db:
    image: postgres:15
    environment:
      POSTGRES_DB: myapp
      POSTGRES_USER: user
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
    volumes:
      - postgres_data:/var/lib/postgresql/data
    deploy:
      placement:
        constraints:
          - node.role == manager
    secrets:
      - db_password
    networks:
      - dbnet

networks:
  webnet:
    driver: overlay
  dbnet:
    driver: overlay

volumes:
  postgres_data:
    driver: local

secrets:
  db_password:
    external: true
```

```bash
# Deploy stack
docker stack deploy -c docker-stack.yml myapp

# Manage stack
docker stack ls
docker stack services myapp
docker service scale myapp_web=5
```

---

## 10. PostgreSQL with Docker

### Basic PostgreSQL Setup

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    container_name: postgres_db
    environment:
      POSTGRES_DB: myapp
      POSTGRES_USER: dbuser
      POSTGRES_PASSWORD: dbpass
      PGDATA: /var/lib/postgresql/data/pgdata
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init-scripts:/docker-entrypoint-initdb.d
    command: >
      postgres
      -c shared_preload_libraries=pg_stat_statements
      -c pg_stat_statements.track=all
      -c max_connections=200
      -c shared_buffers=256MB
      -c work_mem=4MB

  pgadmin:
    image: dpage/pgadmin4:latest
    container_name: pgadmin
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@admin.com
      PGADMIN_DEFAULT_PASSWORD: admin
    ports:
      - "8080:80"
    volumes:
      - pgadmin_data:/var/lib/pgadmin
    depends_on:
      - postgres

volumes:
  postgres_data:
  pgadmin_data:
```

### Custom PostgreSQL Image

```dockerfile
FROM postgres:15

# Install additional extensions
RUN apt-get update && apt-get install -y \
    postgresql-contrib \
    postgresql-15-pg-partman \
    && rm -rf /var/lib/apt/lists/*

# Copy custom configuration
COPY postgresql.conf /etc/postgresql/postgresql.conf
COPY pg_hba.conf /etc/postgresql/pg_hba.conf

# Copy initialization scripts
COPY init-scripts/ /docker-entrypoint-initdb.d/

# Custom entrypoint
COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["postgres", "-c", "config_file=/etc/postgresql/postgresql.conf"]
```

### PostgreSQL Configuration

```bash
# postgresql.conf
listen_addresses = '*'
max_connections = 200
shared_buffers = 256MB
work_mem = 4MB
maintenance_work_mem = 64MB
effective_cache_size = 1GB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
```

---

## 11. PostgreSQL Partitioning and Scaling

### Table Partitioning Setup

```sql
-- init-scripts/01-create-partitioned-tables.sql

-- Create partitioned table by date range
CREATE TABLE orders (
    id SERIAL,
    order_date DATE NOT NULL,
    customer_id INTEGER,
    amount DECIMAL(10,2),
    PRIMARY KEY (id, order_date)
) PARTITION BY RANGE (order_date);

-- Create partitions
CREATE TABLE orders_2024_q1 PARTITION OF orders
    FOR VALUES FROM ('2024-01-01') TO ('2024-04-01');

CREATE TABLE orders_2024_q2 PARTITION OF orders
    FOR VALUES FROM ('2024-04-01') TO ('2024-07-01');

CREATE TABLE orders_2024_q3 PARTITION OF orders
    FOR VALUES FROM ('2024-07-01') TO ('2024-10-01');

CREATE TABLE orders_2024_q4 PARTITION OF orders
    FOR VALUES FROM ('2024-10-01') TO ('2025-01-01');

-- Create indexes on partitions
CREATE INDEX idx_orders_2024_q1_customer ON orders_2024_q1 (customer_id);
CREATE INDEX idx_orders_2024_q2_customer ON orders_2024_q2 (customer_id);

-- Hash partitioning example
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) PARTITION BY HASH (id);

CREATE TABLE users_p0 PARTITION OF users
    FOR VALUES WITH (modulus 4, remainder 0);
CREATE TABLE users_p1 PARTITION OF users
    FOR VALUES WITH (modulus 4, remainder 1);
CREATE TABLE users_p2 PARTITION OF users
    FOR VALUES WITH (modulus 4, remainder 2);
CREATE TABLE users_p3 PARTITION OF users
    FOR VALUES WITH (modulus 4, remainder 3);
```

### Horizontal Scaling with Multiple PostgreSQL Instances

```yaml
version: '3.8'

services:
  # Master PostgreSQL
  postgres-master:
    image: postgres:15
    container_name: postgres_master
    environment:
      POSTGRES_DB: myapp
      POSTGRES_USER: replicator
      POSTGRES_PASSWORD: replicatorpass
      POSTGRES_REPLICATION_MODE: master
      POSTGRES_REPLICATION_USER: replicator
      POSTGRES_REPLICATION_PASSWORD: replicatorpass
    ports:
      - "5432:5432"
    volumes:
      - postgres_master_data:/var/lib/postgresql/data
      - ./master-config:/etc/postgresql
    command: >
      postgres
      -c wal_level=replica
      -c max_wal_senders=3
      -c max_replication_slots=3
      -c hot_standby=on

  # Read Replica 1
  postgres-replica1:
    image: postgres:15
    container_name: postgres_replica1
    environment:
      POSTGRES_MASTER_SERVICE: postgres-master
      POSTGRES_REPLICATION_MODE: slave
      POSTGRES_REPLICATION_USER: replicator
      POSTGRES_REPLICATION_PASSWORD: replicatorpass
      PGUSER: postgres
    ports:
      - "5433:5432"
    volumes:
      - postgres_replica1_data:/var/lib/postgresql/data
    depends_on:
      - postgres-master

  # Read Replica 2
  postgres-replica2:
    image: postgres:15
    container_name: postgres_replica2
    environment:
      POSTGRES_MASTER_SERVICE: postgres-master
      POSTGRES_REPLICATION_MODE: slave
      POSTGRES_REPLICATION_USER: replicator
      POSTGRES_REPLICATION_PASSWORD: replicatorpass
      PGUSER: postgres
    ports:
      - "5434:5432"
    volumes:
      - postgres_replica2_data:/var/lib/postgresql/data
    depends_on:
      - postgres-master

  # Connection Pooler
  pgbouncer:
    image: pgbouncer/pgbouncer:latest
    container_name: pgbouncer
    environment:
      DATABASES_HOST: postgres-master
      DATABASES_PORT: 5432
      DATABASES_USER: dbuser
      DATABASES_PASSWORD: dbpass
      DATABASES_DBNAME: myapp
      POOL_MODE: transaction
      MAX_CLIENT_CONN: 100
      DEFAULT_POOL_SIZE: 25
    ports:
      - "6432:5432"
    depends_on:
      - postgres-master

  # Load Balancer for Read Queries
  haproxy:
    image: haproxy:2.8
    container_name: postgres_lb
    ports:
      - "5435:5432"  # Read-only endpoint
    volumes:
      - ./haproxy.cfg:/usr/local/etc/haproxy/haproxy.cfg:ro
    depends_on:
      - postgres-replica1
      - postgres-replica2

volumes:
  postgres_master_data:
  postgres_replica1_data:
  postgres_replica2_data:
```

### HAProxy Configuration for Read Replicas

```bash
# haproxy.cfg
global
    daemon

defaults
    mode tcp
    timeout connect 5000ms
    timeout client 50000ms
    timeout server 50000ms

frontend postgres_frontend
    bind *:5432
    default_backend postgres_replicas

backend postgres_replicas
    balance roundrobin
    option tcp-check
    tcp-check expect string "is_ready"
    server replica1 postgres-replica1:5432 check
    server replica2 postgres-replica2:5432 check backup
```

### Sharding Strategy

```yaml
# Shard-specific compose file
version: '3.8'

services:
  # Shard 1 - Users A-M
  postgres-shard1:
    image: postgres:15
    environment:
      POSTGRES_DB: myapp_shard1
      POSTGRES_USER: sharduser
      POSTGRES_PASSWORD: shardpass
    ports:
      - "5441:5432"
    volumes:
      - shard1_data:/var/lib/postgresql/data
      - ./shard1-init:/docker-entrypoint-initdb.d

  # Shard 2 - Users N-Z
  postgres-shard2:
    image: postgres:15
    environment:
      POSTGRES_DB: myapp_shard2
      POSTGRES_USER: sharduser
      POSTGRES_PASSWORD: shardpass
    ports:
      - "5442:5432"
    volumes:
      - shard2_data:/var/lib/postgresql/data
      - ./shard2-init:/docker-entrypoint-initdb.d

  # Coordinator/Router
  postgres-coordinator:
    build: ./coordinator
    environment:
      SHARD1_HOST: postgres-shard1
      SHARD1_PORT: 5432
      SHARD2_HOST: postgres-shard2
      SHARD2_PORT: 5432
      SHARD1_DB: myapp_shard1
      SHARD2_DB: myapp_shard2
    ports:
      - "5440:5432"
    depends_on:
      - postgres-shard1
      - postgres-shard2

volumes:
  shard1_data:
  shard2_data:
```

### Automated Partition Management

```sql
-- init-scripts/02-partition-management.sql

-- Create function to automatically create monthly partitions
CREATE OR REPLACE FUNCTION create_monthly_partitions(
    table_name TEXT,
    start_date DATE,
    end_date DATE
) RETURNS VOID AS $
DECLARE
    partition_date DATE;
    partition_name TEXT;
    start_range DATE;
    end_range DATE;
BEGIN
    partition_date := date_trunc('month', start_date);
    
    WHILE partition_date <= end_date LOOP
        partition_name := table_name || '_' || to_char(partition_date, 'YYYY_MM');
        start_range := partition_date;
        end_range := partition_date + INTERVAL '1 month';
        
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF %I 
                       FOR VALUES FROM (%L) TO (%L)',
                       partition_name, table_name, start_range, end_range);
        
        -- Create indexes
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_customer_id ON %I (customer_id)',
                       partition_name, partition_name);
        
        partition_date := partition_date + INTERVAL '1 month';
    END LOOP;
END;
$ LANGUAGE plpgsql;

-- Create partitions for next 12 months
SELECT create_monthly_partitions('orders', CURRENT_DATE, CURRENT_DATE + INTERVAL '12 months');

-- Schedule automatic partition creation (using pg_cron extension)
-- SELECT cron.schedule('create-partitions', '0 0 1 * *', 
--     'SELECT create_monthly_partitions(''orders'', CURRENT_DATE + INTERVAL ''1 month'', CURRENT_DATE + INTERVAL ''2 months'')');
```

### Monitoring and Metrics

```yaml
  # Monitoring stack
  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'

  postgres-exporter:
    image: prometheuscommunity/postgres-exporter:latest
    container_name: postgres_exporter
    environment:
      DATA_SOURCE_NAME: "postgresql://dbuser:dbpass@postgres-master:5432/myapp?sslmode=disable"
    ports:
      - "9187:9187"
    depends_on:
      - postgres-master

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./grafana/datasources:/etc/grafana/provisioning/datasources

volumes:
  prometheus_data:
  grafana_data:
```

### Database Migration Strategy

```dockerfile
# Dockerfile for migration service
FROM postgres:15

RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

RUN pip3 install alembic psycopg2-binary

COPY alembic.ini /app/
COPY alembic/ /app/alembic/
COPY migrations/ /app/migrations/

WORKDIR /app

ENTRYPOINT ["alembic"]
CMD ["upgrade", "head"]
```

```yaml
  # Migration service
  migration:
    build: ./migration
    environment:
      DATABASE_URL: postgresql://dbuser:dbpass@postgres-master:5432/myapp
    depends_on:
      - postgres-master
    volumes:
      - ./migrations:/app/migrations
```

---

## 12. Production Best Practices

### Health Checks and Monitoring

```dockerfile
# Advanced health check
FROM node:18-alpine

WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production

COPY . .

# Install curl for health checks
RUN apk add --no-cache curl

# Comprehensive health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:3000/health/ready || exit 1

EXPOSE 3000
USER node

CMD ["node", "server.js"]
```

```javascript
// Health check endpoint example (server.js)
app.get('/health/live', (req, res) => {
  res.status(200).json({ status: 'alive', timestamp: new Date().toISOString() });
});

app.get('/health/ready', async (req, res) => {
  try {
    // Check database connection
    await db.query('SELECT 1');
    
    // Check external dependencies
    const redisStatus = await redis.ping();
    
    res.status(200).json({
      status: 'ready',
      timestamp: new Date().toISOString(),
      checks: {
        database: 'healthy',
        redis: redisStatus === 'PONG' ? 'healthy' : 'unhealthy'
      }
    });
  } catch (error) {
    res.status(503).json({
      status: 'not ready',
      error: error.message,
      timestamp: new Date().toISOString()
    });
  }
});
```

### Production Docker Compose

```yaml
version: '3.8'

services:
  app:
    image: myapp:${APP_VERSION:-latest}
    deploy:
      replicas: 3
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
      resources:
        limits:
          memory: 512M
          cpus: '0.5'
        reservations:
          memory: 256M
    environment:
      NODE_ENV: production
      DATABASE_URL: postgresql://user:${DB_PASSWORD}@postgres:5432/myapp
      REDIS_URL: redis://redis:6379
    secrets:
      - db_password
      - api_key
    networks:
      - app_network
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
      - nginx_logs:/var/log/nginx
    depends_on:
      - app
    networks:
      - app_network
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: myapp
      POSTGRES_USER: user
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./postgres/postgresql.conf:/etc/postgresql/postgresql.conf:ro
    secrets:
      - db_password
    networks:
      - db_network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U user -d myapp"]
      interval: 10s
      timeout: 5s
      retries: 5
    deploy:
      resources:
        limits:
          memory: 1G
        reservations:
          memory: 512M

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis_data:/data
    networks:
      - app_network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3

networks:
  app_network:
    driver: overlay
    attachable: true
  db_network:
    driver: overlay
    internal: true

volumes:
  postgres_data:
    driver: local
  redis_data:
    driver: local
  nginx_logs:
    driver: local

secrets:
  db_password:
    external: true
  api_key:
    external: true
```

### Backup and Recovery

```yaml
  # Backup service
  backup:
    image: postgres:15
    environment:
      PGPASSWORD: ${DB_PASSWORD}
    volumes:
      - ./backups:/backups
      - ./backup-scripts:/scripts
    networks:
      - db_network
    command: |
      sh -c '
        # Daily backup
        pg_dump -h postgres -U user -d myapp -f /backups/myapp_$(date +%Y%m%d_%H%M%S).sql
        
        # Keep only last 7 days of backups
        find /backups -name "myapp_*.sql" -mtime +7 -delete
        
        # Point-in-time recovery setup
        pg_basebackup -h postgres -U replicator -D /backups/base_backup_$(date +%Y%m%d) -Ft -z -P -W
      '
    depends_on:
      - postgres
```

### SSL/TLS Configuration

```nginx
# nginx/nginx.conf
upstream app_backend {
    least_conn;
    server app:3000 max_fails=3 fail_timeout=30s;
}

server {
    listen 80;
    listen [::]:80;
    server_name example.com www.example.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name example.com www.example.com;

    ssl_certificate /etc/nginx/ssl/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;
    ssl_prefer_server_ciphers off;

    location / {
        proxy_pass http://app_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_send_timeout 30s;
        proxy_read_timeout 30s;
    }

    location /health {
        access_log off;
        proxy_pass http://app_backend/health;
    }
}
```

### Performance Optimization

```yaml
# High-performance compose configuration
version: '3.8'

services:
  app:
    image: myapp:latest
    deploy:
      replicas: 5
      placement:
        constraints:
          - node.labels.type == compute
      resources:
        limits:
          memory: 1G
          cpus: '1.0'
    environment:
      NODE_ENV: production
      UV_THREADPOOL_SIZE: 128
      NODE_OPTIONS: "--max-old-space-size=768"
    sysctls:
      - net.core.somaxconn=65535
    ulimits:
      nofile:
        soft: 65536
        hard: 65536

  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: myapp
      POSTGRES_USER: user
    volumes:
      - postgres_data:/var/lib/postgresql/data
    command: >
      postgres
      -c max_connections=200
      -c shared_buffers=512MB
      -c effective_cache_size=2GB
      -c maintenance_work_mem=128MB
      -c checkpoint_completion_target=0.9
      -c wal_buffers=16MB
      -c default_statistics_target=100
      -c random_page_cost=1.1
      -c effective_io_concurrency=200
      -c work_mem=8MB
      -c min_wal_size=1GB
      -c max_wal_size=4GB
    deploy:
      placement:
        constraints:
          - node.labels.type == storage
      resources:
        limits:
          memory: 2G
        reservations:
          memory: 1G
```

### CI/CD Integration

```yaml
# .github/workflows/deploy.yml
name: Deploy to Production

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Build Docker image
      run: |
        docker build -t myapp:${{ github.sha }} .
        docker tag myapp:${{ github.sha }} myapp:latest
    
    - name: Run security scan
      run: |
        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
          aquasec/trivy image myapp:${{ github.sha }}
    
    - name: Push to registry
      run: |
        echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
        docker push myapp:${{ github.sha }}
        docker push myapp:latest
    
    - name: Deploy to production
      run: |
        # Update compose file with new image tag
        sed -i 's|myapp:latest|myapp:${{ github.sha }}|g' docker-compose.prod.yml
        
        # Deploy using Docker Swarm
        docker stack deploy -c docker-compose.prod.yml myapp
        
        # Wait for deployment to complete
        docker service logs myapp_app --follow --tail 100
```

### Troubleshooting Commands

```bash
# Container debugging
docker logs -f container_name          # Follow logs
docker exec -it container_name sh      # Interactive shell
docker inspect container_name          # Detailed info
docker stats container_name           # Resource usage

# Network debugging
docker network ls                     # List networks
docker network inspect network_name   # Network details
docker run --rm --net container:name nicolaka/netshoot  # Network tools

# Volume debugging
docker volume inspect volume_name     # Volume details
docker run --rm -v volume_name:/data alpine ls -la /data  # Inspect volume contents

# Performance monitoring
docker system df                      # Disk usage
docker system events                  # Real-time events
docker system prune -a               # Clean up everything

# Backup and restore
docker run --rm -v volume_name:/data -v $(pwd):/backup alpine \
  tar czf /backup/backup.tar.gz -C /data .

docker run --rm -v volume_name:/data -v $(pwd):/backup alpine \
  tar xzf /backup/backup.tar.gz -C /data
```

---

## Summary

This comprehensive guide covers Docker from basic concepts to advanced PostgreSQL scaling strategies. Key takeaways:

**Docker Fundamentals:**
- Understand images, containers, and the layered filesystem
- Master Dockerfile best practices and multi-stage builds
- Utilize Docker Compose for multi-container applications

**Networking and Storage:**
- Configure custom networks for service isolation
- Implement persistent storage with volumes
- Secure inter-container communication

**PostgreSQL Scaling:**
- Implement table partitioning for performance
- Set up read replicas for horizontal scaling
- Use connection pooling and load balancing
- Automate partition management

**Production Readiness:**
- Implement comprehensive monitoring and health checks
- Configure SSL/TLS and security best practices
- Set up automated backups and recovery procedures
- Optimize for performance and reliability

**Next Steps:**
1. Practice with the provided examples
2. Set up a local development environment
3. Implement monitoring and alerting
4. Plan your PostgreSQL partitioning strategy
5. Test backup and recovery procedures

This foundation will enable you to build scalable, production-ready applications with Docker and PostgreSQL.