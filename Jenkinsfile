pipeline {
  agent {
    docker {
      args '-v gradle_cache:/root/.gradle -m 200m'
      image 'gradle:jdk8-alpine'
    }
    
  }
  stages {
    stage('Init') {
      steps {
        sh 'gradle clean'
      }
    }
    stage('Build') {
      steps {
        sh 'gradle shadowJar'
      }
    }
    stage('Archive') {
      steps {
        archiveArtifacts(artifacts: 'build/libs/*', onlyIfSuccessful: true)
      }
    }
  }
}