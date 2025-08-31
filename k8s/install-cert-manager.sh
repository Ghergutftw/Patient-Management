#!/bin/bash

# Script to install cert-manager for automatic SSL certificate management
# This will handle Let's Encrypt certificates automatically

echo "Installing cert-manager..."

# Install cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.2/cert-manager.yaml

echo "Waiting for cert-manager to be ready..."
kubectl wait --namespace cert-manager \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/name=cert-manager \
  --timeout=300s

echo "Creating Let's Encrypt ClusterIssuer..."

# Create ClusterIssuer for Let's Encrypt
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: your-email@example.com  # CHANGE THIS TO YOUR EMAIL
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
EOF

echo "cert-manager installation completed!"
echo ""
echo "IMPORTANT: Update the email address in the ClusterIssuer above to your actual email!"
echo "You can edit it with: kubectl edit clusterissuer letsencrypt-prod"
