# CurseForge proxy

The app's CurseForge requests go through this proxy, which adds the API key so the key is never part of the APK. It only forwards the World of Warcraft requests the app makes, only from WoW Foreverywhere, rate-limits each device, and caches nothing.

## Run

On a server with Docker, where the proxy's hostname points at the server and ports 80 and 443 are open:

```bash
cp .env.example .env    # then put the key between the single quotes
docker compose up -d --build
curl https://cf.yorudev.com/health
```

Caddy gets and renews the HTTPS certificate on its own. To use another hostname, set `PROXY_HOST` in `.env` and build the app with `-PCURSEFORGE_PROXY=https://your.host`.

## Only WoW Foreverywhere

Requests must identify as `WoWForeverywhere/<version>` in their User-Agent; anything else gets a 403.

Release builds also carry a token from the `CURSEFORGE_PROXY_TOKEN` repository secret, stored scrambled in the APK. Builds of this source without that secret don't have it. While `APP_TOKEN` in `.env` is empty the proxy doesn't ask for the token, so versions from before it keep working. Once most users have a release that carries it, put the same value in `APP_TOKEN` and restart:

```bash
docker compose up -d
```

To change the token, update the secret and `APP_TOKEN` together and publish a release. Older versions then lose CurseForge until they update.
