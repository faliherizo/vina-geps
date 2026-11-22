# ── Serveur Java d'Urgences-santé (Spring Boot 4, Java 25) ─────────────────────────────────────
#
# Deux temps : Maven compile et empaquette (sans essais — ils tournent en CI, `mvn verify`), puis
# un JRE seul sert le jar. La base est créée et tenue à jour par Liquibase au démarrage : aucun
# second serveur n'est nécessaire pour la bâtir.
#
#   docker compose up -d --build
#
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src
RUN apk add --no-cache maven
COPY backend-java/pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline || true
COPY backend-java/src ./src
RUN mvn -B -q -DskipTests -Djacoco.skip=true package \
 && cp target/nexu-vina-backend-*.jar /app.jar

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
RUN apk add --no-cache curl \
 && addgroup -S urgences && adduser -S urgences -G urgences \
 && mkdir -p /app/data && chown urgences:urgences /app/data && chmod 700 /app/data
COPY --from=build /app.jar /app/app.jar
USER urgences
ENV PORT=4000 \
    DB_URL=jdbc:sqlite:/app/data/urgences-sante.db \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 4000
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 CMD curl -fs http://localhost:${PORT}/api/health || exit 1
# La base, ses journaux et ses sauvegardes ne se lisent que par leur propriétaire (IN-04) : umask 077 pour tout ce que le
# serveur crée, et les fichiers d'un volume plus ancien ramenés à 600 au démarrage.
ENTRYPOINT ["sh", "-c", "umask 077 && chmod -R go-rwx /app/data && exec java $JAVA_OPTS -jar /app/app.jar"]
