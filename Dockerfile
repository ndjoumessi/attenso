# Empaquetage de production (jalon L2).
#
# Deux étages, et le premier n'est pas du confort : construire dans l'image
# finale y laisserait le JDK, Maven et le dépôt .m2 — environ 400 Mo de surface
# d'attaque qui ne servent plus une fois le jar produit.
FROM maven:3.9-eclipse-temurin-17 AS construction
WORKDIR /source

# Node, pour construire le front. L'image Maven ne l'a pas, et le dépôt
# NodeSource est ici préféré à un téléchargement de binaire par un plugin
# Maven : il est signé, versionné, et l'étage entier disparaît de l'image
# finale. Rien de tout cela ne subsiste à l'exécution — ENF-04 parle des
# appels sortants du PRODUIT, pas de ceux de sa fabrication.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# Les dépendances d'abord, le code ensuite : une correction d'une ligne dans
# un contrôleur ne doit pas refaire tout le téléchargement Maven.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Les dépendances du front avant son code, pour la même raison que celles de
# Maven : une correction dans un composant ne doit pas refaire npm ci.
COPY front/package.json front/package-lock.json ./front/
RUN cd front && npm ci --no-fund --no-audit

COPY front ./front
COPY src ./src
COPY package.json .
# Les tests exigent Docker (Testcontainers) et ne peuvent donc pas tourner
# DANS une image en construction. C'est la CI qui les passe, à chaque poussée,
# avec exactement la même commande que le développeur.
RUN mvn -q -B -DskipTests package

# Jammy et non Alpine : Temurin ne publie PAS de JRE Alpine pour arm64, et
# l'image ne se construirait donc pas sur un Mac Apple Silicon ni sur un
# serveur ARM — qui sont exactement les machines les moins chères à louer en
# UE. Quelques dizaines de mégaoctets de plus contre une image qui se
# construit partout.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /application

# curl sert au contrôle de vivacité ci-dessous, et à rien d'autre.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Utilisateur sans privilège : une faille d'exécution de code ne doit pas
# donner root dans le conteneur.
RUN groupadd --system attenso && useradd --system --gid attenso attenso
USER attenso

COPY --from=construction --chown=attenso:attenso /source/target/*.jar application.jar

EXPOSE 8080

# L'orchestrateur a besoin de savoir si l'application est VIVANTE, pas si le
# port est ouvert : Tomcat répond avant que Flyway n'ait fini de migrer, et un
# conteneur déclaré prêt trop tôt reçoit du trafic qu'il ne peut pas servir.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'

# Le conteneur connaît sa mémoire mieux que nous : pas de -Xmx en dur, qui
# serait faux dès qu'on change la taille de la machine.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
