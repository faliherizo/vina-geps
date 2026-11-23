# Bac à sable de poussée : aucune dépendance hors de Node ; l'image tourne sans privilège.
FROM node:22-alpine
WORKDIR /app
COPY package.json ./
COPY src ./src
USER node
ENV PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1:8080/sante || exit 1
CMD ["node", "src/serveur.mjs"]
