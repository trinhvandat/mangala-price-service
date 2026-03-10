#!/bin/bash
# =============================================================================
# SSL Setup Script for mangala-price-service
# =============================================================================
# Run this AFTER nginx is installed and DNS is pointing to your VPS
# Usage: sudo ./setup-ssl.sh
# =============================================================================

set -e

DOMAIN="price-staging.mangala.dev"
EMAIL="admin@mangala.dev"  # Change to your email

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Setting up SSL for ${DOMAIN}...${NC}"

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Please run as root (sudo ./setup-ssl.sh)${NC}"
    exit 1
fi

# Install certbot if not present
if ! command -v certbot &> /dev/null; then
    echo -e "${YELLOW}Installing Certbot...${NC}"
    apt-get update
    apt-get install -y certbot python3-certbot-nginx
fi

# Install nginx if not present
if ! command -v nginx &> /dev/null; then
    echo -e "${YELLOW}Installing Nginx...${NC}"
    apt-get update
    apt-get install -y nginx
fi

# Create certbot webroot directory
mkdir -p /var/www/certbot

# Copy nginx config (HTTP only first, for certbot challenge)
cat > /etc/nginx/sites-available/price-service.conf << 'NGINX_TEMP'
server {
    listen 80;
    listen [::]:80;
    server_name price-staging.mangala.dev;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX_TEMP

# Enable site
ln -sf /etc/nginx/sites-available/price-service.conf /etc/nginx/sites-enabled/

# Test and reload nginx
nginx -t
systemctl reload nginx

echo -e "${YELLOW}Requesting SSL certificate...${NC}"

# Get SSL certificate
certbot certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --domain ${DOMAIN} \
    --email ${EMAIL} \
    --agree-tos \
    --no-eff-email

# Now install the full HTTPS config
echo -e "${YELLOW}Installing full HTTPS configuration...${NC}"

# Copy the full nginx config from the repo
cp /path/to/your/repo/nginx/price-service.conf /etc/nginx/sites-available/price-service.conf

# Test and reload
nginx -t
systemctl reload nginx

# Setup auto-renewal
echo -e "${YELLOW}Setting up auto-renewal...${NC}"
systemctl enable certbot.timer
systemctl start certbot.timer

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✅ SSL Setup Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Your service is now available at:"
echo "  https://${DOMAIN}"
echo ""
echo "SSL certificate will auto-renew via certbot.timer"
