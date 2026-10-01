# Build image for Shandalar, with JDK 17, Maven and Gradle.
# Caches (~/.m2, ~/.gradle) are mounted as named Docker volumes by scripts/dock,
# so nothing is installed on the host.
FROM eclipse-temurin:17-jdk

ARG GRADLE_VERSION=9.6.1

RUN apt-get update \
 && apt-get install -y --no-install-recommends maven unzip curl git python3 \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
 && unzip -q /tmp/gradle.zip -d /opt \
 && ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle \
 && rm /tmp/gradle.zip

ENV MAVEN_OPTS="-Xmx3g" \
    GRADLE_OPTS="-Xmx4g -Dorg.gradle.daemon=false"

WORKDIR /work
