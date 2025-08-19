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
        
        stage('Deploy to Nexus') {
            steps {
                script {
                    def services = ['patient-service', 'auth-service', 'billing-service', 'api-gateway', 'analytics-service']
                    
                    services.each { service ->
                        def pom = readMavenPom file: "${service}/pom.xml"
                        def dynamicVersion = "${pom.version}-${env.BUILD_ID}-${env.BUILD_TIMESTAMP}"
                        def artifactFile = "${service}/target/${pom.artifactId}-${pom.version}.jar"

                        nexusArtifactUploader(
                            nexusVersion: 'nexus3',
                            protocol: 'http',
                            nexusUrl: '35.174.104.227:8081',
                            groupId: pom.groupId,
                            version: dynamicVersion,
                            repository: 'maven-releases',
                            credentialsId: 'nexus-credentials',
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
        
        stage('Build Docker Images') {
            steps {
                script {
                    def services = ['patient-service', 'auth-service', 'billing-service', 'api-gateway', 'analytics-service']
                    
                    withDockerRegistry([credentialsId: 'docker-hub-credentials', url: 'https://index.docker.io/v1/']) {
                        services.each { service ->
                            def pom = readMavenPom file: "${service}/pom.xml"
                            
                            dir(service) {
                                def image = docker.build("ghergutmadalin/patient-managment:${service}-latest")
                            }
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
                        sh """
                            trivy image --format table -o trivy-${service}-image-report.html ghergutmadalin/patient-managment:${service}-latest
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
                            def pom = readMavenPom file: "${service}/pom.xml"
                            def image = docker.image("ghergutmadalin/patient-managment:${service}-latest")
                            image.push()
                        }
                    }
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
                
                // Publish Trivy reports if they exist
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
                
                // Publish Docker image reports if they exist
                script {
                    def imageReports = sh(script: "ls trivy-*-image-report.html 2>/dev/null || true", returnStdout: true).trim()
                    if (imageReports) {
                        publishHTML([
                            allowMissing: false,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: '.',
                            reportFiles: 'trivy-*-image-report.html',
                            reportName: 'Trivy Docker Image Reports'
                        ])
                    }
                }
            }
        }
    }
}