def call(String serviceName, String dockerRepo) {
    pipeline {
        agent { label 'python_agent' }
        
        environment {
            DOCKERHUB_CREDS = credentials('DockerHub')
        }
        
        stages {
            stage('Lint') {
                steps {
                    sh '''
                        python3 -m pip install pylint
                        pylint --fail-under=5 *.py
                    '''
                }
            }
            
            stage('Security Scan') {
                steps {
                    sh '''
                        python3 -m pip install safety
                        safety check -r requirements.txt
                    '''
                }
            }
            
            stage('Package') {
                steps {
                    script {
                        sh """
                            docker login -u ${DOCKERHUB_CREDS_USR} -p ${DOCKERHUB_CREDS_PSW}
                            docker build -t ${DOCKERHUB_CREDS_USR}/${dockerRepo}:latest .
                            docker push ${DOCKERHUB_CREDS_USR}/${dockerRepo}:latest
                        """
                    }
                }
            }
            
            stage('Deploy') {
                steps {
                    withCredentials([sshUserPrivateKey(credentialsId: '3855-ssh-key', keyFileVariable: 'SSH_KEY')]) {
                        sh """
                            ssh -i \$SSH_KEY -o StrictHostKeyChecking=no azureuser@runify-deployment.canadaeast.cloudapp.azure.com \
                            "docker compose pull ${serviceName} && docker compose up -d ${serviceName}"
                        """
                    }
                }
            }
        }
    }
}