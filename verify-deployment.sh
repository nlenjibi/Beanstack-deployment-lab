#!/bin/bash
# verify-deployment.sh - Verify Elastic Beanstalk deployment

set -e

# Configuration
APPLICATION_NAME="bem12-app"
ENVIRONMENT_NAME="bem12-env"
REGION="us-east-1"

echo "======================================"
echo "  BEM12 Deployment Verification"
echo "======================================"
echo ""

# Check environment status
echo "[1/5] Checking Elastic Beanstalk environment status..."
STATUS=$(aws elasticbeanstalk describe-environments \
  --environment-names $ENVIRONMENT_NAME \
  --region $REGION \
  --query 'Environments[0].Status' \
  --output text 2>/dev/null || echo "NOT_FOUND")

if [ "$STATUS" == "Ready" ]; then
    echo "   Environment status: $STATUS"
else
    echo "   Environment status: $STATUS"
    echo "  Warning: Environment not in 'Ready' state"
fi

# Get application URL
echo ""
echo "[2/5] Getting application URL..."
URL=$(aws elasticbeanstalk describe-environments \
  --environment-names $ENVIRONMENT_NAME \
  --region $REGION \
  --query 'Environments[0].CNAME' \
  --output text 2>/dev/null || echo "")

if [ -n "$URL" ]; then
    echo "   Application URL: http://$URL"
else
    echo "   Could not retrieve URL"
    exit 1
fi

# Test health endpoint
echo ""
echo "[3/5] Testing health endpoint..."
HEALTH_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://$URL/health 2>/dev/null || echo "000")
if [ "$HEALTH_RESPONSE" == "200" ]; then
    echo "   Health check: PASSED (HTTP $HEALTH_RESPONSE)"
    curl -s http://$URL/health
    echo ""
else
    echo "   Health check: FAILED (HTTP $HEALTH_RESPONSE)"
fi

# Test root endpoint
echo ""
echo "[4/5] Testing root endpoint..."
ROOT_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://$URL/ 2>/dev/null || echo "000")
if [ "$ROOT_RESPONSE" == "200" ]; then
    echo "   Root endpoint: PASSED (HTTP $ROOT_RESPONSE)"
    curl -s http://$URL/ | python3 -m json.tool 2>/dev/null || curl -s http://$URL/
    echo ""
else
    echo "   Root endpoint: FAILED (HTTP $ROOT_RESPONSE)"
fi

# Check version
echo ""
echo "[5/5] Checking application version..."
VERSION=$(aws elasticbeanstalk describe-environments \
  --environment-names $ENVIRONMENT_NAME \
  --region $REGION \
  --query 'Environments[0].VersionLabel' \
  --output text 2>/dev/null || echo "unknown")
echo "   Current version: $VERSION"

echo ""
echo "======================================"
echo "  Verification Complete"
echo "======================================"
echo ""
echo "Application: $APPLICATION_NAME"
echo "Environment: $ENVIRONMENT_NAME"
echo "URL: http://$URL"
echo "Status: $STATUS"
echo "Version: $VERSION"
echo ""
/users/