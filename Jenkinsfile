node {
    stage 'Clone sources'
        git credentialsId: 'jenkins-ssh',
            url: 'git@github.com:v3rtumnus/plan-man.git'

    stage 'Create bootable jar'
        sh './gradlew clean bootJar'

    stage 'Deploy service'
        sh 'sudo service plan-man stop'
        sh 'cp build/libs/plan-man.jar /opt/plan-man/'
        sh 'sudo chmod a+x /opt/plan-man/plan-man.jar'
        sh 'sudo service plan-man start'
}
