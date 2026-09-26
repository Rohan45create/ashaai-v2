#!/bin/bash
# ----------------------------------------------------------------------------------
# Provision Azure Resources for AshaAI
# This script provisions:
# 1. Resource Group
# 2. Azure App Service Plan (Free F1) + Web App (Spring Boot Backend)
# 3. Azure Container Apps Environment + Container App (AI Service, min-replicas 0)
# 4. Azure Static Web App (React Frontend, Free tier)
# ----------------------------------------------------------------------------------

set -e

# Configuration Variables
RESOURCE_GROUP="ashaai"
# Azure for Students sys.regionrestriction limits deployments to:
# ["centralindia", "eastasia", "koreacentral", "malaysiawest", "uaenorth"].
# Defaulting to centralindia for closest proximity and compliance.
LOCATION="centralindia"

# Backend Variables
APP_SERVICE_PLAN="ashaai-plan"
WEBAPP_NAME="ashaai-back" # Must be globally unique

# AI Service Variables (Docker Hub is used for image hosting instead of ACR to stay 100% free)
CONTAINER_APP_ENV="ashaai-env"
CONTAINER_APP_NAME="ashaai-ai-service"

# Frontend Variables
SWA_NAME="ashaai-frontend"

echo "Logging in to Azure..."
az login

echo "Creating Resource Group: $RESOURCE_GROUP in $LOCATION..."
az group create --name $RESOURCE_GROUP --location $LOCATION

# ----------------------------------------------------------------------------------
# 1. Backend (Spring Boot) - Azure App Service
# ----------------------------------------------------------------------------------
echo "Creating App Service Plan (Free F1 tier)..."
az appservice plan create \
    --name $APP_SERVICE_PLAN \
    --resource-group $RESOURCE_GROUP \
    --sku F1 \
    --is-linux

echo "Creating Web App for Spring Boot (Java 21)..."
az webapp create \
    --resource-group $RESOURCE_GROUP \
    --plan $APP_SERVICE_PLAN \
    --name $WEBAPP_NAME \
    --runtime "JAVA|21-java21"

echo "Web App $WEBAPP_NAME created."
echo "IMPORTANT: Get the publish profile to put in GitHub Secrets (AZURE_WEBAPP_PUBLISH_PROFILE):"
echo "  az webapp deployment list-publishing-profiles --name $WEBAPP_NAME --resource-group $RESOURCE_GROUP --xml"

# ----------------------------------------------------------------------------------
# 2. AI Service (Python) - Azure Container Apps (Free Consumption Tier)
# Note: Azure Container Registry (ACR) Basic costs ~$5/month and is NOT free.
# The GitHub Actions workflow (deploy-ai-service.yml) builds and pushes to Docker Hub.
# ----------------------------------------------------------------------------------
echo "Ensuring Container App CLI extension is installed..."
az extension add --name containerapp --upgrade --yes

echo "Creating Azure Container Apps Environment..."
az containerapp env create \
    --name $CONTAINER_APP_ENV \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION

echo "Creating Azure Container App with min-replicas 0 (Consumption Plan)..."
az containerapp create \
    --name $CONTAINER_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --environment $CONTAINER_APP_ENV \
    --image mcr.microsoft.com/azuredocs/containerapps-helloworld:latest \
    --target-port 8000 \
    --ingress external \
    --min-replicas 0 \
    --max-replicas 1 \
    --query configuration.ingress.fqdn

# ----------------------------------------------------------------------------------
# 3. Frontend (React) - Azure Static Web Apps (Free Tier)
# ----------------------------------------------------------------------------------
echo "Creating Azure Static Web App ($SWA_NAME)..."
# Create via CLI or Azure Portal. Note: SWA supports centralindia or closest available region.
az staticwebapp create \
    --name $SWA_NAME \
    --resource-group $RESOURCE_GROUP \
    --location "centralindia" \
    --sku Free || echo "If CLI creation fails for region, create via Azure Portal (portal.azure.com) -> Static Web Apps."

echo "============================================================"
echo "Provisioning commands complete."
echo "Please remember to:"
echo "1. Get the Web App publish profile for backend:"
echo "   az webapp deployment list-publishing-profiles --name $WEBAPP_NAME --resource-group $RESOURCE_GROUP --xml"
echo "   Add as GitHub Secret: AZURE_WEBAPP_PUBLISH_PROFILE"
echo "2. Get the Static Web App deployment token:"
echo "   az staticwebapp secrets list --name $SWA_NAME --resource-group $RESOURCE_GROUP --query 'properties.apiKey' -o tsv"
echo "   Add as GitHub Secret: AZURE_STATIC_WEB_APPS_API_TOKEN"
echo "3. Create a service principal for Container App deployer:"
echo "   az ad sp create-for-rbac --name 'ashaai-deployer' --role contributor --scopes /subscriptions/<SUBSCRIPTION_ID>/resourceGroups/$RESOURCE_GROUP --sdk-auth"
echo "   Add as GitHub Secret: AZURE_CREDENTIALS"
echo "4. Add Docker Hub secrets for AI service deployment:"
echo "   DOCKERHUB_USERNAME, DOCKERHUB_TOKEN"
echo "5. Add backend URL as secret for frontend build & cron:"
echo "   BACKEND_API_URL=https://$WEBAPP_NAME.azurewebsites.net"
echo "============================================================"

