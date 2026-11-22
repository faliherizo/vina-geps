# ── Interface d'Urgences-santé : construite par Vite, servie par nginx ─────────────────────────
#
# nginx sert les fichiers statiques et relaie /api vers le serveur Java (API_UPSTREAM:API_PORT).
FROM node:20-alpine AS build
WORKDIR /app/frontend
# Le verrou fixe les versions auditées (tests d'intrusion du 22 novembre 2026, IN-02) : npm ci refuse un verrou désaccordé.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend ./
ENV VITE_API_URL=/api
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/frontend/dist /usr/share/nginx/html
# Gabarit rempli par l'image nginx au démarrage (envsubst sur ${API_UPSTREAM} et ${API_PORT} seulement).
COPY frontend/nginx.conf /etc/nginx/templates/default.conf.template
ENV API_UPSTREAM=backend API_PORT=4000 NGINX_ENVSUBST_FILTER="^API_"
EXPOSE 80
