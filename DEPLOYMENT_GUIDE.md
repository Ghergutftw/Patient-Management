# Patient Management System - Kubernetes Deployment Guide

This guide provides complete step-by-step instructions for deploying the Patient Management System using Jenkins CI/CD pipeline and Kubernetes.

## Prerequisites

- Kubernetes cluster (1 master + 2 worker nodes)
- Jenkins server with Docker and kubectl installed
- Docker Hub account
- Git repository access

## 1. Initial Setup - Kubernetes Cluster

```bash
# Verify cluster status
kubectl cluster-info
kubectl get nodes

# Check cluster components
kubectl get pods -n kube-system
```

## 2. Create Service Account and RBAC for Jenkins

```bash
# Create namespace for the application
kubectl create namespace patient-management

# Create service account for Jenkins deployment
kubectl create serviceaccount jenkins-deployer -n patient-management

# Create cluster role binding with full admin permissions
kubectl create clusterrolebinding jenkins-deployer-admin \
  --clusterrole=cluster-admin \
  --serviceaccount=patient-management:jenkins-deployer

# Generate long-lived token for Jenkins (8760h = 1 year)
kubectl create token jenkins-deployer -n patient-management --duration=8760h

# Get cluster information for kubeconfig setup
kubectl cluster-info

# Verify service account creation
kubectl get serviceaccount jenkins-deployer -n patient-management
kubectl describe clusterrolebinding jenkins-deployer-admin
```

## 3. Configure Jenkins Credentials

### Option A: Copy kubeconfig file to Jenkins VM

```bash
# On Kubernetes Master - Generate kubeconfig for Jenkins
kubectl config view --raw > /tmp/jenkins-kubeconfig

# Copy kubeconfig to Jenkins VM (replace <jenkins-vm-ip> with actual IP)
scp /tmp/jenkins-kubeconfig root@<jenkins-vm-ip>:/var/lib/jenkins/.kube/config

# Set proper permissions
ssh root@<jenkins-vm-ip> "chown jenkins:jenkins /var/lib/jenkins/.kube/config"

# Test kubectl access from Jenkins VM
ssh root@<jenkins-vm-ip> "kubectl get nodes"
```

### Option B: Configure via Jenkins Web UI

```bash
# Generate kubeconfig content
kubectl config view --raw

# In Jenkins Web UI:
# 1. Go to Jenkins → Manage Jenkins → Manage Credentials
# 2. Click "Add Credentials"
# 3. Select "Secret file" or "Secret text"
# 4. ID: kubeconfig-credentials
# 5. Paste the kubeconfig content
```

## 4. Prepare Project Repository

```bash
# Navigate to your project directory
cd /path/to/patient-management

# Check current status
git status
git branch

# Add Kubernetes manifests and Jenkins pipeline
git add k8s/
git add Jenkinsfile
git add DEPLOYMENT_GUIDE.md

# Commit changes
git commit -m "Add Kubernetes manifests, Jenkins pipeline, and deployment guide"

# Push to repository
git push origin k8s
```

## 5. Manual Kubernetes Deployment (Optional)

If you want to deploy manually before setting up Jenkins:

```bash
# Apply manifests in correct order
kubectl apply -f k8s/patient-management-namespace.yaml
kubectl apply -f k8s/postgres-configmap.yaml
kubectl apply -f k8s/infrastructure.yaml

# Wait for infrastructure to be ready
kubectl wait --for=condition=available --timeout=300s deployment/patient-service-db -n patient-management
kubectl wait --for=condition=available --timeout=300s deployment/auth-service-db -n patient-management
kubectl wait --for=condition=available --timeout=300s deployment/kafka -n patient-management

# Deploy microservices
kubectl apply -f k8s/microservices.yaml

# Verify deployment
kubectl get all -n patient-management
```

## 6. Jenkins Pipeline Configuration

### Pipeline Features:
- **Dependency-based deployment**: Services deploy in proper order (databases → Kafka → auth-service → core services → API gateway)
- **Health checks**: Each layer waits for dependencies to be ready
- **Buffer periods**: Adequate time between service starts
- **Automatic rollback**: Failed deployments trigger automatic rollback
- **Service verification**: Pipeline reports access URLs and health status

### Pipeline Parameters:
- **DOCKER_TAG**: `latest` (or your preferred tag)
- **GIT_BRANCH**: `k8s`
- **BUILD_DOCKER_IMAGES**: `true`
- **PUSH_TO_DOCKER_REGISTRY**: `true`
- **DEPLOY_TO_K8S**: `true`
- **ENABLE_TRIVY_IMAGE_SCAN**: `false` (for faster builds)
- **CLEANUP_IMAGES**: `true`

### Required Jenkins Credentials:
- `kubeconfig-credentials`: Kubernetes configuration file
- `docker-hub-credentials`: Docker Hub username/password

### Deployment Stages:
1. **🏗️ Infrastructure**: Namespace, ConfigMaps
2. **🗄️ Databases**: PostgreSQL instances with health checks
3. **📨 Messaging**: Kafka with 30-second startup buffer
4. **🔐 Authentication**: auth-service deployed first
5. **🏥 Core Services**: patient-service, billing-service, analytics-service
6. **🚪 Gateway**: api-gateway (depends on all services)
7. **📊 Monitoring**: Grafana verification

## 7. Deployment Verification

### Check Pod Status
```bash
# Monitor pod deployment
kubectl get pods -n patient-management -w

# Check specific pod details
kubectl describe pod <pod-name> -n patient-management

# Check pod logs
kubectl logs -l app=api-gateway -n patient-management --tail=20
kubectl logs -l app=patient-service -n patient-management --tail=20
kubectl logs -l app=auth-service -n patient-management --tail=20
```

### Check Services and Endpoints
```bash
# List all services
kubectl get services -n patient-management

# Check service endpoints
kubectl get endpoints -n patient-management

# Detailed service information
kubectl describe service api-gateway -n patient-management
kubectl describe service grafana -n patient-management
```

### Database Connectivity Test
```bash
# Test patient database
kubectl exec -it deployment/patient-service-db -n patient-management -- psql -U myuser -d mydatabase -c "SELECT 1;"

# Test auth database
kubectl exec -it deployment/auth-service-db -n patient-management -- psql -U myuser -d mydatabase -c "SELECT 1;"
```

## 8. Application Access

### API Gateway Access

```bash
# Get NodePort for API Gateway
kubectl get service api-gateway -n patient-management

# Method 1: Direct NodePort access (replace <node-ip> and <nodeport>)
curl http://<node-ip>:<nodeport>/actuator/health

# Method 2: Port forwarding
kubectl port-forward service/api-gateway 9090:4004 -n patient-management --address 0.0.0.0 &
curl http://localhost:9090/actuator/health

# Method 3: Access from worker nodes
ssh root@<worker-node-ip> "curl http://localhost:<nodeport>/actuator/health"
```

### API Endpoints to Test
```bash
# Health check
curl http://<access-url>/actuator/health

# API documentation (if available)
curl http://<access-url>/swagger-ui.html

# Patient service endpoints
curl http://<access-url>/api/patients
curl -X POST http://<access-url>/api/patients -H "Content-Type: application/json" -d '{"name":"John Doe","age":30}'
```

### Grafana Monitoring Access

```bash
# Get Grafana service details
kubectl get service grafana -n patient-management

# Access Grafana dashboard
# URL: http://<node-ip>:<grafana-nodeport>
# Username: admin
# Password: admin
```

## 9. Troubleshooting Commands

### Dependency-Based Deployment Issues

**Check deployment order and status:**
```bash
# Check overall deployment status
kubectl get deployments -n patient-management

# Verify deployment order (should be: DBs → Kafka → auth → core services → api-gateway)
kubectl get deployments -n patient-management --sort-by=.metadata.creationTimestamp

# Check which services are stuck
kubectl get pods -n patient-management | grep -E "(Pending|CrashLoopBackOff|Error)"
```

**Database dependency issues:**
```bash
# Check if databases are ready before services start
kubectl get pods -l app=patient-service-db -n patient-management
kubectl get pods -l app=auth-service-db -n patient-management

# Test database connectivity
kubectl exec -it deployment/patient-service-db -n patient-management -- pg_isready -U myuser -d mydatabase
kubectl exec -it deployment/auth-service-db -n patient-management -- pg_isready -U myuser -d mydatabase
```

**Kafka dependency issues:**
```bash
# Check Kafka status
kubectl get pods -l app=kafka -n patient-management
kubectl logs -l app=kafka -n patient-management --tail=20

# Test Kafka connectivity
kubectl exec -it deployment/kafka -n patient-management -- kafka-topics.sh --bootstrap-server localhost:9092 --list
```

**Service startup sequence issues:**
```bash
# Check if auth-service is ready before other services
kubectl get pods -l app=auth-service -n patient-management
kubectl logs -l app=auth-service -n patient-management --tail=20

# Verify API Gateway waits for all services
kubectl describe deployment api-gateway -n patient-management
kubectl logs -l app=api-gateway -n patient-management --tail=30
```

### Pod Issues
```bash
# Check pod status and events
kubectl get pods -n patient-management
kubectl describe pod <pod-name> -n patient-management

# Check pod logs
kubectl logs <pod-name> -n patient-management --tail=50
kubectl logs <pod-name> -n patient-management --previous

# Execute commands in pod
kubectl exec -it <pod-name> -n patient-management -- /bin/sh
```

### Service Issues
```bash
# Check service configuration
kubectl describe service <service-name> -n patient-management

# Check endpoints
kubectl get endpoints <service-name> -n patient-management

# Test internal DNS resolution
kubectl run debug-pod --image=curlimages/curl -i --tty --rm -n patient-management -- nslookup api-gateway.patient-management.svc.cluster.local
```

### Network Issues
```bash
# Check kube-proxy status
kubectl get pods -n kube-system | grep kube-proxy

# Check NodePort binding
ss -tlnp | grep <nodeport>
netstat -tlnp | grep <nodeport>

# Test pod-to-pod connectivity
kubectl run debug-pod --image=curlimages/curl -i --tty --rm -n patient-management -- curl http://api-gateway.patient-management.svc.cluster.local:4004/actuator/health
```

### Pipeline Deployment Failures

**Check Jenkins pipeline logs for deployment stage failures:**
```bash
# If pipeline fails at dependency deployment, check specific components:

# Database deployment failure
kubectl get events -n patient-management | grep -E "(patient-service-db|auth-service-db)"
kubectl describe deployment patient-service-db -n patient-management

# Kafka deployment failure  
kubectl get events -n patient-management | grep kafka
kubectl describe deployment kafka -n patient-management

# Service deployment failure
kubectl get events -n patient-management | grep -E "(patient-service|auth-service|billing-service)"
kubectl rollout status deployment/<failed-service> -n patient-management
```

**Manual rollback if pipeline rollback fails:**
```bash
# Rollback specific deployment
kubectl rollout undo deployment/<service-name> -n patient-management

# Check rollback status
kubectl rollout status deployment/<service-name> -n patient-management

# Force restart if needed
kubectl rollout restart deployment/<service-name> -n patient-management
```

### Resource Issues
```bash
# Check resource usage
kubectl top nodes
kubectl top pods -n patient-management

# Check events
kubectl get events -n patient-management --sort-by=.metadata.creationTimestamp

# Check persistent volumes
kubectl get pv,pvc -n patient-management
```

## 10. Scaling and Updates

### Scale Deployments
```bash
# Scale up/down deployments
kubectl scale deployment patient-service --replicas=3 -n patient-management
kubectl scale deployment api-gateway --replicas=2 -n patient-management

# Scale down for maintenance
kubectl scale deployment --all --replicas=0 -n patient-management
```

### Rolling Updates
```bash
# Update image version
kubectl set image deployment/patient-service patient-service=ghergutmadalin/patient-managment:patient-service-v2 -n patient-management

# Check rollout status
kubectl rollout status deployment/patient-service -n patient-management

# Rollback if needed
kubectl rollout undo deployment/patient-service -n patient-management
```

### Configuration Updates
```bash
# Update ConfigMap
kubectl edit configmap postgres-config -n patient-management

# Restart deployments to pick up new config
kubectl rollout restart deployment/patient-service -n patient-management
```

## 11. Monitoring and Logs

### Continuous Monitoring
```bash
# Watch pod status
kubectl get pods -n patient-management -w

# Follow logs
kubectl logs -f deployment/api-gateway -n patient-management

# Monitor multiple services
kubectl logs -f -l app=patient-service -n patient-management
```

### Log Collection
```bash
# Collect logs from all pods
mkdir logs
kubectl logs deployment/api-gateway -n patient-management > logs/api-gateway.log
kubectl logs deployment/patient-service -n patient-management > logs/patient-service.log
kubectl logs deployment/auth-service -n patient-management > logs/auth-service.log
```

## 12. Cleanup Commands

### Partial Cleanup
```bash
# Delete only application deployments (keep infrastructure)
kubectl delete deployment api-gateway patient-service auth-service analytics-service billing-service -n patient-management

# Scale down all deployments
kubectl scale deployment --all --replicas=0 -n patient-management
```

### Complete Cleanup
```bash
# Delete entire namespace (removes everything)
kubectl delete namespace patient-management

# Verify cleanup
kubectl get all -n patient-management
```

### Jenkins Cleanup
```bash
# Remove Jenkins credentials
# Go to Jenkins → Manage Jenkins → Manage Credentials → Delete kubeconfig-credentials

# Remove service account and RBAC
kubectl delete clusterrolebinding jenkins-deployer-admin
kubectl delete serviceaccount jenkins-deployer -n patient-management
```

## 13. Quick Start Summary

For a quick deployment from scratch:

```bash
# 1. Setup RBAC
kubectl create namespace patient-management
kubectl create serviceaccount jenkins-deployer -n patient-management
kubectl create clusterrolebinding jenkins-deployer-admin --clusterrole=cluster-admin --serviceaccount=patient-management:jenkins-deployer

# 2. Configure Jenkins
kubectl config view --raw > /tmp/jenkins-kubeconfig
# Add to Jenkins credentials as 'kubeconfig-credentials'

# 3. Deploy via Jenkins pipeline
# Set parameters: DOCKER_TAG=latest, DEPLOY_TO_K8S=true, BUILD_DOCKER_IMAGES=true

# 4. Verify and access
kubectl get all -n patient-management
kubectl port-forward service/api-gateway 9090:4004 -n patient-management --address 0.0.0.0
curl http://localhost:9090/actuator/health
```

## 14. Important Notes

- **Security**: The jenkins-deployer service account has cluster-admin privileges for simplicity. In production, use more restrictive permissions.
- **Persistence**: Database data is stored in Kubernetes volumes. Ensure proper backup strategies.
- **Resource Limits**: Consider adding resource requests and limits to pod specifications for production use.
- **Networking**: NodePort services expose applications on all nodes. Consider using LoadBalancer or Ingress for production.
- **Monitoring**: Grafana dashboard provides application monitoring. Configure alerts for production environments.

## 15. Next Steps

1. **Security Hardening**: Implement proper RBAC, network policies, and pod security policies
2. **Backup Strategy**: Set up automated database backups
3. **Monitoring**: Configure alerts and dashboards
4. **CI/CD Enhancement**: Add automated testing stages to the pipeline
5. **Production Readiness**: Add health checks, resource limits, and proper secrets management

## Part 6: Domain Integration with krunky.xyz

### Prerequisites
- GoDaddy domain: krunky.xyz
- AWS certificate (optional, can use Let's Encrypt)
- Kubernetes cluster with LoadBalancer support

### Step 1: Deploy with Domain Integration
```bash
# Make the deployment script executable
chmod +x deploy-with-domain.sh

# Update the email in the script before running
sed -i 's/your-email@example.com/youractual@email.com/g' deploy-with-domain.sh

# Run the complete deployment
./deploy-with-domain.sh
```

### Step 2: Configure DNS Records
After deployment, note the External IP and configure these DNS records in GoDaddy:

| Type | Name | Value | TTL |
|------|------|-------|-----|
| A | @ | YOUR_K8S_EXTERNAL_IP | 1 Hour |
| A | api | YOUR_K8S_EXTERNAL_IP | 1 Hour |
| A | grafana | YOUR_K8S_EXTERNAL_IP | 1 Hour |

### Step 3: Verify Domain Setup
```bash
# Check DNS propagation
nslookup api.krunky.xyz
nslookup grafana.krunky.xyz

# Test HTTPS endpoints
curl -I https://api.krunky.xyz/actuator/health
curl -I https://grafana.krunky.xyz

# Monitor certificate generation
kubectl get certificate -n patient-management
kubectl describe certificate krunky-xyz-tls -n patient-management
```

### Step 4: Access Your Application
- **API Gateway**: https://api.krunky.xyz
- **Grafana Dashboard**: https://grafana.krunky.xyz
- **Health Check**: https://api.krunky.xyz/actuator/health

---

## Quick Ingress Setup Guide

### Option 1: Run Complete Deployment Script
```bash
# Run on Kubernetes master node
./deploy-with-domain.sh

# Script will output External IP - use this for GoDaddy DNS
```

### Option 2: Manual Step-by-Step Setup
```bash
# 1. Install NGINX Ingress Controller
chmod +x k8s/install-ingress.sh
./k8s/install-ingress.sh

# 2. Install cert-manager for SSL
chmod +x k8s/install-cert-manager.sh
./k8s/install-cert-manager.sh

# 3. Get External IP (wait 2-5 minutes for LoadBalancer)
kubectl get service ingress-nginx-controller --namespace=ingress-nginx -w

# 4. Deploy application
kubectl apply -f k8s/patient-management-namespace.yaml
kubectl apply -f k8s/postgres-configmap.yaml
kubectl apply -f k8s/infrastructure.yaml
kubectl apply -f k8s/microservices.yaml

# 5. Apply ingress configuration
kubectl apply -f k8s/ingress.yaml
```

### GoDaddy DNS Configuration

1. **Login to GoDaddy:** https://dcc.godaddy.com/
2. **Navigate to:** My Products → DNS → krunky.xyz → Manage DNS
3. **Add these A records:**

| Type | Name | Value | TTL |
|------|------|-------|-----|
| A | @ | `YOUR_EXTERNAL_IP` | 1 Hour |
| A | api | `YOUR_EXTERNAL_IP` | 1 Hour |
| A | grafana | `YOUR_EXTERNAL_IP` | 1 Hour |

**Example:**
```
A    @        34.123.45.67    1 Hour
A    api      34.123.45.67    1 Hour  
| A    grafana  34.123.45.67    1 Hour
```

4. **Wait 5-60 minutes for DNS propagation**
5. **Test:** `curl -I https://api.krunky.xyz/actuator/health`

---

### Troubleshooting Domain Issues

1. **DNS not resolving**:
   ```bash
   # Check DNS propagation
   dig api.krunky.xyz
   nslookup api.krunky.xyz 8.8.8.8
   ```

2. **Certificate not working**:
   ```bash
   # Check cert-manager logs
   kubectl logs -n cert-manager deployment/cert-manager
   
   # Check certificate status
   kubectl get certificaterequest -n patient-management
   ```

3. **502 Bad Gateway**:
   ```bash
   # Check if backend services are running
   kubectl get pods -n patient-management
   
   # Check ingress logs
   kubectl logs -n ingress-nginx deployment/ingress-nginx-controller
   ```

---

## Part 7: Deployment Process with Dependencies

The Jenkins pipeline now follows proper dependency order similar to Docker Compose:

### Deployment Sequence
1. **🏗️ Infrastructure Setup** - Namespace & ConfigMaps
2. **🗄️ Database Layer** - PostgreSQL databases with health checks
3. **📨 Messaging Layer** - Kafka with proper startup time
4. **🔐 Authentication Layer** - auth-service (first microservice)
5. **🏥 Core Business Services** - patient-service, billing-service, analytics-service
6. **🚪 Gateway Layer** - api-gateway (depends on all services)
7. **📊 Monitoring Layer** - Grafana monitoring

### Pipeline Features
- **Sequential deployment** with proper wait conditions
- **Health checks** for each layer before proceeding
- **Buffer periods** between service starts
- **Automatic rollback** on deployment failure
- **Service verification** and access URL reporting

---

## Part 8: Cleanup and Maintenance

### Quick Cleanup (Preserves Service Account)

To stop all deployments while keeping Jenkins access:

```bash
# Remove application deployments
kubectl delete deployments --all -n patient-management

# Remove services
kubectl delete services --all -n patient-management

# Remove ingress configurations
kubectl delete ingress --all -n patient-management

# Remove configmaps
kubectl delete configmap postgres-config -n patient-management

# Remove persistent volume claims (this will delete data!)
kubectl delete pvc --all -n patient-management

# Remove SSL certificates
kubectl delete secret krunky-xyz-tls -n patient-management --ignore-not-found
kubectl delete secret letsencrypt-prod -n patient-management --ignore-not-found
```

### Complete Cleanup (Everything)

To completely remove everything including Jenkins access:

```bash
# Remove the entire namespace (WARNING: This removes everything)
kubectl delete namespace patient-management

# Remove cluster-wide resources
kubectl delete clusterrolebinding jenkins-deployer-admin
kubectl delete clusterrole jenkins-deployer --ignore-not-found

# Remove ingress controller (optional)
kubectl delete namespace ingress-nginx

# Remove cert-manager (optional)
kubectl delete -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.2/cert-manager.yaml

# Remove ClusterIssuer
kubectl delete clusterissuer letsencrypt-prod --ignore-not-found
```

### Partial Cleanup Options

**Remove only application services (keep infrastructure):**
```bash
kubectl delete deployment patient-service billing-service auth-service analytics-service api-gateway -n patient-management
```

**Remove only infrastructure (keep applications):**
```bash
kubectl delete deployment patient-service-db auth-service-db kafka grafana -n patient-management
```

**Scale down everything (preserve configuration):**
```bash
kubectl scale deployment --all --replicas=0 -n patient-management
```

**Scale back up:**
```bash
kubectl scale deployment --all --replicas=1 -n patient-management
```

### Verification Commands

**Check what's running:**
```bash
# Overview of all resources
kubectl get all -n patient-management

# Detailed pod status
kubectl get pods -n patient-management -o wide

# Check persistent volumes
kubectl get pv,pvc -n patient-management

# Check service account (should remain after quick cleanup)
kubectl get serviceaccount jenkins-deployer -n patient-management
```

**Check logs for troubleshooting:**
```bash
# Application logs
kubectl logs -f deployment/api-gateway -n patient-management
kubectl logs -f deployment/patient-service -n patient-management

# Infrastructure logs
kubectl logs -f deployment/kafka -n patient-management
kubectl logs -f deployment/patient-service-db -n patient-management
```

### Maintenance Tasks

**Update application images:**
```bash
# Update specific service
kubectl set image deployment/patient-service patient-service=ghergutmadalin/patient-managment:patient-service-v2.0 -n patient-management

# Restart deployment
kubectl rollout restart deployment/patient-service -n patient-management

# Check rollout status
kubectl rollout status deployment/patient-service -n patient-management
```

**Database maintenance:**
```bash
# Backup database (example for patient-service-db)
kubectl exec -it deployment/patient-service-db -n patient-management -- pg_dump -U myuser mydatabase > backup.sql

# Access database shell
kubectl exec -it deployment/patient-service-db -n patient-management -- psql -U myuser -d mydatabase
```

**Resource monitoring:**
```bash
# Check resource usage
kubectl top pods -n patient-management
kubectl top nodes

# Check events
kubectl get events -n patient-management --sort-by='.lastTimestamp'
```

---
