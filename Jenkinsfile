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
                sh 'docker-compose -f /var/plan-man-data/docker-compose.yml down'
                sh 'cp build/libs/plan-man.jar /var/plan-man-data/docker'
                sh 'docker-compose -f /var/plan-man-data/docker-compose.yml build'
                sh 'docker-compose -f /var/plan-man-data/docker-compose.yml up -d'
            }
        }
    }
}
