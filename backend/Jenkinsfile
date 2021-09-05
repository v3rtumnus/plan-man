pipeline {
    agent any

    environment {
        GRADLE_USER_HOME = '/var/lib/jenkins/.gradle'
    }

    stages {
        stage('Clone sources') {
            steps {
                git credentialsId: 'jenkins-ssh',
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
                sh 'sudo service plan-man stop'
                sh 'cp build/libs/plan-man.jar /opt/plan-man/'
                sh 'sudo chmod a+x /opt/plan-man/plan-man.jar'
                sh 'sudo service plan-man start'
            }
        }
    }
}