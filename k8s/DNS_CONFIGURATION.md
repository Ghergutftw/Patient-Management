# DNS Configuration Guide for krunky.xyz Domain

## Step 1: Get Your Kubernetes Cluster External IP

After installing the NGINX Ingress Controller, get the LoadBalancer external IP:

```bash
kubectl get service ingress-nginx-controller --namespace=ingress-nginx
```

Look for the `EXTERNAL-IP` column. This is the IP address you'll point your domain to.

## Step 2: Configure DNS Records in GoDaddy

Log into your GoDaddy account and navigate to DNS Management for `krunky.xyz`.

### Required DNS Records:

1. **A Record for main domain:**
   - Type: A
   - Name: @
   - Value: `YOUR_KUBERNETES_EXTERNAL_IP`
   - TTL: 1 Hour

2. **A Record for API subdomain:**
   - Type: A
   - Name: api
   - Value: `YOUR_KUBERNETES_EXTERNAL_IP`
   - TTL: 1 Hour

3. **A Record for Grafana subdomain:**
   - Type: A
   - Name: grafana
   - Value: `YOUR_KUBERNETES_EXTERNAL_IP`
   - TTL: 1 Hour

4. **CNAME Record (alternative to A records for subdomains):**
   - Type: CNAME
   - Name: api
   - Value: krunky.xyz
   - TTL: 1 Hour

   - Type: CNAME
   - Name: grafana
   - Value: krunky.xyz
   - TTL: 1 Hour

## Step 3: AWS Certificate Configuration

If you're using AWS Load Balancer Controller instead of NGINX:

1. **Get your AWS Certificate ARN:**
   ```bash
   aws acm list-certificates --region us-east-1
   ```

2. **Update the ingress configuration:**
   Replace `YOUR_CERTIFICATE_ID` in the ALB ingress section with your actual certificate ARN.

## Step 4: Verification Commands

After DNS propagation (5-60 minutes), verify your setup:

```bash
# Check DNS resolution
nslookup api.krunky.xyz
nslookup grafana.krunky.xyz

# Test HTTPS access
curl -I https://api.krunky.xyz/actuator/health
curl -I https://grafana.krunky.xyz

# Check certificate status
kubectl get certificate -n patient-management
kubectl describe certificate krunky-xyz-tls -n patient-management
```

## Step 5: Common DNS Configurations

### GoDaddy DNS Manager Example:
```
Type    Name      Value                    TTL
A       @         YOUR_K8S_EXTERNAL_IP     1 Hour
A       api       YOUR_K8S_EXTERNAL_IP     1 Hour  
A       grafana   YOUR_K8S_EXTERNAL_IP     1 Hour
A       www       YOUR_K8S_EXTERNAL_IP     1 Hour
```

### Cloudflare DNS Example (if using Cloudflare):
```
Type    Name      Content                  Proxy Status
A       krunky.xyz    YOUR_K8S_EXTERNAL_IP     Orange Cloud (Proxied)
A       api           YOUR_K8S_EXTERNAL_IP     Orange Cloud (Proxied)
A       grafana       YOUR_K8S_EXTERNAL_IP     Orange Cloud (Proxied)
```

## Troubleshooting DNS Issues

1. **DNS not resolving:**
   ```bash
   # Check if DNS has propagated
   dig api.krunky.xyz
   dig grafana.krunky.xyz
   
   # Use online DNS checker tools
   # https://www.whatsmydns.net/
   ```

2. **Certificate not working:**
   ```bash
   # Check cert-manager logs
   kubectl logs -n cert-manager deployment/cert-manager
   
   # Check certificate request
   kubectl get certificaterequest -n patient-management
   kubectl describe certificaterequest -n patient-management
   ```

3. **Ingress not working:**
   ```bash
   # Check ingress status
   kubectl get ingress -n patient-management
   kubectl describe ingress patient-management-ingress -n patient-management
   
   # Check NGINX controller logs
   kubectl logs -n ingress-nginx deployment/ingress-nginx-controller
   ```

## Expected Final URLs

After successful configuration:
- **API Gateway:** https://api.krunky.xyz
- **Grafana Dashboard:** https://grafana.krunky.xyz  
- **Health Check:** https://api.krunky.xyz/actuator/health
- **API Documentation:** https://api.krunky.xyz/swagger-ui.html (if available)

## Security Notes

1. **HTTPS Only:** The ingress is configured to redirect HTTP to HTTPS automatically
2. **CORS:** Configured to allow requests from your domain
3. **Certificate Auto-Renewal:** cert-manager will automatically renew Let's Encrypt certificates
4. **Rate Limiting:** Consider adding rate limiting annotations for production
