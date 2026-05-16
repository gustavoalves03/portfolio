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
# Angular 17+ avec @angular/build:application produit dist/<project>/browser/*
# (SSR enabled) ou dist/<project>/* (SSR disabled).
RUN set -eux; \
    mkdir -p /tmp/dist; \
    BROWSER_DIR=$(find dist -mindepth 2 -maxdepth 2 -type d -name browser | head -1); \
    if [ -n "$BROWSER_DIR" ]; then \
      echo "Found browser bundle in $BROWSER_DIR"; \
      cp -r "$BROWSER_DIR"/. /tmp/dist/; \
    else \
      PROJECT_DIR=$(find dist -mindepth 1 -maxdepth 1 -type d | head -1); \
      echo "No browser/ subdir, using $PROJECT_DIR"; \
      cp -r "$PROJECT_DIR"/. /tmp/dist/; \
    fi; \
    ls -la /tmp/dist/ | head -10; \
    test -f /tmp/dist/index.html || (echo "ERROR: no index.html in /tmp/dist" && exit 1)

# --- Run ---
FROM nginx:alpine
# Remove default nginx welcome page (otherwise it lingers if our copy misses it)
RUN rm -f /usr/share/nginx/html/index.html /usr/share/nginx/html/50x.html
# SPA fallback
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /tmp/dist/ /usr/share/nginx/html/
EXPOSE 80
