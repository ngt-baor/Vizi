# Vizi backend on Oracle Always Free

This deployment keeps the Vue frontend on Cloudflare Pages and PostgreSQL on
Supabase. Oracle runs only the Spring Boot API and Caddy HTTPS proxy.

## VM shape

- Ubuntu 24.04 or 22.04, ARM64.
- `VM.Standard.A1.Flex`, Always Free eligible.
- Start with 1 OCPU and 6 GB RAM.
- Reserve a public IPv4 address so the API hostname does not change.

## Oracle networking

Allow these ingress ports in the VCN security list or network security group:

- TCP 22 from the administrator IP only.
- TCP 80 from `0.0.0.0/0`.
- TCP 443 from `0.0.0.0/0`.
- UDP 443 from `0.0.0.0/0` for HTTP/3 (optional).

Do not expose PostgreSQL or port 8080 publicly.

## Install and deploy

```bash
sudo ./deploy/oracle/bootstrap-ubuntu.sh
sudo usermod -aG docker "$USER"
newgrp docker

cp deploy/oracle/.env.example deploy/oracle/.env
nano deploy/oracle/.env

docker compose -f deploy/oracle/compose.yaml up -d --build
docker compose -f deploy/oracle/compose.yaml ps
docker compose -f deploy/oracle/compose.yaml logs --tail=200 backend
```

The hostname in `VIZI_API_HOST` must already resolve to the VM public IPv4.
Caddy obtains and renews the TLS certificate automatically.

## Verify

```bash
curl -fsS "https://${VIZI_API_HOST}/api/health"
curl -i -H "Origin: https://vizi.pages.dev" \
  "https://${VIZI_API_HOST}/api/templates"
```

The second response must include:

```text
Access-Control-Allow-Origin: https://vizi.pages.dev
```

Only after both checks pass, change Cloudflare Pages
`VITE_API_BASE_URL` to `https://${VIZI_API_HOST}` and redeploy the frontend.

## Update and rollback

```bash
git pull --ff-only
docker compose -f deploy/oracle/compose.yaml up -d --build
```

Keep the current Render service until the Oracle API passes the production
smoke test. Rollback is changing `VITE_API_BASE_URL` back to the Render URL.

## Secrets and persistence

- `deploy/oracle/.env` is ignored by Git. Never commit it.
- Uploaded images use a named Docker volume and survive container rebuilds.
- Back up uploads before replacing or deleting the VM boot volume.
- Supabase remains the source of truth for relational data.
