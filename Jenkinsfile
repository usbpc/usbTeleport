pipeline {
  agent {
    docker {
      image 'gradle:jdk8-alpine'
      args '-v gradle_cache:/root/.gradle -m 200m'
    }
    
  }
  stages {
    stage('Init') {
      steps {
        sh 'gradle clean -Dorg.gradle.parallel=false -Dorg.gradle.daemon=false -Dkotlin.incremental=false'
      }
    }
    stage('Build') {
      steps {
        sh 'gradle shadowJar -Dorg.gradle.parallel=false -Dorg.gradle.daemon=false -Dkotlin.incremental=false'
      }
    }
    stage('Archive') {
      steps {
        archiveArtifacts(artifacts: 'build/libs/*', onlyIfSuccessful: true)
      }
    }
  }
}