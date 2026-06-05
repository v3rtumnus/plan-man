pipeline {
    agent any

    triggers { 
        pollSCM('*/5 * * * *') 
    }
    
    stages {
        stage('Clone sources') {
            steps {
                git credentialsId: 'github-ssh',
                    url: 'git@github.com:v3rtumnus/plan-man.git'
            }
        }

        stage('Create bootable jar') {
            steps {
                sh './gradlew clean bootJar'
            }
        }

        stage('Deploy service') {
            steps {
                sh 'docker compose -f /opt/appl/plan-man/docker-compose.yml -p plan-man down'
                sh 'cp build/libs/plan-man.jar /opt/appl/plan-man/docker/plan-man.jar'
                sh 'docker compose -f /opt/appl/plan-man/docker-compose.yml -p plan-man up -d'
            }
        }
    }
}
