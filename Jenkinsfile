pipeline {
  agent {
    docker {
      image 'gradle:jdk8-alpine'
      args '-v gradle_cache:/root/.gradle -m 1g'
    }
    
  }
  stages {
    stage('Init') {
      steps {
        sh 'gradle clean --no-deamon'
      }
    }
    stage('Build') {
      steps {
        sh 'gradle shadowJar --no-deamon -Dorg.gradle.parallel=false'
      }
    }
    stage('Archive') {
      steps {
        archiveArtifacts(artifacts: 'build/libs/*', onlyIfSuccessful: true)
      }
    }
  }
}