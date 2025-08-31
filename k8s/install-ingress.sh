#!/bin/bash

# Script to install NGINX Ingress Controller for Patient Management System
# Run this on your Kubernetes master node

echo "Installing NGINX Ingress Controller..."

# Method 1: Using Helm (Recommended)
if command -v helm &> /dev/null; then
    echo "Installing via Helm..."
    helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
    helm repo update
    
    helm install ingress-nginx ingress-nginx/ingress-nginx \
        --namespace ingress-nginx \
        --create-namespace \
        --set controller.service.type=LoadBalancer \
        --set controller.service.externalTrafficPolicy=Local \
        --set controller.admissionWebhooks.enabled=false
else
    # Method 2: Using kubectl apply
    echo "Installing via kubectl..."
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.1/deploy/static/provider/cloud/deploy.yaml
fi

echo "Waiting for NGINX Ingress Controller to be ready..."
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=300s

echo "Getting LoadBalancer IP..."
kubectl get service ingress-nginx-controller --namespace=ingress-nginx

echo "NGINX Ingress Controller installation completed!"
echo ""
echo "Next steps:"
echo "1. Note the LoadBalancer EXTERNAL-IP above"
echo "2. Configure your DNS records to point to this IP"
echo "3. Apply the ingress configuration: kubectl apply -f k8s/ingress.yaml"
