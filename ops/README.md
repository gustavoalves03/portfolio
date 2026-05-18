# ops/

Infrastructure config files mirrored from the production VPS for versioning.

## Caddyfile

Lives at `/etc/caddy/Caddyfile` on the production VPS (`deploy@51.255.194.40`).
Caddy terminates TLS for `luxpretty.lu` / `www.luxpretty.lu` and reverse-proxies
to the two Docker containers (`luxpretty-backend` on 8080, `luxpretty-frontend`
on 8081).

### Deploy changes

Caddyfile is **not** auto-deployed by CI. To apply changes:

```bash
scp ops/Caddyfile deploy@51.255.194.40:/tmp/Caddyfile.new
ssh deploy@51.255.194.40 "sudo mv /tmp/Caddyfile.new /etc/caddy/Caddyfile \
  && sudo caddy validate --config /etc/caddy/Caddyfile \
  && sudo systemctl reload caddy"
```

### Routes overview

- `/api/*` → backend (8080)
- `/oauth2/*` → backend (Spring Security OAuth start)
- `/login/oauth2/*` → backend (OAuth callbacks)
- `/*` → frontend Angular (8081)
