# Compute Engine deployment

This deployment uses the existing services on one Ubuntu VM with Docker Compose, not GKE.
The cloud starts with fresh PostgreSQL, Cassandra, Kafka and Redis volumes. Do not copy local
database volumes or credentials. This is a single-node deployment, not a highly available cluster.

## Target

- Project: `earnest-mark-513303-s2`
- VM: `instance-20261031-040324`, zone `us-central1-c`
- Machine: 2 vCPU / 8 GB; 50 GB Standard persistent disk
- Entry: `127.0.0.1:8088` on the VM, intentionally not publicly exposed

The 8 GB memory settings are starting limits, not a proven capacity guarantee. Inspect
`sudo docker stats --no-stream` and container OOM/restart counts after startup. Java heap
limits leave some room for native memory. Build sequentially, preferably with services stopped
on this small VM. Subsequent rebuilds while all services are running may exhaust memory;
schedule downtime and use the stop command below first if necessary.

## First deployment (inside the VM)

```bash
git clone --branch mvp-v1 https://github.com/back2childhood/shopping-web.git
cd shopping-web
bash scripts/init-gcp-env.sh
bash scripts/deploy-gcp.sh
```

Record `git rev-parse HEAD` for every deployment. `.env.gcp` is ignored by Git and excluded
from Docker build contexts; keep a private backup. Never print expanded `docker compose config`
to shared logs because it contains secrets; use `config --quiet` for validation.

`compose.gcp.yml` is an overlay, not a standalone file. It removes inherited host ports,
requires cloud credentials, adds resource/log limits and restarts, and builds the frontend for
Node instead of Cloudflare. Nginx serves frontend and `/api` through one origin. Database
password changes require a database-aware rotation, not merely editing the environment file.

## Access from the Mac

Replace `VM_EXTERNAL_IP` with the current address in the console:

```bash
ssh -N -L 127.0.0.1:8088:127.0.0.1:8088 jiliqian805@VM_EXTERNAL_IP
```

Keep that terminal open and visit `http://localhost:8088`. Browser traffic travels through
the encrypted SSH connection. No Google Cloud ingress rule for 8088 or database ports is
needed. A temporary external IP can change after VM stop/start. Do not open public login
traffic until TLS and ingress controls have been configured. SSH access should be restricted
to approved clients or moved to IAP; this deployment does not modify cloud firewall rules.

## Operation

```bash
# Status, logs, and memory
sudo docker compose -p shopping-gcp --env-file .env.gcp -f compose.yml -f compose.gcp.yml ps
sudo docker compose -p shopping-gcp --env-file .env.gcp -f compose.yml -f compose.gcp.yml logs --tail=100
sudo docker stats --no-stream

# Stop services without deleting their data
sudo docker compose -p shopping-gcp --env-file .env.gcp -f compose.yml -f compose.gcp.yml stop

# Start existing images in dependency order after a VM restart
sudo docker compose -p shopping-gcp --env-file .env.gcp -f compose.yml -f compose.gcp.yml up -d --wait --wait-timeout 900

# Deploy a reviewed new commit (do not overwrite local server changes)
git pull --ff-only origin mvp-v1
bash scripts/deploy-gcp.sh
```

Restart policies are not dependency-readiness checks after a VM reboot; if a service fails
while its database is warming up, inspect its logs and run the explicit `up --wait` command.
Nginx `/healthz` only checks the entry process, not the complete business flow. Verify buyer
registration/login, seller product creation/update, buyer catalog, checkout and order history
after a release. Maven unit tests do not replace these cloud smoke tests.

Do not run `down -v` or `docker volume prune`: they can delete database data. Hibernation,
closing SSH, and stopping Docker are not equivalent to stopping the VM for billing purposes.
Use Compute Engine **Stop**, not Delete, when finished. Confirm the two-hour STOP runtime
limit in the console; the deployment script does not configure it. Persistent storage and
other retained resources may continue to cost money. Budget alerts are not hard spending caps.

Kafka has one broker and Cassandra one node here. Volumes are not backups; automated
snapshots are currently disabled. This configuration must be revisited before real customer data.
