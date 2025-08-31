#!/bin/bash

# Complete deployment script for Patient Management System with Domain Integration
# Run this script on your Kubernetes master node after setting up Jenkins

set -e  # Exit on any error

echo "🚀 Starting Patient Management System Deployment with Domain Integration"
echo "=================================================================="

# Configuration
NAMESPACE="patient-management"
DOMAIN="krunky.xyz"
EMAIL="madalinghergut@gmail.com"

echo "📋 Configuration:"
echo "   Namespace: $NAMESPACE"
echo "   Domain: $DOMAIN"
echo "   Email: $EMAIL"
echo ""

# Step 1: Check prerequisites
echo "🔍 Checking prerequisites..."
if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl is not installed"
    exit 1
fi

if ! command -v helm &> /dev/null; then
    echo "⚠️  Helm is not installed. Will use kubectl apply method for ingress."
    USE_HELM=false
else
    echo "✅ Helm is available"
    USE_HELM=true
fi

# Step 2: Create namespace and apply basic manifests
echo "📦 Creating namespace and applying manifests..."
kubectl apply -f k8s/patient-management-namespace.yaml
kubectl apply -f k8s/postgres-configmap.yaml
kubectl apply -f k8s/infrastructure.yaml
kubectl apply -f k8s/microservices.yaml

echo "⏳ Waiting for services to start (this may take a few minutes)..."
sleep 30

# Step 3: Install NGINX Ingress Controller
echo "🌐 Installing NGINX Ingress Controller..."
if [ "$USE_HELM" = true ]; then
    helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
    helm repo update
    
    helm install ingress-nginx ingress-nginx/ingress-nginx \
        --namespace ingress-nginx \
        --create-namespace \
        --set controller.service.type=LoadBalancer \
        --set controller.service.externalTrafficPolicy=Local \
        --set controller.admissionWebhooks.enabled=false
else
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.1/deploy/static/provider/cloud/deploy.yaml
fi

echo "⏳ Waiting for NGINX Ingress Controller to be ready..."
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=300s

# Step 4: Install cert-manager
echo "🔐 Installing cert-manager for SSL certificates..."
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.2/cert-manager.yaml

echo "⏳ Waiting for cert-manager to be ready..."
kubectl wait --namespace cert-manager \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/name=cert-manager \
  --timeout=300s

# Step 5: Create ClusterIssuer
echo "📜 Creating Let's Encrypt ClusterIssuer..."
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: $EMAIL
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
EOF

# Step 6: Apply ingress configuration
echo "🌍 Applying ingress configuration..."
kubectl apply -f k8s/ingress.yaml

# Step 7: Get LoadBalancer IP
echo "🔍 Getting LoadBalancer External IP..."
EXTERNAL_IP=""
while [ -z "$EXTERNAL_IP" ]; do
    echo "Waiting for external IP..."
    EXTERNAL_IP=$(kubectl get service ingress-nginx-controller --namespace ingress-nginx --template="{{range .status.loadBalancer.ingress}}{{.ip}}{{end}}")
    [ -z "$EXTERNAL_IP" ] && sleep 10
done

echo ""
echo "🎉 Deployment completed successfully!"
echo "=================================================================="
echo ""
echo "📊 System Status:"
echo "   External IP: $EXTERNAL_IP"
echo "   Namespace: $NAMESPACE"
echo ""
echo "🌐 Services:"
kubectl get pods -n $NAMESPACE
echo ""
echo "🔗 LoadBalancer Services:"
kubectl get service ingress-nginx-controller --namespace ingress-nginx
echo ""
echo "📋 Next Steps:"
echo "1. Configure DNS records in GoDaddy:"
echo "   - A record: @ -> $EXTERNAL_IP"
echo "   - A record: api -> $EXTERNAL_IP"
echo "   - A record: grafana -> $EXTERNAL_IP"
echo ""
echo "2. Wait for DNS propagation (5-60 minutes)"
echo ""
echo "3. Test your endpoints:"
echo "   - API Gateway: https://api.$DOMAIN/actuator/health"
echo "   - Grafana: https://grafana.$DOMAIN"
echo ""
echo "4. Monitor certificate generation:"
echo "   kubectl get certificate -n $NAMESPACE"
echo ""
echo "📖 For detailed DNS configuration, see: k8s/DNS_CONFIGURATION.md"
echo ""
echo "🎯 Your Patient Management System is now ready for external access!"

# Optional: Show running services
echo ""
echo "🔍 Current Pod Status:"
kubectl get pods -n $NAMESPACE -o wide

echo ""
echo "📊 Service Status:"
kubectl get services -n $NAMESPACE
