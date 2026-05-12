# AWS Setup Guide — BEM12 Application

This document covers every AWS resource the project needs, step by step.
Follow the sections in order the first time you set up the environment.

---

## S3 Buckets Overview

The project uses **two separate S3 buckets** with different access patterns
and security requirements.

| Purpose | Bucket name | Public access | Versioning |
|---------|-------------|---------------|------------|
| Elastic Beanstalk deployment ZIPs | `bem12-eb-deployments` | ❌ Private | ✅ Enabled |
| User profile pictures | `bem12-profiles` | ⚠️ Partial (read on `profiles/` only) | ❌ Not needed |

> **Naming note** — S3 bucket names are globally unique across all AWS accounts.
> If either name is taken, append your AWS account ID suffix:
> `bem12-eb-deployments-123456` / `bem12-profiles-123456`
> Then update the matching values in `.ebextensions/01-environment.config`,
> your `.env` file, and the `S3_BUCKET_NAME` GitHub secret.

---

## Bucket 1 — `bem12-eb-deployments`

**Purpose:** Stores the versioned ZIP bundles produced by GitHub Actions
and consumed by Elastic Beanstalk to deploy new application versions.

### 1.1 Create the bucket

1. Open **S3 → Create bucket**
2. **Bucket name:** `bem12-eb-deployments`
3. **AWS Region:** `us-east-1`
4. **Object Ownership:** ACLs disabled (default)
5. **Block all public access:** ✅ ON (all four checkboxes ticked)
6. **Bucket Versioning:** ✅ Enable
7. **Default encryption:** SSE-S3 (AES-256) — enable
8. Click **Create bucket**

### 1.2 Lifecycle rule (keep costs low)

1. Go to the bucket → **Management → Lifecycle rules → Create rule**
2. **Rule name:** `expire-old-bundles`
3. **Scope:** Apply to all objects
4. **Action:** Expire current versions after **90 days**
5. **Action:** Permanently delete non-current versions after **30 days**
6. Save rule

### 1.3 Permissions needed

This bucket has **no bucket policy** (stays private).
Access is granted only via IAM.

#### GitHub Actions IAM user

Attach the following inline policy to the IAM user whose keys are stored
as `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` in GitHub Secrets:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowDeploymentBundleUpload",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::bem12-eb-deployments",
        "arn:aws:s3:::bem12-eb-deployments/*"
      ]
    },
    {
      "Sid": "AllowElasticBeanstalkDeploy",
      "Effect": "Allow",
      "Action": [
        "elasticbeanstalk:CreateApplicationVersion",
        "elasticbeanstalk:UpdateEnvironment",
        "elasticbeanstalk:DescribeEnvironments",
        "elasticbeanstalk:DescribeApplicationVersions",
        "elasticbeanstalk:DescribeEvents"
      ],
      "Resource": "*"
    },
    {
      "Sid": "AllowDescribeForWaiter",
      "Effect": "Allow",
      "Action": [
        "elasticbeanstalk:DescribeEnvironmentHealth",
        "elasticbeanstalk:DescribeInstancesHealth"
      ],
      "Resource": "*"
    }
  ]
}
```

#### Elastic Beanstalk service role

Elastic Beanstalk reads bundles from S3 using its own service role
(`aws-elasticbeanstalk-service-role` or your custom role).
The managed policy `AWSElasticBeanstalkManagedUpdatesCustomerRolePolicy`
already includes the required `s3:GetObject` permission on EB-managed
buckets. If you see `AccessDenied` during deployment, add this to the
service role:

```json
{
  "Effect": "Allow",
  "Action": ["s3:GetObject"],
  "Resource": "arn:aws:s3:::bem12-eb-deployments/*"
}
```

---

## Bucket 2 — `bem12-profiles`

**Purpose:** Stores user profile pictures uploaded through the application.
Images must be readable by browsers (public GET on the `profiles/` prefix)
but the bucket is otherwise private.

### 2.1 Create the bucket

1. Open **S3 → Create bucket**
2. **Bucket name:** `bem12-profiles`
3. **AWS Region:** `us-east-1`
4. **Object Ownership:** ACLs disabled (default)
5. **Block all public access:**
   - ✅ Block *new* public ACLs
   - ✅ Block *any* public ACLs
   - ❌ **Uncheck** "Block public bucket policies"
   - ❌ **Uncheck** "Restrict public buckets"

   > Unchecking the bottom two options allows the bucket policy below
   > to grant public read on `profiles/*` while everything else stays private.

6. **Bucket Versioning:** Disabled
7. **Default encryption:** SSE-S3 (AES-256) — enable
8. Click **Create bucket**

### 2.2 Bucket policy — public read on `profiles/*` only

1. Go to the bucket → **Permissions → Bucket policy → Edit**
2. Paste the policy below, replacing `bem12-profiles` if you used a
   different name:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowPublicReadProfilePictures",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::bem12-profiles/profiles/*"
    }
  ]
}
```

3. Click **Save changes**

### 2.3 Permissions needed

#### EB EC2 instance role (write access for the application)

The EB environment runs your Spring Boot app on EC2. That EC2 instance
needs permission to PUT objects into `profiles/*`.

1. Go to **IAM → Roles**
2. Find the role attached to your EB environment
   (default name: `aws-elasticbeanstalk-ec2-role`)
3. **Add permissions → Create inline policy**
4. Switch to **JSON** and paste:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowProfilePictureUpload",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject"
      ],
      "Resource": "arn:aws:s3:::bem12-profiles/profiles/*"
    }
  ]
}
```

5. **Policy name:** `BEM12ProfilePictureAccess`
6. Click **Create policy**

### 2.4 CORS (not required)

Profile pictures are displayed via `<img src="...">` tags — no CORS
configuration is needed. CORS would only be required for direct
`fetch()` / `XMLHttpRequest` calls from the browser to S3, which this
application does not do (uploads go through the Spring Boot backend).

---

## DynamoDB Tables

The project uses **two DynamoDB tables**.

| Table | Partition key | Purpose |
|-------|--------------|---------|
| `bem12-users` | `email` (String) | User accounts and profile picture URLs |
| `bem12-visits` | `visitId` (String) | Page visit tracking |

---

### Table 1 — `bem12-users`

**Purpose:** Stores registered users (`email`, `passwordHash`, `profilePictureUrl`).
This is the authoritative user store — the application reads and writes here on every login and registration.

#### Create the table

1. Open **DynamoDB → Create table**
2. **Table name:** `bem12-users`
3. **Partition key:** `email` — type **String**
4. **Table settings:** Default settings (On-demand billing mode)
5. Click **Create table**

#### IAM permissions (EC2 instance role)

```json
{
  "Sid": "AllowUsersTableAccess",
  "Effect": "Allow",
  "Action": [
    "dynamodb:PutItem",
    "dynamodb:GetItem",
    "dynamodb:UpdateItem",
    "dynamodb:Scan",
    "dynamodb:DescribeTable"
  ],
  "Resource": "arn:aws:dynamodb:us-east-1:*:table/bem12-users"
}
```

---

### Table 2 — `bem12-visits`

**Purpose:** Records every page visit (path + timestamp) for analytics.

#### Create the table

1. Open **DynamoDB → Create table**
2. **Table name:** `bem12-visits`
3. **Partition key:** `visitId` — type **String**
4. **Table settings:** Default settings (On-demand billing mode)
5. Click **Create table**

#### IAM permissions (EC2 instance role)

Add the following to the same EB EC2 instance role:

```json
{
  "Sid": "AllowVisitsTableAccess",
  "Effect": "Allow",
  "Action": [
    "dynamodb:PutItem",
    "dynamodb:GetItem",
    "dynamodb:Scan",
    "dynamodb:Query",
    "dynamodb:DescribeTable"
  ],
  "Resource": "arn:aws:dynamodb:us-east-1:*:table/bem12-visits"
}
```

---

## Elastic Beanstalk Environment

### Create application and environment

1. **Elastic Beanstalk → Create application**
   - Application name: `bem12-app`

2. **Create environment** inside that application
   - Environment name: `bem12-env`
   - Platform: **Java — Corretto 17**
   - Environment type: **Load balanced**
   - Instance type: `t3.micro`

3. **Configuration → Software → Environment properties** — add:

   | Key | Value |
   |-----|-------|
   | `APP_VERSION` | `1.0.0` |
   | `DYNAMODB_REGION` | `us-east-1` |
   | `DYNAMODB_TABLE_NAME` | `bem12-visits` |
   | `DYNAMODB_USERS_TABLE_NAME` | `bem12-users` |
   | `S3_PROFILE_BUCKET` | `bem12-profiles` |
   | `S3_REGION` | `us-east-1` |
   | `JAVA_OPTS` | `-Xmx256m -Xms128m` |

4. **Configuration → Security** — set the IAM instance profile to the role
   you granted DynamoDB and S3 permissions to in the sections above.

5. **Configuration → Load balancer → Processes** — set health check path
   to `/health`.

### Upload initial version

Before GitHub Actions can deploy, Elastic Beanstalk needs at least one
existing version. Upload a placeholder bundle manually:

```bash
# Build locally
cd bem12-app
mvn clean package -DskipTests
cd ..

# Bundle
mkdir -p bundle
cp bem12-app/target/bem12-app-*.jar bundle/application.jar
cp Procfile bundle/
cp -r .ebextensions bundle/
cd bundle && zip -r ../initial.zip . && cd ..

# Upload to S3 and create first EB version
aws s3 cp initial.zip s3://bem12-eb-deployments/initial.zip

aws elasticbeanstalk create-application-version \
  --application-name bem12-app \
  --version-label v0-initial \
  --source-bundle S3Bucket=bem12-eb-deployments,S3Key=initial.zip

aws elasticbeanstalk update-environment \
  --environment-name bem12-env \
  --version-label v0-initial
```

---

## GitHub Secrets

Add these under **Settings → Secrets and variables → Actions → New secret**:

| Secret | Value |
|--------|-------|
| `AWS_ACCESS_KEY_ID` | Access key of the IAM user with EB + deployment bucket permissions |
| `AWS_SECRET_ACCESS_KEY` | Matching secret key |
| `S3_BUCKET_NAME` | `bem12-eb-deployments` |

---

## IAM User for GitHub Actions

Create a dedicated least-privilege IAM user — never use root credentials.

1. **IAM → Users → Create user**
2. **User name:** `bem12-github-actions`
3. **Access type:** Programmatic access only (no console login)
4. Attach the inline policy from **section 1.3** above
5. **Create access key** → download and add to GitHub Secrets

---

## Summary Checklist

```
S3
  ☐ bem12-eb-deployments created (private, versioning on, AES-256)
  ☐ Lifecycle rule: expire bundles after 90 days
  ☐ bem12-profiles created (public read on profiles/* only, AES-256)
  ☐ Bucket policy applied on bem12-profiles

IAM
  ☐ GitHub Actions user: bem12-github-actions
      ☐ S3 deploy policy attached (PutObject on bem12-eb-deployments)
      ☐ EB deploy policy attached
  ☐ EB EC2 instance role updated
      ☐ S3 profile upload policy (PutObject on bem12-profiles/profiles/*)
      ☐ DynamoDB table policy (PutItem/Scan/etc on bem12-visits)

DynamoDB
  ☐ bem12-users table created (partition key: email, on-demand)
      ☐ EC2 role policy: PutItem/GetItem/UpdateItem/Scan/DescribeTable on bem12-users
  ☐ bem12-visits table created (partition key: visitId, on-demand)
      ☐ EC2 role policy: PutItem/GetItem/Scan/DescribeTable on bem12-visits

Elastic Beanstalk
  ☐ Application bem12-app created
  ☐ Environment bem12-env created (Corretto 17, load balanced)
  ☐ Environment variables configured
  ☐ Instance profile set to the updated EC2 role
  ☐ Health check path set to /health
  ☐ Initial version uploaded and deployed

GitHub
  ☐ AWS_ACCESS_KEY_ID secret added
  ☐ AWS_SECRET_ACCESS_KEY secret added
  ☐ S3_BUCKET_NAME = bem12-eb-deployments secret added
  ☐ Branch protection on main: require CI status checks before merge
```
aws.md — added bem12-users table creation steps, its IAM policy (PutItem, GetItem, UpdateItem, Scan, DescribeTable), and the new EB env var to the checklist.