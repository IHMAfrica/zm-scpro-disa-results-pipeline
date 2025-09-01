# ZM SCPro DISA Results Pipeline

An Apache Flink streaming job that consumes lab result messages from Kafka and writes parsed results to PostgreSQL. The job is packaged as a fat jar and designed to run on a Flink session cluster managed by the Flink Kubernetes Operator. CI builds and publishes a container to GHCR, and a Rancher Fleet-compatible FlinkSessionJob manifest is included.

## Features
- Kafka -> Flink -> PostgreSQL streaming pipeline
- Externalized configuration via environment variables and/or command-line arguments
- Containerized with Docker; deployable via Flink Kubernetes Operator (FlinkSessionJob)
- GitHub Actions pipeline builds and pushes image to GHCR

## Build
Requirements:
- JDK 21
- Gradle Wrapper included

Build fat jar:
- ./gradlew clean shadowJar

Run locally (example):
- export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
- export KAFKA_TOPIC=lab-results
- export KAFKA_GROUP_ID=flink-local
- export JDBC_URL=jdbc:postgresql://localhost:5432/hie_manager
- export JDBC_USER=postgres
- export JDBC_PASSWORD=postgres
- java -jar build/libs/zm-scpro-disa-results-pipeline-*-all.jar --kafka.security.protocol=SASL_PLAINTEXT --kafka.sasl.mechanism=SCRAM-SHA-256 --kafka.sasl.username=admin --kafka.sasl.password=secret

Note: When running locally outside a Flink cluster, you might also use the application plugin:
- ./gradlew run --args="--kafka.bootstrap.servers=localhost:9092 --kafka.topic=lab-results"

## Configuration
Configuration can be provided via environment variables or command-line arguments. Precedence: CLI args override environment variables; environment variables override built-in defaults. Secrets should be provided via environment variables or Kubernetes secrets, not hard-coded.

Supported keys (CLI args use --key=value form):
- kafka.bootstrap.servers / KAFKA_BOOTSTRAP_SERVERS
- kafka.topic / KAFKA_TOPIC
- kafka.group.id / KAFKA_GROUP_ID
- kafka.security.protocol / KAFKA_SECURITY_PROTOCOL (e.g., SASL_PLAINTEXT or SASL_SSL)
- kafka.sasl.mechanism / KAFKA_SASL_MECHANISM (e.g., SCRAM-SHA-256)
- kafka.sasl.username / KAFKA_SASL_USERNAME
- kafka.sasl.password / KAFKA_SASL_PASSWORD
- jdbc.url / JDBC_URL (e.g., jdbc:postgresql://host:5432/db)
- jdbc.user / JDBC_USER
- jdbc.password / JDBC_PASSWORD

Example CLI args:
- --kafka.bootstrap.servers=broker1:9092,broker2:9092
- --kafka.topic=lab-results
- --jdbc.url=jdbc:postgresql://db:5432/hie_manager

## Docker
Build image locally:
- docker build -t zm-scpro-disa-results-pipeline:dev .

Run container (example):
- docker run --rm \
  -e KAFKA_BOOTSTRAP_SERVERS=broker:9092 \
  -e KAFKA_TOPIC=lab-results \
  -e JDBC_URL=jdbc:postgresql://postgres:5432/hie_manager \
  -e JDBC_USER=postgres \
  -e JDBC_PASSWORD=postgres \
  zm-scpro-disa-results-pipeline:dev

Note: In Kubernetes, the Flink Operator handles job submission; you usually won't run the container directly.

## Kubernetes Deployment (FlinkSessionJob)
This repo provides k8s/fleet/flink-sessionjob.yaml with a FlinkSessionJob targeting an existing session cluster named session-cluster.

Important:
- Ensure a FlinkDeployment session cluster named session-cluster exists in your namespace.
- Provide connector jars (Kafka/JDBC) compatible with Flink 1.20 in the cluster lib/ if not bundled.
- Inject configuration via:
  - Environment variables on the pod (via PodTemplate), or
  - Job arguments in the FlinkSessionJob spec.

Example additions (pod env) in k8s/fleet/flink-sessionjob.yaml:
  podTemplate:
    spec:
      containers:
        - name: flink-main-container
          env:
            - name: KAFKA_BOOTSTRAP_SERVERS
              value: "broker1:9092,broker2:9092"
            - name: JDBC_URL
              valueFrom:
                secretKeyRef:
                  name: jdbc-secret
                  key: url
            - name: JDBC_USER
              valueFrom:
                secretKeyRef:
                  name: jdbc-secret
                  key: user
            - name: JDBC_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: jdbc-secret
                  key: password

Example job arguments in FlinkSessionJob:
  job:
    arguments:
      - "--kafka.topic=lab-results"
      - "--kafka.group.id=consumer-x"

## CI/CD
The GitHub Actions workflow .github/workflows/ci.yml builds the project, creates a container image, and pushes to GHCR with :latest and :sha tags. It also emits a resolved manifest artifact with the fully-qualified image reference for Fleet use.

## Security Notes
- Provide secrets (Kafka SASL password, JDBC password) via environment variables or Kubernetes secrets.
- The app logs redact secret values but will show non-sensitive settings for troubleshooting.

## License
Add your license information here.
