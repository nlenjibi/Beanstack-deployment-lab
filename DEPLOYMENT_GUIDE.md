# Elastic Beanstalk CI/CD Lab - Complete Implementation Guide

## Quick Start

### For Java (Spring Boot) Application

```bash
# 1. Navigate to Java app directory
cd bem12-app

# 2. Build the application
mvn clean package -DskipTests

# 3. Create deployment package
mkdir -p deploy
cp target/*.jar deploy/application.jar
cd deploy && zip -r ../bem12-app.zip . && cd ..

# 4. Upload to S3
aws s3 cp bem12-app.zip s3://your-bucket-name/bem12-app.zip

# 5. Deploy to Elastic Beanstalk
aws elasticbeanstalk create-application-version \
  --application-name bem12-app \
  --version-label v1 \
  --source-bundle S3Bucket=your-bucket-name,S3Key=bem12-app.zip

aws elasticbeanstalk update-environment \
  --environment-name bem12-env \
  --version-label v1
```

### For Node.js Application

```bash
# 1. Navigate to Node.js app directory
cd bem12-node-app

# 2. Install dependencies
npm ci

# 3. Create deployment package
mkdir -p deploy
cp -r *.js node_modules package*.json deploy/
cd deploy && zip -r ../bem12-node-app.zip . && cd ..

# 4. Upload to S3
aws s3 cp bem12-node-app.zip s3://your-bucket-name/bem12-node-app.zip

# 5. Deploy to Elastic Beanstalk
aws elasticbeanstalk create-application-version \
  --application-name bem12-node-app \
  --version-label v1 \
  --source-bundle S3Bucket=your-bucket-name,S3Key=bem12-node-app.zip

aws elasticbeanstalk update-environment \
  --environment-name bem12-node-env \
  --version-label v1
```

## File Structure

```
bem12-lab/
├── README.md                          # Main documentation
├── cloudformation-template.yml        # Infrastructure as Code
├── deploy-to-eb.sh                    # Manual deployment script
├── .github/
│   └── workflows/
│       ├── deploy.yml                 # Java CI/CD pipeline
│       └── deploy-node.yml            # Node.js CI/CD pipeline
├── .ebextensions/
│   └── 01-environment.config          # EB environment config
├── Procfile                           # Application process definition
├── bem12-app/                         # Java Spring Boot Application
│   ├── pom.xml
│   └── src/main/java/com/bem12/app/
│       ├── BEM12Application.java
│       └── BEM12Controller.java
└── bem12-node-app/                    # Node.js Application
    ├── package.json
    └── server.js
```

## Configuration Details

### Elastic Beanstalk Environment Settings

| Setting | Value | Description |
|---------|-------|-------------|
| Platform | Amazon Linux 2 | Operating system |
| Runtime | Corretto 17 / Node.js 18 | Application runtime |
| Environment Type | Load Balanced | Auto-scaling enabled |
| Min Instances | 1 | Minimum EC2 instances |
| Max Instances | 3 | Maximum EC2 instances |
| Health Check | /health | Application health endpoint |

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `NODE_ENV` | production | Application environment |
| `app.version` | 1.0.0 | Application version |
| `external.service.name` | Local | External service identifier |
| `SPRING_DATASOURCE_URL` | - | Database connection (optional) |
| `DYNAMODB_TABLE` | - | DynamoDB table name (optional) |

## CI/CD Workflow Steps

### Java Application Pipeline

1. **Trigger**: Code push to main/dev branch
2. **Checkout**: Clone repository
3. **Setup**: Install JDK 17
4. **Build**: Compile with Maven
5. **Package**: Create ZIP archive
6. **Upload**: Store in S3 bucket
7. **Version**: Create EB application version
8. **Deploy**: Update EB environment
9. **Verify**: Test deployment endpoints

### Node.js Application Pipeline

1. **Trigger**: Code push to main/dev branch
2. **Checkout**: Clone repository
3. **Setup**: Install Node.js 18
4. **Install**: Install npm dependencies
5. **Package**: Create ZIP archive
6. **Upload**: Store in S3 bucket
7. **Version**: Create EB application version
8. **Deploy**: Update EB environment
9. **Verify**: Test deployment endpoints

## Testing

### Manual Testing

```bash
# Get environment URL
URL=$(aws elasticbeanstalk describe-environments \
  --environment-names bem12-env \
  --query 'Environments[0].CNAME' \
  --output text)

# Test root endpoint
curl http://$URL/

# Test health endpoint
curl http://$URL/health

# Test info endpoint
curl http://$URL/info
```

### Automated Testing

```bash
# Local testing
mvn test

# Integration testing
mvn verify -Pintegration
```

## Monitoring

### CloudWatch Logs

```bash
# Request logs
aws elasticbeanstalk request-environment-info \
  --environment-name bem12-env \
  --info-type tail

# Retrieve logs
aws elasticbeanstalk retrieve-environment-info \
  --environment-name bem12-env \
  --info-type tail
```

### Environment Events

```bash
aws elasticbeanstalk describe-events \
  --environment-name bem12-env \
  --max-items 10
```

## Troubleshooting Common Issues

### Issue: Deployment Timeout

**Solution**: Increase timeout in GitHub Actions workflow
```yaml
- name: Wait for environment update
  run: |
    aws elasticbeanstalk wait environment-updated \
      --environment-name ${{ env.ENVIRONMENT_NAME }} \
      --timeout 900
```

### Issue: Health Check Failed

**Solution**: Verify health endpoint returns 200
```java
@GetMapping("/health")
public ResponseEntity<String> health() {
    return ResponseEntity.ok("UP");
}
```

### Issue: Memory Limit Exceeded

**Solution**: Increase instance size or optimize application
```yaml
- Namespace: aws:autoscaling:launchconfiguration
  OptionName: InstanceType
  Value: t3.small
```

## Best Practices

1. **Version Control**: Tag all releases
2. **Environment Separation**: Use separate environments for dev/staging/prod
3. **Secrets Management**: Use AWS Secrets Manager or Parameter Store
4. **Monitoring**: Enable CloudWatch alarms
5. **Backup**: Regular S3 versioning
6. **Security**: Regular security updates and audits
7. **Documentation**: Keep architecture and deployment docs current

## Performance Optimization

### Auto-scaling Configuration

```yaml
- Namespace: aws:autoscaling:trigger
  OptionName: MeasureName
  Value: CPUUtilization

- Namespace: aws:autoscaling:trigger
  OptionName: Statistic
  Value: Average

- Namespace: aws:autoscaling:trigger
  OptionName: Unit
  Value: Percent

- Namespace: aws:autoscaling:trigger
  OptionName: LowerThreshold
  Value: 30

- Namespace: aws:autoscaling:trigger
  OptionName: UpperThreshold
  Value: 70
```

### Caching Configuration

```yaml
- Namespace: aws:elasticbeanstalk:environment:process:default
  OptionName: StickinessEnabled
  Value: true
```

## Security Checklist

- [ ] AWS credentials stored in GitHub Secrets
- [ ] S3 bucket encryption enabled
- [ ] IAM roles follow least privilege
- [ ] Security groups restrict access
- [ ] Environment variables don't contain secrets
- [ ] Application uses HTTPS
- [ ] Regular dependency updates
- [ ] Vulnerability scanning enabled
- [ ] Audit logs enabled
- [ ] Backup strategy defined

## Rollback Procedure

```bash
# List available versions
aws elasticbeanstalk describe-application-versions \
  --application-name bem12-app

# Rollback to previous version
aws elasticbeanstalk update-environment \
  --environment-name bem12-env \
  --version-label v1.0.0

# Verify rollback
aws elasticbeanstalk describe-environments \
  --environment-names bem12-env \
  --query 'Environments[0].VersionLabel'
```

## Cost Optimization

1. **Use t3.micro** for development
2. **Auto-scale** to zero for non-production
3. **Reserved Instances** for production
4. **S3 Lifecycle** policies for old versions
5. **CloudWatch** log retention policies
6. **Terminate** unused environments

## Additional Resources

- [AWS Elastic Beanstalk CLI](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/eb-cli3.html)
- [Spring Boot on AWS](https://aws.amazon.com/spring/)
- [GitHub Actions for AWS](https://github.com/aws-actions)
- [Elastic Beanstalk Best Practices](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/eb-best-practices.html)
