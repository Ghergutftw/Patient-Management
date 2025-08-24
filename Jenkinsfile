def deployServiceFromManifest(serviceName, namespace) {
    sh """
        echo "📄 Looking for k8s manifests for ${serviceName}..."
        
        # Try different possible locations for k8s manifests
        if [ -f "k8s/${serviceName}.yaml" ]; then
            MANIFEST_FILE="k8s/${serviceName}.yaml"
        elif [ -f "k8s/${serviceName}-deployment.yaml" ]; then
            MANIFEST_FILE="k8s/${serviceName}-deployment.yaml"
        elif [ -f "k8s/deployments/${serviceName}.yaml" ]; then
            MANIFEST_FILE="k8s/deployments/${serviceName}.yaml"
        elif [ -f "k8s/services/${serviceName}.yaml" ]; then
            MANIFEST_FILE="k8s/services/${serviceName}.yaml"
        else
            echo "❌ No k8s manifest found for ${serviceName}"
            exit 1
        fi
        
        echo "📝 Using manifest: \$MANIFEST_FILE"
        
        # Update namespace in the manifest if needed
        sed "s/namespace: .*/namespace: ${namespace}/g" \$MANIFEST_FILE > ${serviceName}-k8s-updated.yaml
        
        # Update image tag if needed
        sed -i "s|image: .*${serviceName}.*|image: ${env.DOCKER_IMAGE_NAME}:${serviceName}-${params.DOCKER_TAG}|g" ${serviceName}-k8s-updated.yaml
        
        echo "📝 Applying k8s manifest for ${serviceName}..."
        kubectl apply -f ${serviceName}-k8s-updated.yaml
        
        echo "⏳ Waiting for ${serviceName} deployment to be ready..."
        kubectl rollout status deployment/${serviceName} -n ${namespace} --timeout=${env.K8S_DEPLOYMENT_TIMEOUT}
        
        echo "✅ ${serviceName} deployed successfully from manifest"
    """
}

pipeline {
    agent any

    parameters {
        string(name: 'NEXUS_IP', defaultValue: '35.174.104.227', description: 'Nexus server IP address')
        string(name: 'SONAR_IP', defaultValue: '52.91.189.5', description: 'SonarQube server IP address')
        string(name: 'DOCKER_TAG', defaultValue: 'latest', description: 'Docker image tag to use')
        string(name: 'GIT_BRANCH', defaultValue: 'jenkins', description: 'Git branch to checkout')
        booleanParam(name: 'SKIP_TESTS', defaultValue: true, description: 'Skip running tests')
        booleanParam(name: 'ENABLE_SONAR', defaultValue: false, description: 'Enable SonarQube analysis')
        booleanParam(name: 'ABORT_ON_QUALITY_GATE_FAILURE', defaultValue: false, description: 'Abort pipeline if Quality Gate fails')
        booleanParam(name: 'DEPLOY_TO_NEXUS', defaultValue: false, description: 'Deploy artifacts to Nexus')
        booleanParam(name: 'ENABLE_TRIVY_FS_SCAN', defaultValue: true, description: 'Enable file system security scan')
        booleanParam(name: 'ENABLE_TRIVY_IMAGE_SCAN', defaultValue: true, description: 'Enable image security scan')
        booleanParam(name: 'CLEANUP_IMAGES', defaultValue: true, description: 'Cleanup local images after push')

        booleanParam(name: 'DEPLOY_TO_K8S', defaultValue: false, description: 'Deploy to Kubernetes cluster')
        booleanParam(name: 'DEPLOY_MONITORING', defaultValue: true, description: 'Deploy monitoring stack (Prometheus, Grafana, Loki)')
       
        string(name: 'K8S_NAMESPACE', defaultValue: 'patient-management-test', description: 'Kubernetes namespace for deployment')
        string(name: 'K8S_CLUSTER_CONTEXT', defaultValue: 'your-cluster-context', description: 'Kubernetes cluster context')
        string(name: 'K8S_NODE_COUNT', defaultValue: '2', description: 'Expected number of nodes in cluster')
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

        NEXUS_URL = "http://${params.NEXUS_IP}:8081"
        SONAR_URL = "http://${params.SONAR_IP}:9000"

         KUBECONFIG = credentials('kubeconfig-credentials') // Add your kubeconfig credentials ID
        K8S_DEPLOYMENT_TIMEOUT = '300s'
    }

    stages {
        stage('Display Parameters') {
            steps {
                script {
                    echo "🔧 Pipeline Parameters:"
                    echo "Git Branch: ${params.GIT_BRANCH}"
                    echo "Nexus IP: ${params.NEXUS_IP}"
                    echo "Nexus Full URL: ${env.NEXUS_URL}"
                    echo "SonarQube IP: ${params.SONAR_IP}"
                    echo "SonarQube Full URL: ${env.SONAR_URL}"
                    echo "Docker Tag: ${params.DOCKER_TAG}"
                    echo "Skip Tests: ${params.SKIP_TESTS}"
                    echo "Enable SonarQube: ${params.ENABLE_SONAR}"
                    echo "Abort on Quality Gate Failure: ${params.ABORT_ON_QUALITY_GATE_FAILURE}"
                    echo "Deploy to Nexus: ${params.DEPLOY_TO_NEXUS}"
                    echo "Enable Trivy FS Scan: ${params.ENABLE_TRIVY_FS_SCAN}"
                    echo "Enable Trivy Image Scan: ${params.ENABLE_TRIVY_IMAGE_SCAN}"
                    echo "Cleanup Images: ${params.CLEANUP_IMAGES}"
                    echo "Docker Image Name: ${env.DOCKER_IMAGE_NAME}"
                }
            }
        }

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
                    echo "📦 Services detected from parent POM: ${env.SERVICES}"
                    echo "📋 Individual services: ${parentPom.modules}"
                }
            }
        }

        stage('Compile') {
            steps {
                sh "mvn compile -pl ${env.SERVICES}"
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
                                echo "⚠️ Quality Gate failed: ${qg.status}"
                                if (params.ABORT_ON_QUALITY_GATE_FAILURE) {
                                    error("Quality Gate failed")
                                } else {
                                    echo "🔄 Continuing pipeline despite Quality Gate failure (as per configuration)"
                                }
                            } else {
                                echo "✅ Quality Gate passed successfully"
                            }
                        }
                    } catch (Exception e) {
                        echo "❌ Quality Gate check failed with error: ${e.getMessage()}"
                        echo "🔍 This might be due to SonarQube server connectivity issues"
                        echo "🔗 SonarQube Server URL: ${env.SONAR_URL}"
                        
                        if (params.ABORT_ON_QUALITY_GATE_FAILURE) {
                            error("Quality Gate check failed: ${e.getMessage()}")
                        } else {
                            echo "🔄 Continuing pipeline despite Quality Gate error (as per configuration)"
                        }
                    }
                }
            }
        }

        stage('Package') {
            steps {
                sh "mvn package ${params.SKIP_TESTS ? '-DskipTests' : ''} -pl ${env.SERVICES}"
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
            steps {
                script {
                    def parentPom = readMavenPom file: 'pom.xml'
                    def services = parentPom.modules

                    services.each { service ->
                        dir(service) {
                            echo "🏗️ Building Docker image for ${service}..."

                            // Build Docker image using Spring Boot build-image with default name first
                            sh "mvn spring-boot:build-image -Dspring-boot.build-image.imageName=${service}:${params.DOCKER_TAG} -DskipTests"

                            // Tag the image with your desired name
                            sh "docker tag ${service}:${params.DOCKER_TAG} ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}"

                            // Verify the image was tagged correctly
                            sh "docker images | grep ${env.DOCKER_IMAGE_NAME}"

                            echo "✅ Successfully built and tagged ${service}"
                        }
                    }
                }
            }
        }

        stage('Docker Image Security Scan with Trivy') {
            when { expression { params.ENABLE_TRIVY_IMAGE_SCAN } }
            steps {
                script {
                    def parentPom = readMavenPom file: 'pom.xml'
                    def services = parentPom.modules

                    services.each { service ->
                        echo "🔍 Scanning image: ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}"

                        // Verify image exists before scanning
                        sh """
                            if docker inspect ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG} > /dev/null 2>&1; then
                                echo "✅ Image found, starting Trivy scan..."
                                trivy image --format table -o trivy-${service}-image-report.html ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}
                                echo "✅ Trivy scan completed for ${service}"
                            else
                                echo "❌ Image ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG} not found"
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
            steps {
                script {
                    def parentPom = readMavenPom file: 'pom.xml'
                    def services = parentPom.modules

                    withDockerRegistry([credentialsId: "${env.DOCKER_HUB_CREDENTIALS}", url: 'https://index.docker.io/v1/']) {
                        services.each { service ->
                            echo "🚀 Pushing image: ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}"

                            def image = docker.image("${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}")
                            image.push()

                            // Also push with just 'latest' tag for the main service image
                            image.push("${service}-latest")

                            echo "✅ Successfully pushed ${env.DOCKER_IMAGE_NAME}:${service}-${params.DOCKER_TAG}"
                        }
                    }
                }
            }
        }

        stage('Cleanup Local Images') {
            when { expression { params.CLEANUP_IMAGES } }
            steps {
                script {
                    def parentPom = readMavenPom file: 'pom.xml'
                    def services = parentPom.modules

                    echo "🧹 Cleaning up local images to save space..."
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
                    def parentPom = readMavenPom file: 'pom.xml'
                    def services = parentPom.modules
                    
                    echo "🚀 Starting Kubernetes deployment to namespace: ${params.K8S_NAMESPACE}"
                    
                    // Verify cluster connectivity and node count
                    sh """
                        echo "🔍 Verifying Kubernetes cluster connectivity..."
                        kubectl cluster-info
                        
                        echo "📊 Checking cluster nodes..."
                        ACTUAL_NODES=\$(kubectl get nodes --no-headers | wc -l)
                        echo "Expected nodes: ${params.K8S_NODE_COUNT}"
                        echo "Actual nodes: \$ACTUAL_NODES"
                        
                        if [ "\$ACTUAL_NODES" -lt "${params.K8S_NODE_COUNT}" ]; then
                            echo "⚠️ Warning: Expected ${params.K8S_NODE_COUNT} nodes but found \$ACTUAL_NODES"
                            echo "Continuing with deployment..."
                        fi
                        
                        kubectl get nodes -o wide
                    """
                    
                    // Create namespace if it doesn't exist
                    sh """
                        echo "🏗️ Creating namespace ${params.K8S_NAMESPACE} if it doesn't exist..."
                        kubectl create namespace ${params.K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        kubectl get namespace ${params.K8S_NAMESPACE}
                    """
                    
                    // Deploy monitoring stack first if enabled
                    if (params.DEPLOY_MONITORING) {
                        echo "📊 Deploying monitoring stack using repository files..."
                        deployMonitoringStackFromRepo(params.K8S_NAMESPACE)
                    }
                    
                    // Deploy each service to Kubernetes using k8s manifests if available
                    services.each { service ->
                        echo "🚀 Deploying ${service} to Kubernetes..."
                        
                        // Check if k8s manifests exist for this service
                        def k8sManifestExists = sh(
                            script: "test -f k8s/${service}.yaml || test -f k8s/${service}-deployment.yaml || test -f k8s/deployments/${service}.yaml",
                            returnStatus: true
                        ) == 0
                        
                        if (k8sManifestExists) {
                            echo "📄 Using existing k8s manifest for ${service}"
                            deployServiceFromManifest(service, params.K8S_NAMESPACE)
                        } else {
                            echo "🏗️ Generating k8s manifest for ${service}"
                            deployServiceDynamically(service, params.K8S_NAMESPACE, params.DOCKER_TAG)
                        }
                    }
                    
                    // Verify deployments are running on different nodes
                    sh """
                        echo "🔍 Verifying pod distribution across nodes..."
                        kubectl get pods -n ${params.K8S_NAMESPACE} -o wide
                        
                        echo "📊 Pod distribution summary:"
                        kubectl get pods -n ${params.K8S_NAMESPACE} -o jsonpath='{range .items[*]}{.spec.nodeName}{"\\n"}{end}' | sort | uniq -c
                        
                        echo "🎯 Deployment verification complete!"
                    """
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
                echo "🔍 Pipeline failed. Debugging information:"
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
            }
        }

        success {
            script {
                echo "🎉 Pipeline completed successfully!"
                echo "📊 Check the Trivy reports for security scan results"
                echo "🐳 Images pushed to: https://hub.docker.com/repository/docker/ghergutmadalin/patient-managment"
            }
        }
    }

}