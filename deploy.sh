#!/bin/bash
# =============================================================================
# mangala-price-service - Staging Deployment Script
# =============================================================================
# Run this script on your VPS after cloning the repository
# Usage: ./deploy.sh
# =============================================================================

set -e

echo "🚀 Deploying mangala-price-service (staging)..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo -e "${RED}❌ Docker Compose is not installed. Please install Docker Compose first.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Prerequisites OK${NC}"

# Setup environment
echo -e "${YELLOW}Setting up environment...${NC}"

if [ ! -f .env ]; then
    if [ -f .env.staging ]; then
        cp .env.staging .env
        echo -e "${GREEN}✅ Created .env from .env.staging${NC}"
    else
        echo -e "${RED}❌ No .env.staging found. Please create .env file.${NC}"
        exit 1
    fi
fi

# Pull latest changes (if git repo)
if [ -d .git ]; then
    echo -e "${YELLOW}Pulling latest changes...${NC}"
    git pull origin main || git pull origin master || echo "Could not pull, continuing..."
fi

# Build and deploy
echo -e "${YELLOW}Building and deploying services...${NC}"

# Use docker compose (v2) or docker-compose (v1)
if docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
else
    COMPOSE_CMD="docker-compose"
fi

# Stop existing services
$COMPOSE_CMD -f docker-compose.staging.yml down --remove-orphans || true

# Build fresh
$COMPOSE_CMD -f docker-compose.staging.yml build --no-cache

# Start services
$COMPOSE_CMD -f docker-compose.staging.yml up -d

echo -e "${YELLOW}Waiting for services to be healthy...${NC}"
sleep 10

# Check health
echo -e "${YELLOW}Checking service health...${NC}"

MAX_RETRIES=30
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Service is healthy!${NC}"
        break
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo "Waiting for service to be ready... ($RETRY_COUNT/$MAX_RETRIES)"
    sleep 5
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    echo -e "${RED}❌ Service did not become healthy in time${NC}"
    echo "Checking logs..."
    $COMPOSE_CMD -f docker-compose.staging.yml logs --tail=50 price-service
    exit 1
fi

# Show status
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✅ Deployment Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Service Status:"
$COMPOSE_CMD -f docker-compose.staging.yml ps
echo ""
echo "Endpoints:"
echo "  - Health: http://localhost:8080/actuator/health"
echo "  - Metrics: http://localhost:8080/actuator/metrics"
echo "  - Info: http://localhost:8080/actuator/info"
echo ""
echo "Useful commands:"
echo "  - Logs: docker compose -f docker-compose.staging.yml logs -f price-service"
echo "  - Stop: docker compose -f docker-compose.staging.yml down"
echo "  - Restart: docker compose -f docker-compose.staging.yml restart price-service"
