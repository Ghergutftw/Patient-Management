def updateImageInManifest(serviceName, imageName, imageTag) {
    sh """
        sed 's|image: ${serviceName}:.*|image: ${imageName}:${serviceName}-${imageTag}|g' k8s/microservices.yaml > k8s/microservices-updated.yaml
    """
}

pipeline {
    agent any

    parameters {
        string(name: 'NEXUS_IP', defaultValue: '35.174.104.227', description: 'Nexus server IP address')
        string(name: 'SONAR_IP', defaultValue: '52.91.189.5', description: 'SonarQube server IP address')
        string(name: 'DOCKER_TAG', defaultValue: 'latest', description: 'Docker image tag to deploy')
        string(name: 'GIT_BRANCH', defaultValue: 'k8s', description: 'Git branch to checkout')

        booleanParam(name: 'ENABLE_TESTS', defaultValue: false, description: 'Enable running tests')
        booleanParam(name: 'ENABLE_SONAR', defaultValue: false, description: 'Enable SonarQube analysis')
        booleanParam(name: 'ABORT_ON_QUALITY_GATE_FAILURE', defaultValue: false, description: 'Abort pipeline if Quality Gate fails')
        booleanParam(name: 'DEPLOY_TO_NEXUS', defaultValue: false, description: 'Deploy artifacts to Nexus')
        booleanParam(name: 'BUILD_DOCKER_IMAGES', defaultValue: true, description: 'Build and tag Docker images')
        booleanParam(name: 'PUSH_TO_DOCKER_REGISTRY', defaultValue: true, description: 'Push Docker images to registry')
        booleanParam(name: 'ENABLE_TRIVY_FS_SCAN', defaultValue: true, description: 'Enable file system security scan')
        booleanParam(name: 'ENABLE_TRIVY_IMAGE_SCAN', defaultValue: true, description: 'Enable image security scan')
        booleanParam(name: 'CLEANUP_IMAGES', defaultValue: true, description: 'Cleanup local images after push')
        booleanParam(name: 'DEPLOY_TO_K8S', defaultValue: true, description: 'Deploy to Kubernetes cluster')
    }

    tools {
        maven 'MAVEN_3_9'
        jdk 'JDK21'
    }

    environment {
        DOCKER_IMAGE_NAME = "ghergutmadalin/patient-managment"
        NEXUS_CREDENTIALS = 'nexus-credentials'
        DOCKER_HUB_CREDENTIALS = 'docker-hub-credentials'
        SONAR_SERVER = 'SONAR_QUBE_SERVER'
        KUBECONFIG = credentials('kubeconfig-credentials')

        NEXUS_URL = "http://${params.NEXUS_IP}:8081"
        SONAR_URL = "http://${params.SONAR_IP}:9000"
        K8S_DEPLOYMENT_TIMEOUT = '300s'
    }

    stages {
        stage('Git Checkout') {
            steps {
                git branch: "${params.GIT_BRANCH}", url: 'https://github.com/Ghergutftw/Patient-Management.git'
            }
        }

        stage('Load Services') {
            steps {
                script {
                    def parentPom = readMavenPom file: 'pom.xml'
                    env.SERVICES = parentPom.modules.join(',')
                    echo "Services detected: ${parentPom.modules}"
                }
            }
        }

        stage('File System Check with trivy') {
            when { expression { params.ENABLE_TRIVY_FS_SCAN } }
            steps {
                sh 'trivy fs --format table -o trivy-fs-report.html .'
            }
        }

        stage('SonarQube Analysis') {
            when { expression { params.ENABLE_SONAR } }
            steps {
                script {
                    def parentPom = readMavenPom file: 'pom.xml'
                    def services = parentPom.modules
                    
                    // Option 1: Run SonarQube for each service individually
                    services.each { service ->
                        dir(service) {
                            withSonarQubeEnv("${env.SONAR_SERVER}") {
                                sh """mvn sonar:sonar \\
                                    -Dsonar.projectKey=PatientManagement-${service} \\
                                    -Dsonar.host.url=${env.SONAR_URL} \\
                                    -Dsonar.sources=src/main/java \\
                                    -Dsonar.tests=src/test/java \\
                                    -Dsonar.java.binaries=target/classes"""
                            }
                        }
                    }
                    
                    // Option 2: Run aggregate analysis from root (current approach)
                    // Uncomment below and comment above if you prefer single project analysis
                    /*
                    def sourcePaths = services.collect { "${it}/src/main/java" }.join(',')
                    def testPaths = services.collect { "${it}/src/test/java" }.join(',')
                    def binaryPaths = services.collect { "${it}/target/classes" }.join(',')

                    withSonarQubeEnv("${env.SONAR_SERVER}") {
                        sh """mvn sonar:sonar \\
                            -Dsonar.projectKey=PatientManagement \\
                            -Dsonar.host.url=${env.SONAR_URL} \\
                            -Dsonar.sources=${sourcePaths} \\
                            -Dsonar.tests=${testPaths} \\
                            -Dsonar.java.binaries=${binaryPaths}"""
                    }
                    */
                }
            }
        }

        stage('Quality Gate') {
            when { expression { params.ENABLE_SONAR } }
            steps {
                script {
                    try {
                        timeout(time: 10, unit: 'MINUTES') {
                            // Use the same SonarQube server URL as configured in the analysis
                            def qg = waitForQualityGate(abortPipeline: false)
                            if (qg.status != 'OK') {
                                echo "Quality Gate failed: ${qg.status}"
                                if (params.ABORT_ON_QUALITY_GATE_FAILURE) {
                                    error("Quality Gate failed")
                                } else {
                                    echo "Continuing pipeline despite Quality Gate failure (as per configuration)"
                                }
                            } else {
                                echo "Quality Gate passed successfully"
                            }
                        }
                    } catch (Exception e) {
                        echo "❌ Quality Gate check failed with error: ${e.getMessage()}"
                        echo "🔍 This might be due to SonarQube server connectivity issues"
                        echo "🔗 SonarQube Server URL: ${env.SONAR_URL}"
                        
                        if (params.ABORT_ON_QUALITY_GATE_FAILURE) {
                            error("Quality Gate check failed: ${e.getMessage()}")
                        } else {
                            echo "Continuing pipeline despite Quality Gate error (as per configuration)"
                        }
                    }
                }
            }
        }

        stage('Deploy to Nexus') {
            when { expression { params.DEPLOY_TO_NEXUS } }
            steps {
                script {
                    def parentPom = readMavenPom file: 'pom.xml'
                    def services = parentPom.modules

                    services.each { service ->
                        def pom = readMavenPom file: "${service}/pom.xml"
                        def dynamicVersion = "${pom.version}-${env.BUILD_ID}-${env.BUILD_TIMESTAMP}"
                        def artifactFile = "${service}/target/${pom.artifactId}-${pom.version}.jar"

                        nexusArtifactUploader(
                            nexusVersion: 'nexus3',
                            protocol: 'http',
                            nexusUrl: "${params.NEXUS_IP}:8081",
                            groupId: pom.groupId,
                            version: dynamicVersion,
                            repository: 'maven-releases',
                            credentialsId: "${env.NEXUS_CREDENTIALS}",
                            artifacts: [
                                [
                                    artifactId: pom.artifactId,
                                    classifier: '',
                                    file: artifactFile,
                                    type: 'jar'
                                ]
                            ]
                        )
                    }
                }
            }
        }

        stage('Build and Tag Docker Images') {
            when { expression { params.BUILD_DOCKER_IMAGES } }
            steps {
                script {
                    def parentPom = readMavenPom file: 'pom.xml'
                    def services = parentPom.modules

                    services.each { service ->
                        dir(service) {
                            echo "Building Docker image for ${service}..."

                            // Build Docker image using Spring Boot build-image
                            sh "mvn spring-boot:build-image -Dspring-boot.build-image.imageName=${service}:${params.DOCKER_TAG} -DskipTests"

                            // Tag the image with registry name
                            sh "docker tag ${service}:${params.DOCKER_TAG} ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}"

                            // Verify the image was tagged correctly
                            sh "docker images | grep ${env.DOCKER_IMAGE_NAME}"

                            echo "Successfully built and tagged ${service}"
                        }
                    }
                }
            }
        }

        stage('Docker Image Security Scan with Trivy') {
            when { 
                allOf {
                    expression { params.ENABLE_TRIVY_IMAGE_SCAN }
                    expression { params.BUILD_DOCKER_IMAGES }
                }
            }
            steps {
                script {
                    def parentPom = readMavenPom file: 'pom.xml'
                    def services = parentPom.modules

                    services.each { service ->
                        echo "Scanning image: ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}"

                        // Verify image exists before scanning
                        sh """
                            if docker inspect ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG} > /dev/null 2>&1; then
                                echo "Image found, starting Trivy scan..."
                                trivy image --format table -o trivy-${service}-image-report.html ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}
                                echo "Trivy scan completed for ${service}"
                            else
                                echo "Image ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG} not found"
                                echo "Available images:"
                                docker images | grep patient-managment || echo "No patient-managment images found"
                                exit 1
                            fi
                        """
                    }
                }
            }
        }

        stage('Push to Docker Registry') {
            when { expression { params.PUSH_TO_DOCKER_REGISTRY } }
            steps {
                script {
                    def parentPom = readMavenPom file: 'pom.xml'
                    def services = parentPom.modules

                    withDockerRegistry([credentialsId: "${env.DOCKER_HUB_CREDENTIALS}", url: 'https://index.docker.io/v1/']) {
                        services.each { service ->
                            echo "Pushing image: ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}"

                            def image = docker.image("${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}")
                            image.push()

                            // Also push with latest tag
                            image.push("${service}-latest")

                            echo "Successfully pushed ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}"
                        }
                    }
                }
            }
        }

        stage('Cleanup Local Images') {
            when { 
                allOf {
                    expression { params.CLEANUP_IMAGES }
                    expression { params.BUILD_DOCKER_IMAGES }
                }
            }
            steps {
                script {
                    def parentPom = readMavenPom file: 'pom.xml'
                    def services = parentPom.modules

                    echo "Cleaning up local images to save space..."
                    services.each { service ->
                        // Remove the local build images to save space
                        sh "docker rmi ${service}:${params.DOCKER_TAG} || true"
                        sh "docker rmi ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG} || true"
                    }

                    // Clean up dangling images
                    sh "docker system prune -f || true"
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when { expression { params.DEPLOY_TO_K8S } }
            steps {
                script {
                    echo "Deploying to Kubernetes cluster"
                    
                    // Update image tags in microservices manifest
                    def services = ['patient-service', 'billing-service', 'auth-service', 'analytics-service', 'api-gateway']
                    
                    sh "cp k8s/microservices.yaml k8s/microservices-updated.yaml"
                    
                    services.each { service ->
                        sh """
                            sed -i 's|image: ${service}:.*|image: ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}|g' k8s/microservices-updated.yaml
                        """
                        echo "Updated ${service} image to ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}"
                    }
                    
                    // Deploy infrastructure
                    echo "Deploying infrastructure components..."
                    sh "kubectl apply -f k8s/patient-management-namespace.yaml"
                    sh "kubectl apply -f k8s/postgres-configmap.yaml"
                    sh "kubectl apply -f k8s/infrastructure.yaml"
                    
                    // Wait for infrastructure
                    echo "Waiting for infrastructure to be ready..."
                    sh """
                        kubectl wait --for=condition=available --timeout=300s deployment/patient-service-db -n patient-management || echo "Warning: patient-service-db timeout"
                        kubectl wait --for=condition=available --timeout=300s deployment/auth-service-db -n patient-management || echo "Warning: auth-service-db timeout"
                        kubectl wait --for=condition=available --timeout=300s deployment/kafka -n patient-management || echo "Warning: kafka timeout"
                        kubectl wait --for=condition=available --timeout=300s deployment/grafana -n patient-management || echo "Warning: grafana timeout"
                    """
                    
                    // Deploy microservices
                    echo "Deploying microservices..."
                    sh "kubectl apply -f k8s/microservices-updated.yaml"
                    
                    // Wait for microservices
                    services.each { service ->
                        echo "Waiting for ${service} to be ready..."
                        sh """
                            kubectl wait --for=condition=available --timeout=300s deployment/${service} -n patient-management || echo "Warning: ${service} timeout"
                            kubectl rollout status deployment/${service} -n patient-management --timeout=60s || echo "Warning: ${service} rollout timeout"
                        """
                    }
                    
                    echo "Deployment completed successfully"
                }
            }
            post {
                failure {
                    script {
                        echo "Deployment failed, attempting rollback..."
                        def services = ['patient-service', 'billing-service', 'auth-service', 'analytics-service', 'api-gateway']
                        services.each { service ->
                            sh "kubectl rollout undo deployment/${service} -n patient-management || echo 'Cannot rollback ${service}'"
                        }
                        // Also rollback infrastructure if needed
                        sh "kubectl rollout undo deployment/grafana -n patient-management || echo 'Cannot rollback grafana'"
                    }
                }
            }
        }

        stage('Verify Kubernetes Deployment') {
            when { expression { params.DEPLOY_TO_K8S } }
            steps {
                script {
                    echo "Verifying Kubernetes deployment..."
                    
                    sh "kubectl get pods -n patient-management -o wide"
                    sh "kubectl get services -n patient-management"
                    sh "kubectl get deployments -n patient-management"
                    
                    // Check API Gateway access
                    echo "Checking API Gateway accessibility..."
                    sh """
                        GATEWAY_IP=\$(kubectl get service api-gateway -n patient-management -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
                        if [ -n "\$GATEWAY_IP" ] && [ "\$GATEWAY_IP" != "null" ]; then
                            echo "API Gateway LoadBalancer IP: http://\$GATEWAY_IP:4004"
                        else
                            NODE_PORT=\$(kubectl get service api-gateway -n patient-management -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || echo "")
                            if [ -n "\$NODE_PORT" ] && [ "\$NODE_PORT" != "null" ]; then
                                echo "API Gateway NodePort: \$NODE_PORT (use any node IP)"
                            else
                                echo "Use port-forward to access API Gateway:"
                                echo "kubectl port-forward service/api-gateway 4004:4004 -n patient-management"
                            fi
                        fi
                    """
                    
                    // Check Grafana access
                    echo "Checking Grafana accessibility..."
                    sh """
                        GRAFANA_IP=\$(kubectl get service grafana -n patient-management -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
                        if [ -n "\$GRAFANA_IP" ] && [ "\$GRAFANA_IP" != "null" ]; then
                            echo "=============================================="
                            echo "GRAFANA DASHBOARD ACCESS:"
                            echo "URL: http://\$GRAFANA_IP:3000"
                            echo "Username: admin"
                            echo "Password: admin"
                            echo "=============================================="
                        else
                            GRAFANA_NODE_PORT=\$(kubectl get service grafana -n patient-management -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || echo "")
                            if [ -n "\$GRAFANA_NODE_PORT" ] && [ "\$GRAFANA_NODE_PORT" != "null" ]; then
                                echo "=============================================="
                                echo "GRAFANA DASHBOARD ACCESS:"
                                echo "URL: http://<any-node-ip>:\$GRAFANA_NODE_PORT"
                                echo "Username: admin"
                                echo "Password: admin"
                                echo "=============================================="
                            else
                                echo "=============================================="
                                echo "GRAFANA DASHBOARD ACCESS:"
                                echo "Use port-forward: kubectl port-forward service/grafana 3000:3000 -n patient-management"
                                echo "Then access: http://localhost:3000"
                                echo "Username: admin"
                                echo "Password: admin"
                                echo "=============================================="
                            fi
                        fi
                    """
                    
                    // Display summary of access URLs
                    echo "=============================================="
                    echo "DEPLOYMENT SUMMARY"
                    echo "=============================================="
                    sh """
                        echo "Namespace: patient-management"
                        echo "Docker Images Tag: ${params.DOCKER_TAG}"
                        echo ""
                        echo "API GATEWAY:"
                        GATEWAY_IP=\$(kubectl get service api-gateway -n patient-management -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
                        if [ -n "\$GATEWAY_IP" ] && [ "\$GATEWAY_IP" != "null" ]; then
                            echo "  URL: http://\$GATEWAY_IP:4004"
                        else
                            NODE_PORT=\$(kubectl get service api-gateway -n patient-management -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || echo "")
                            if [ -n "\$NODE_PORT" ] && [ "\$NODE_PORT" != "null" ]; then
                                echo "  URL: http://<node-ip>:\$NODE_PORT"
                            else
                                echo "  URL: Use port-forward (see above)"
                            fi
                        fi
                        
                        echo ""
                        echo "GRAFANA DASHBOARD:"
                        GRAFANA_IP=\$(kubectl get service grafana -n patient-management -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
                        if [ -n "\$GRAFANA_IP" ] && [ "\$GRAFANA_IP" != "null" ]; then
                            echo "  URL: http://\$GRAFANA_IP:3000"
                        else
                            GRAFANA_NODE_PORT=\$(kubectl get service grafana -n patient-management -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || echo "")
                            if [ -n "\$GRAFANA_NODE_PORT" ] && [ "\$GRAFANA_NODE_PORT" != "null" ]; then
                                echo "  URL: http://<node-ip>:\$GRAFANA_NODE_PORT"
                            else
                                echo "  URL: Use port-forward (see above)"
                            fi
                        fi
                        echo "  Username: admin"
                        echo "  Password: admin"
                    """
                    echo "=============================================="
                    
                    echo "Deployment verification completed"
                }
            }
        }


    }
    post {
        always {
            script {
                // Only run junit if test results exist
                if (fileExists('**/target/surefire-reports/*.xml')) {
                    junit '**/target/surefire-reports/*.xml'
                }

                // Archive artifacts if they exist
                if (fileExists('**/target/*.jar')) {
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                }

                // Publish Trivy reports if they exist // needs HTML Publisher
                if (fileExists('trivy-fs-report.html')) {
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: '.',
                        reportFiles: 'trivy-fs-report.html',
                        reportName: 'Trivy File System Report'
                    ])
                }

                // Publish individual Docker image reports
                def parentPom = readMavenPom file: 'pom.xml'
                def services = parentPom.modules
                services.each { service ->
                    def reportFile = "trivy-${service}-image-report.html"
                    if (fileExists(reportFile)) {
                        publishHTML([
                            allowMissing: false,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: '.',
                            reportFiles: reportFile,
                            reportName: "Trivy ${service.capitalize()} Image Report"
                        ])
                    }
                }
            }
        }

        failure {
            script {
                echo "Pipeline failed. Debugging information:"
                sh '''
                    echo "=== All Docker Images ==="
                    docker images

                    echo "=== Patient Management Images ==="
                    docker images | grep patient-managment || echo "No patient-managment images found"

                    echo "=== Docker System Info ==="
                    docker system df || true

                    echo "=== Available Disk Space ==="
                    df -h || true
                '''
                
                if (params.DEPLOY_TO_K8S) {
                    sh """
                        echo "=== Kubernetes Debug Info ==="
                        kubectl get all -n patient-management || true
                        kubectl describe pods -n patient-management || true
                    """
                }
            }
        }

        success {
            script {
                echo "Pipeline completed successfully!"
                echo "Check the Trivy reports for security scan results"
                echo "Images pushed to: https://hub.docker.com/repository/docker/ghergutmadalin/patient-managment"
                
                if (params.DEPLOY_TO_K8S) {
                    echo "Application deployed to Kubernetes namespace: patient-management"
                    echo "Access your application through the LoadBalancer services"
                }
            }
        }
    }

}