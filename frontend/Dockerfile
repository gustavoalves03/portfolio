# --- Build ---
FROM node:22-bookworm-slim AS build
WORKDIR /app

# 0) Outils pour paquets natifs (seulement si nécessaire)
RUN apt-get update && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/*

# 1) Copier manifests (+ .npmrc si tu en as un)
COPY package*.json ./
# COPY .npmrc ./.npmrc

# 2) Epingle npm compatible (ajuste la version si besoin)
#   - Commente cette ligne si ton lockfile a été généré avec npm 10+
RUN npm -v && node -v && npm i -g npm@9 && npm -v

# 3) Installer deps (ci si lockfile, sinon install) + options utiles
#    --no-fund/--no-audit: moins de bruit ; --legacy-peer-deps si conflits
RUN if [ -f package-lock.json ]; then \
      npm ci --no-fund --no-audit || npm ci --legacy-peer-deps --no-fund --no-audit; \
    else \
      npm install --no-fund --no-audit || npm install --legacy-peer-deps --no-fund --no-audit; \
    fi

# 4) Copier le reste et builder
COPY . .
RUN npm run build -- --configuration production

# 5) Normaliser la sortie du build vers /tmp/dist
RUN set -eux; \
    mkdir -p /tmp/dist; \
    if compgen -G "dist/*/browser/*" > /dev/null; then \
      cp -r dist/*/browser/* /tmp/dist/; \
    elif compgen -G "dist/*/*" > /dev/null; then \
      cp -r dist/*/* /tmp/dist/; \
    else \
      cp -r dist/* /tmp/dist/ 2>/dev/null || true; \
    fi

# --- Run ---
FROM nginx:alpine
# SPA fallback
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /tmp/dist/ /usr/share/nginx/html/
EXPOSE 80
