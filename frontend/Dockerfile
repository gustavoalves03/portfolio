# --- Build ---
FROM node:22 AS build
WORKDIR /app

# 1) Copier les manifests en premier pour profiter du cache
COPY package*.json ./

# 2) Installer : si lockfile présent -> npm ci, sinon -> npm install
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi

# 3) Copier le reste du code et builder
COPY . .
RUN npm run build -- --configuration production

# 4) Normaliser la sortie du build vers /tmp/dist
# - Angular 17+ : dist/<app>/browser
# - Autres : dist/<app> ou dist/
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
# Fallback SPA pour éviter les 404 sur rafraîchissement de route
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copier les fichiers statiques normalisés
COPY --from=build /tmp/dist/ /usr/share/nginx/html/

EXPOSE 80
