#!/bin/bash
# deploy-to-eb.sh - Manual deployment script for Elastic Beanstalk

set -e

# Configuration
APPLICATION_NAME="bem12-app"
ENVIRONMENT_NAME="bem12-env"
S3_BUCKET="bem12-app-deployment-bucket"
VERSION_LABEL="v$(date +%Y%m%d-%H%M%S)"
REGION="us-east-1"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Elastic Beanstalk Deployment Script${NC}"
echo -e "${GREEN}========================================${NC}"

# Function to print colored messages
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Step 1: Build application
print_info "Step 1: Building application..."
if [ -f "pom.xml" ]; then
    mvn clean package -DskipTests
    ARTIFACT="target/*.jar"
    APP_TYPE="Java"
elif [ -f "package.json" ]; then
    npm ci
    ARTIFACT="."
    APP_TYPE="Node.js"
else
    print_error "No build file found (pom.xml or package.json)"
    exit 1
fi

print_info "Application type: $APP_TYPE"

# Step 2: Create deployment package
print_info "Step 2: Creating deployment package..."
mkdir -p deploy

if [ "$APP_TYPE" == "Java" ]; then
    cp $ARTIFACT deploy/application.jar
else
    cp -r *.js node_modules package*.json deploy/ 2>/dev/null || true
fi

# Copy configuration files
cp -r .ebextensions deploy/ 2>/dev/null || true
cp Procfile deploy/ 2>/dev/null || true

# Create ZIP
cd deploy
zip -r ../deployment.zip .
cd ..

print_info "Deployment package created: deployment.zip"
ls -lh deployment.zip

# Step 3: Upload to S3
print_info "Step 3: Uploading to S3..."
aws s3 cp deployment.zip s3://$S3_BUCKET/$VERSION_LABEL.zip --region $REGION

if [ $? -eq 0 ]; then
    print_info "Uploaded to s3://$S3_BUCKET/$VERSION_LABEL.zip"
else
    print_error "Failed to upload to S3"
    exit 1
fi

# Step 4: Create application version
print_info "Step 4: Creating Elastic Beanstalk application version..."
aws elasticbeanstalk create-application-version \
    --application-name $APPLICATION_NAME \
    --version-label $VERSION_LABEL \
    --source-bundle S3Bucket=$S3_BUCKET,S3Key=$VERSION_LABEL.zip \
    --description "Manual deployment $VERSION_LABEL" \
    --region $REGION

if [ $? -eq 0 ]; then
    print_info "Application version created: $VERSION_LABEL"
else
    print_error "Failed to create application version"
    exit 1
fi

# Step 5: Deploy to environment
print_info "Step 5: Deploying to Elastic Beanstalk environment..."
aws elasticbeanstalk update-environment \
    --environment-name $ENVIRONMENT_NAME \
    --version-label $VERSION_LABEL \
    --region $REGION

if [ $? -eq 0 ]; then
    print_info "Deployment initiated"
else
    print_error "Failed to update environment"
    exit 1
fi

# Step 6: Wait for deployment
print_info "Step 6: Waiting for deployment to complete..."
aws elasticbeanstalk wait environment-updated \
    --environment-name $ENVIRONMENT_NAME \
    --region $REGION \
    --timeout 600

if [ $? -eq 0 ]; then
    print_info "Deployment completed successfully!"
else
    print_error "Deployment failed or timed out"
    exit 1
fi

# Step 7: Get application URL
print_info "Step 7: Getting application URL..."
URL=$(aws elasticbeanstalk describe-environments \
    --environment-names $ENVIRONMENT_NAME \
    --region $REGION \
    --query 'Environments[0].CNAME' \
    --output text)

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Deployment Successful!${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "Application URL: ${GREEN}http://$URL${NC}"
echo -e "Version: ${GREEN}$VERSION_LABEL${NC}"
echo ""

# Step 8: Test endpoints
print_info "Testing application endpoints..."
echo ""
echo "Testing /health endpoint:"
curl -s http://$URL/health | python3 -m json.tool 2>/dev/null || curl -s http://$URL/health

echo ""
echo "Testing / endpoint:"
curl -s http://$URL/ | python3 -m json.tool 2>/dev/null || curl -s http://$URL/

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  All Done!${NC}"
echo -e "${GREEN}========================================${NC}"

# Cleanup
rm -f deployment.zip
rm -rf deploy
