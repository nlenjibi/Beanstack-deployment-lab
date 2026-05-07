# BEM12 — Spring Boot on AWS Elastic Beanstalk

A Java Spring Boot web application deployed to AWS Elastic Beanstalk via a fully automated GitHub Actions CI/CD pipeline, with DynamoDB integration for visit tracking.

---

## Architecture

```
GitHub (dev branch)
    │
    ├─► CI workflow (ci.yml)
    │       ├── Build & Unit Tests
    │       ├── Code Quality (Checkstyle)
    │       └── Security Scan (Trivy + OWASP)
    │
    └─► Pull Request ──► main branch
                              │
                              ▼
                    Deploy workflow (deploy.yml)
                              ├── Build & test (gate)
                              ├── Upload bundle → S3
                              ├── Create EB application version
                              ├── Deploy → Elastic Beanstalk
                              └── Verify /health endpoint
```

---

## Branch Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Production. Merging here triggers deployment to Elastic Beanstalk. |
| `dev`  | Development. All feature work goes here. CI runs on every push. |

**Workflow:** commit to `dev` → CI passes → open PR to `main` → merge → auto-deploy.

---

## Prerequisites

- Java 17 (Temurin)
- Maven 3.8+
- AWS account with:
  - Elastic Beanstalk application (`bem12-app`) and environment (`bem12-env`) on Corretto 17
  - S3 bucket for deployment artifacts
  - DynamoDB table `bem12-visits` (partition key: `visitId`, type String)
  - IAM role on EB EC2 instances with DynamoDB read/write permissions

---

## Local Development

```bash
git clone <repo-url>
cd bem12-app

# Copy environment file
cp ../.env.example .env   # edit with your values

mvn spring-boot:run
# → http://localhost:5000
```

---

## Application Endpoints

| Endpoint | Auth | Description |
|----------|------|-------------|
| `GET /` | Public | Landing page |
| `GET /register` | Public | Create account |
| `GET /login` | Public | Login |
| `GET /welcome` | Required | Dashboard with health check button |
| `GET /health` | Public | Health check (used by EB load balancer) |
| `GET /api/status` | Public | Deployment status JSON |
| `GET /api/info` | Public | Application metadata JSON |
| `GET /api/data` | Public | Recent visits from DynamoDB |

---

## CI/CD Pipeline

### On push to `dev` or PR to `main` — `ci.yml`

| Stage | What runs |
|-------|-----------|
| Build & Test | Maven compile + unit tests + JaCoCo coverage report |
| Code Quality | Checkstyle analysis |
| Security Scan | Trivy (results → GitHub Security tab) + OWASP dependency check |

CI must pass before a PR to `main` can be merged (enable branch protection in repo Settings).

### On merge to `main` — `deploy.yml`

| Stage | What runs |
|-------|-----------|
| Build | Compile + run tests (gate), create ZIP bundle, upload to S3 |
| Deploy | Create EB version, deploy, wait for environment update, verify `/health` |

---

## Required GitHub Secrets

Set under **Settings → Secrets and variables → Actions**:

| Secret | Description |
|--------|-------------|
| `AWS_ACCESS_KEY_ID` | IAM access key with EB + S3 + DynamoDB permissions |
| `AWS_SECRET_ACCESS_KEY` | Corresponding secret key |
| `S3_BUCKET_NAME` | S3 bucket name for deployment bundles |

---

## Elastic Beanstalk Environment Variables

Managed via `.ebextensions/01-environment.config`. Override in the EB console under **Configuration → Software**:

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_VERSION` | `1.0.0` | Application version |
| `DYNAMODB_REGION` | `us-east-1` | AWS region for DynamoDB |
| `DYNAMODB_TABLE_NAME` | `bem12-visits` | Table for visit tracking |
| `JAVA_OPTS` | `-Xmx256m -Xms128m` | JVM heap settings |

---

## Running Tests Locally

```bash
cd bem12-app
mvn test                   # unit tests + JaCoCo coverage
mvn checkstyle:check       # code style
```

Coverage report: `target/site/jacoco/index.html`

---

## Project Structure

```
.
├── .ebextensions/           # Elastic Beanstalk configuration
├── .github/workflows/
│   ├── ci.yml               # CI: runs on dev push and PR to main
│   └── deploy.yml           # Deploy: runs on merge to main
├── bem12-app/
│   ├── src/main/java/       # Application source
│   ├── src/test/java/       # Unit tests (17 tests across 4 classes)
│   ├── src/main/resources/  # application.yaml, Thymeleaf templates, CSS
│   ├── checkstyle.xml       # Checkstyle rules
│   └── pom.xml
├── Procfile                 # EB process: web: java -jar application.jar
├── .env                     # Local env vars (not committed)
└── .gitignore
```
