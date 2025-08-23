pipeline { 
    agent any 
     
    tools { 
        maven 'MAVEN_3_9' 
        jdk 'JDK21' 
    } 
 
    stages { 
 
        stage('Git Checkout') { 
            steps { 
                git branch: 'jenkins', url: 'https://github.com/Ghergutftw/Patient-Management.git' 
            } 
        } 
        
        stage('Compile') { 
            steps { 
                sh 'mvn compile -pl patient-service,auth-service,billing-service,api-gateway,analytics-service'
            } 
        } 
 
        stage('File System Check with trivy') { 
            steps { 
                sh 'trivy fs --format table -o trivy-fs-report.html .' 
            } 
        } 
 
        // stage('SonarQube Analysis') { 
        //     steps { 
        //         withSonarQubeEnv('SONAR_QUBE_SERVER') { 
        //             sh '''mvn sonar:sonar \
        //                 -Dsonar.projectKey=PatientManagement \
        //                 -Dsonar.sources=patient-service/src/main/java,auth-service/src/main/java,billing-service/src/main/java,api-gateway/src/main/java,analytics-service/src/main/java \
        //                 -Dsonar.tests=patient-service/src/test/java,auth-service/src/test/java,billing-service/src/test/java,api-gateway/src/test/java,analytics-service/src/test/java \
        //                 -Dsonar.java.binaries=patient-service/target/classes,auth-service/target/classes,billing-service/target/classes,api-gateway/target/classes,analytics-service/target/classes \
        //                 -pl patient-service,auth-service,billing-service,api-gateway,analytics-service'''
        //         } 
        //     } 
        // }
        
        // stage('Quality Gate') { 
        //     steps { 
        //         timeout(time: 10, unit: 'MINUTES') { 
        //             waitForQualityGate abortPipeline: true 
        //         } 
        //     } 
        // }
        
        stage('Package') {
            steps {
                sh 'mvn package -DskipTests -pl patient-service,auth-service,billing-service,api-gateway,analytics-service'
            }
        }
        
        // stage('Deploy to Nexus') {
        //     steps {
        //         script {
        //             def services = ['patient-service', 'auth-service', 'billing-service', 'api-gateway', 'analytics-service']
                    
        //             services.each { service ->
        //                 def pom = readMavenPom file: "${service}/pom.xml"
        //                 def dynamicVersion = "${pom.version}-${env.BUILD_ID}-${env.BUILD_TIMESTAMP}"
        //                 def artifactFile = "${service}/target/${pom.artifactId}-${pom.version}.jar"

        //                 nexusArtifactUploader(
        //                     nexusVersion: 'nexus3',
        //                     protocol: 'http',
        //                     nexusUrl: '35.174.104.227:8081',
        //                     groupId: pom.groupId,
        //                     version: dynamicVersion,
        //                     repository: 'maven-releases',
        //                     credentialsId: 'nexus-credentials',
        //                     artifacts: [
        //                         [
        //                             artifactId: pom.artifactId,
        //                             classifier: '',
        //                             file: artifactFile,
        //                             type: 'jar'
        //                         ]
        //                     ]
        //                 )
        //             }
        //         }
        //     }
        // }
        
        stage('Build and Tag Docker Images') {
            steps {
                script {
                    def services = ['patient-service', 'auth-service', 'billing-service', 'api-gateway', 'analytics-service']
                    
                    services.each { service ->
                        dir(service) {
                            echo "🏗️ Building Docker image for ${service}..."
                            
                            // Build Docker image using Spring Boot build-image with default name first
                            sh "mvn spring-boot:build-image -Dspring-boot.build-image.imageName=${service}:latest -DskipTests"
                            
                            // Tag the image with your desired name
                            sh "docker tag ${service}:latest ghergutmadalin/patient-managment:${service}-latest"
                            
                            // Verify the image was tagged correctly
                            sh "docker images | grep ghergutmadalin/patient-managment"
                            
                            echo "✅ Successfully built and tagged ${service}"
                        }
                    }
                }
            }
        }
        

        stage('Docker Image Security Scan with Trivy') {
            steps {
                script {
                    def services = ['patient-service', 'auth-service', 'billing-service', 'api-gateway', 'analytics-service']
                    
                    services.each { service ->
                        echo "🔍 Scanning image: ghergutmadalin/patient-managment:${service}-latest"
                        
                        // Verify image exists before scanning
                        sh """
                            if docker inspect ghergutmadalin/patient-managment:${service}-latest > /dev/null 2>&1; then
                                echo "✅ Image found, starting Trivy scan..."
                                trivy image --format table -o trivy-${service}-image-report.html ghergutmadalin/patient-managment:${service}-latest
                                echo "✅ Trivy scan completed for ${service}"
                            else
                                echo "❌ Image ghergutmadalin/patient-managment:${service}-latest not found"
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
                    def services = ['patient-service', 'auth-service', 'billing-service', 'api-gateway', 'analytics-service']
                    
                    withDockerRegistry([credentialsId: 'docker-hub-credentials', url: 'https://index.docker.io/v1/']) {
                        services.each { service ->
                            echo "🚀 Pushing image: ghergutmadalin/patient-managment:${service}-latest"
                            
                            def image = docker.image("ghergutmadalin/patient-managment:${service}-latest")
                            image.push()
                            
                            // Also push with just 'latest' tag for the main service image
                            image.push("${service}-latest")
                            
                            echo "✅ Successfully pushed ghergutmadalin/patient-managment:${service}-latest"
                        }
                    }
                }
            }
        }
        
        stage('Cleanup Local Images') {
            steps {
                script {
                    def services = ['patient-service', 'auth-service', 'billing-service', 'api-gateway', 'analytics-service']
                    
                    echo "🧹 Cleaning up local images to save space..."
                    services.each { service ->
                        // Remove the local build images to save space
                        sh "docker rmi ${service}:latest || true"
                        sh "docker rmi ghergutmadalin/patient-managment:${service}-latest || true"
                    }
                    
                    // Clean up dangling images
                    sh "docker system prune -f || true"
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
                def services = ['patient-service', 'auth-service', 'billing-service', 'api-gateway', 'analytics-service']
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