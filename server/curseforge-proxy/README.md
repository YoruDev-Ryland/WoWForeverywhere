# CurseForge proxy

The app's CurseForge requests go through this proxy, which adds the API key so the key is never part of the APK. It only forwards the World of Warcraft requests the app makes, rate-limits each device, and caches nothing.

## Run

On a server with Docker, where the proxy's hostname points at the server and ports 80 and 443 are open:

```bash
cp .env.example .env    # then put the key between the single quotes
docker compose up -d --build
curl https://cf.yorudev.com/health
```

Caddy gets and renews the HTTPS certificate on its own. To use another hostname, set `PROXY_HOST` in `.env` and build the app with `-PCURSEFORGE_PROXY=https://your.host`.
