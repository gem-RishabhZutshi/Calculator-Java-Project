pipeline {
    agent any

    tools {
        maven 'maven' 
    }

    environment {
        // Define SonarQube environment variables
        SONAR_SCANNER_OPTS = '-Xmx512m' // Memory limit for the scanner
        ENVIRONMENT = "${env.BRANCH_NAME}"
    }

    stages {
        stage('Checkout Code') {
            steps {
                // Checkout your code from the Git repository
                git 'https://github.com/gem-RishabhZutshi/Calculator-Java-Project.git'
            }
        }

        stage('Build') {
            steps {
                // Build the project using Maven
                sh 'mvn clean package -DskipTests'
            }
        }

        
        stage('Code Coverage with JaCoCo') {
            steps {
                // Run tests with JaCoCo agent add jacoco dependency in pom.xml
                // Run Xvfb to simulate a display-
                script {
                    // Start Xvfb in the background
                    sh 'Xvfb :99 -screen 0 1920x1080x24 &'
                    // Set the DISPLAY environment variable
                    env.DISPLAY = ':99'
                    // Run the tests
                    sh 'mvn test'
                }
            }
        }

        stage('Generate JaCoCo Report') {
            steps {
                // Generate the JaCoCo report for code coverage
                sh 'mvn jacoco:report'
            }
        }

        stage('PMD Analysis') {
            steps {
                // Run PMD to check for Cyclomatic Complexity and other rules
                script {
                   sh 'mvn site'
                }
            }
        }


        stage('OWASP Dependency-Check') {
            steps {
                // Run OWASP Dependency check scan
                dependencyCheck additionalArguments: ''' 
                    -o './'
                    -s './'
                    -f 'ALL' 
                    --prettyPrint''', odcInstallation: 'OWASP-Dependency-Check', nvdCredentialsId: 'nvdCredentialsId' 
        
                // Publish Dependency-Check report and fail if at least 1 critical vulnerability is found
                dependencyCheckPublisher pattern: 'dependency-check-report.xml', 
                    failedTotalCritical: 1,    // Fail build if at least 1 total critical vulnerability is found
                    stopBuild: true            // Stop the build if the threshold is violate                
           }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQubeServer') { 
                    sh 'mvn sonar:sonar -Dsonar.projectKey=java-backend'
                }
            }
        }

        stage('Quality Gate') {
            steps {
                script {
                    timeout(time: 5, unit: 'MINUTES') {
                        def qualityGate = waitForQualityGate()
                        if (qualityGate.status != 'OK') {
                            error "Pipeline aborted due to quality gate failure: ${qualityGate.status}"
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    // Define variables for ECR repository and Docker image
                    def accountid = 'accountid'
                    def ecrRepo = 'ecrRepo'
                    def ecrRepoUri = '${accountid}.dkr.ecr.region.amazonaws.com/${ecrRepo}'
                    def imageTag = "${env.GIT_COMMIT}" // Tag with the latest commit hash

                    // Step 1: Build Docker image
                    sh 'docker build -t my-java-app .'

                    // Step 2: Authenticate to Amazon ECR
                    sh '''
                        aws ecr get-login-password --region region | docker login --username AWS --password-stdin ${ecrRepoUri}
                    '''

                    // Step 3: Tag Docker image with ECR repository URI
                    sh "docker tag my-java-app:${imageTag} ${ecrRepoUri}:${imageTag}"

                    // Step 4: Push Docker image to ECR
                    sh "docker push ${ecrRepoUri}:${imageTag}"

                    // Step 5: Deploy to EKS
                    sh """
                        aws eks update-kubeconfig --region region --name your-cluster-name
                        kubectl set image deployment/my-deployment-name my-container=${ecrRepoUri}:${imageTag} --record
                    """
                }
            }
       }


    }

    post {
        always {
            archiveArtifacts artifacts: 'target/site/pmd.html, dependency-check-report.xml, target/site/jacoco/jacoco.xml', allowEmptyArchive: true
        }

        failure {
            mail to: 'rishabh.zutshi@gmail.com',
                 subject: "Pipeline Failed: ${currentBuild.fullDisplayName}",
                 body: "Please review the build at ${env.BUILD_URL}"
        }


    }
}