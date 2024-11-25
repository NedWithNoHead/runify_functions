def call(String serviceName, String dockerRepo) {
    pipeline {
        agent { label 'python_agent' }
        
        environment {
            DOCKERHUB_CREDS = credentials('DockerHub')
            VENV = "${WORKSPACE}/venv"
            SERVICE_DIR = "${WORKSPACE}/${serviceName}"
        }
        
        stages {
            stage('Setup') {
                steps {
                    sh """
                        # Change to service directory
                        cd ${WORKSPACE}/${serviceName}
                        
                        # Debug info
                        echo "Current directory:"
                        pwd
                        echo "Directory contents:"
                        ls -la
                        
                        # Setup virtual environment
                        python3 -m venv ${WORKSPACE}/venv
                        . ${WORKSPACE}/venv/bin/activate
                        pip install pylint safety
                    """
                }
            }
            
            stage('Lint') {
                steps {
                    sh """
                        cd ${WORKSPACE}/${serviceName}
                        . ${WORKSPACE}/venv/bin/activate
                        find . -type f -name "*.py" > python_files.txt
                        if [ -s python_files.txt ]; then
                            pylint --fail-under=5 \$(cat python_files.txt)
                        else
                            echo "No Python files found to lint"
                            exit 1
                        fi
                    """
                }
            }
            
            stage('Security Scan') {
                steps {
                    sh """
                        cd ${WORKSPACE}/${serviceName}
                        . ${WORKSPACE}/venv/bin/activate
                        safety check -r requirements.txt
                    """
                }
            }
            
            stage('Package') {
                steps {
                    script {
                        sh """
                            cd ${WORKSPACE}/${serviceName}
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
        
        post {
            always {
                sh 'rm -rf venv python_files.txt'
            }
        }
    }
}
