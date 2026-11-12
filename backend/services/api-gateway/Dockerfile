# syntax=docker/dockerfile:1

# ==========================================
# LTZ - DERLEME ASAMASI
# ==========================================

FROM maven:3.9-eclipse-temurin-25 AS build

ARG SERVICE_NAME

WORKDIR /workspace

# Katman 1: yalniz pom'lar + kalite yapilandirmasi. Maven reaktoru tum modullerin pom'unu ister (-pl yine de tum <module> listesini
# okur); kaynak kod burada YOK, boylece baska bir servisin kodu degisince bu katman ve asagidakiler bozulmaz.
COPY --parents backend/pom.xml backend/config backend/services/*/pom.xml ./

# Katman 2: yalniz bu servisin kaynagi.
COPY backend/services/${SERVICE_NAME}/ ./backend/services/${SERVICE_NAME}/

# Maven deposu BuildKit onbellegine tasinir: bagimliliklar her build'de yeniden indirilmez.
RUN --mount=type=cache,id=ltz-m2,target=/root/.m2 \
    test -n "$SERVICE_NAME" \
    && mvn -B -ntp -f backend/pom.xml \
       -pl "services/${SERVICE_NAME}" \
       -am -DskipTests package \
    && JAR_PATH=$(find "backend/services/${SERVICE_NAME}/target" \
       -maxdepth 1 -type f -name "${SERVICE_NAME}-*.jar" \
       -print -quit) \
    && test -n "$JAR_PATH" \
    && cp "$JAR_PATH" /workspace/app.jar

# ==========================================
# LTZ - CALISMA ASAMASI
# ==========================================

FROM eclipse-temurin:25-jre-noble

WORKDIR /app

# Root olmayan uygulama kullanicisi.
RUN groupadd --system ltz \
    && useradd --system --gid ltz ltz

COPY --from=build \
    --chown=ltz:ltz \
    /workspace/app.jar /app/app.jar

USER ltz

EXPOSE 8080

# Kapsayici sagligi: Actuator health 200 donmuyorsa unhealthy (bash /dev/tcp; imajda curl yok).
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${BACKEND_INTERNAL_PORT:-8080} && printf 'GET /actuator/health HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && head -n1 <&3 | grep -q ' 200 '"]


ENTRYPOINT ["java", "-jar", "/app/app.jar"]
