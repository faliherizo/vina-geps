# ── Interface d'Urgences-santé : construite par Vite, servie par nginx ─────────────────────────
#
# nginx sert les fichiers statiques et relaie /api vers le serveur Java (API_UPSTREAM:API_PORT).
FROM node:22-alpine AS build
WORKDIR /app/frontend
# Le verrou fixe les versions auditées (tests d'intrusion du 22 novembre 2026, IN-02) : npm ci refuse un verrou désaccordé.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend ./
ENV VITE_API_URL=/api
RUN npm run build

# Image sans privilège (IN-01) : maître et travailleurs tournent sous l'utilisateur nginx, sur le port 8080.
FROM nginxinc/nginx-unprivileged:1.29-alpine AS runtime
# La ligne principale de nginx (recommandée en production par nginx.org). La 1.27 restait sur Alpine 3.21.3 et la 1.28
# publiée embarque nginx 1.28.2, que corrige la 1.28.3 jamais publiée en image (5 CVE critiques — Trivy, 22 nov. 2026).
# Les correctifs Alpine publiés après l'image de base s'appliquent ici ; nginx, lui, est épinglé par l'image.
USER root
RUN apk upgrade --no-cache
COPY --from=build /app/frontend/dist /usr/share/nginx/html
# Gabarit rempli par l'image nginx au démarrage (envsubst sur ${API_UPSTREAM} et ${API_PORT} seulement).
COPY frontend/nginx.conf /etc/nginx/templates/default.conf.template
# API_PROXIES_CONFIANCE : la plage d'où X-Forwarded-For est cru (le VPC en AWS) ; par défaut, personne.
ENV API_UPSTREAM=backend API_PORT=4000 API_PROXIES_CONFIANCE=127.0.0.1/32 VITRINE_HOTE="*" NGINX_ENVSUBST_FILTER="^(API|VITRINE)_"
# Déclaré explicitement (Trivy DS002) : l'image de base tourne déjà sous nginx (uid 101), mais rien ne le garantissait
# si elle changeait — c'est l'image livrée qui doit le dire.
USER 101
EXPOSE 8080
