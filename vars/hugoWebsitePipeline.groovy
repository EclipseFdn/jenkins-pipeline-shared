def call(Map givenConfig = [:]) {
  def defaultConfig = [
    "hostname": "",
    /**
     * Default to eclipsefdn/${hostname} if not set
     */
    "imageName": "",
    "namespace": "foundation-internal-webdev-apps",
    "containerName": "nginx",
  ]

  def effectiveConfig = defaultConfig + givenConfig

  if (effectiveConfig.imageName == "") {
    effectiveConfig.imageName = "eclipsefdn/${effectiveConfig.hostname}"
  }

  pipeline {
    agent {
      kubernetes {
        label 'kubedeploy-agent'
        yaml '''
        apiVersion: v1
        kind: Pod
        spec:
          containers:
          - name: kubectl
            image: eclipsefdn/kubectl:okd-c1
            command:
            - cat
            tty: true
            resources:
              limits:
                cpu: 1
                memory: 1Gi
          - name: jnlp
            resources:
              limits:
                cpu: 1
                memory: 1Gi
        '''
      }
    }

    environment {
      ENVIRONMENT = sh(
        script: """
          if [ "${env.BRANCH_NAME}" = "master" ]; then
            printf "production"
          else
            printf "${env.BRANCH_NAME}"
          fi
        """,
        returnStdout: true
      )
      TAG_NAME = sh(
        script: """
          GIT_COMMIT_SHORT=\$(git rev-parse --short ${env.GIT_COMMIT})
          if [ "${env.ENVIRONMENT}" = "" ]; then
            printf \${GIT_COMMIT_SHORT}-${env.BUILD_NUMBER}
          else
            printf ${env.ENVIRONMENT}-\${GIT_COMMIT_SHORT}-${env.BUILD_NUMBER}
          fi
        """,
        returnStdout: true
      )
      BASE_NGINX_IMAGE_TAG = sh(
        script: """
          if [ "${env.ENVIRONMENT}" = "production" ]; then 
            printf "stable-alpine"
          else
            printf "stable-alpine-for-staging"
          fi
        """,
        returnStdout: true
      )
    }

    options {
      buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    triggers { 
      // build once a week to keep up with parents images updates
      cron('H H * * H') 
    }

    stages {
      stage('Build docker image') {
        agent {
          label 'docker-build'
        }
        steps {
          sh """
            docker build --pull --build-arg NGINX_IMAGE_TAG="${env.BASE_NGINX_IMAGE_TAG}" -t ${effectiveConfig.imageName}:${env.TAG_NAME} -t ${effectiveConfig.imageName}:latest .
          """
        }
      }

      stage('Push docker image') {
        agent {
          label 'docker-build'
        }
        when {
          anyOf {
            environment name: 'ENVIRONMENT', value: 'production'
            environment name: 'ENVIRONMENT', value: 'staging'
          }
        }
        steps {
          withDockerRegistry([credentialsId: '04264967-fea0-40c2-bf60-09af5aeba60f', url: 'https://index.docker.io/v1/']) {
            sh """
              docker push ${effectiveConfig.imageName}:${env.TAG_NAME}
              docker push ${effectiveConfig.imageName}:latest
            """
          }
        }
      }

      stage('Deploy to cluster') {
        when {
          anyOf {
            environment name: 'ENVIRONMENT', value: 'production'
            environment name: 'ENVIRONMENT', value: 'staging'
          }
        }
        steps {
          container('kubectl') {
            updateContainerImage([
              credentialsId: '6ad93d41-e6fc-4462-b6bc-297e360784fd',
              namespace: "${effectiveConfig.namespace}",
              selector: "app=${effectiveConfig.hostname},environment=${env.ENVIRONMENT}",
              containerName: "${containerName}",
              newImageRef: "${effectiveConfig.imageName}:${env.TAG_NAME}"
            ])
          }
        }
      }
    }

    post {
      always {
        deleteDir() /* clean up workspace */
        //sendNotifications currentBuild
      }
    }
  }

}