#!/usr/bin/env groovy

import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

/**
 * Send notifications based on build status string
 */
def call(RunWrapper currentBuild) {
  // build status of null means successful
  buildStatus =  currentBuild.result ?: 'SUCCESS'
  previousBuildStatus =  currentBuild.previousBuild?.result ?: 'SUCCESS'

  // Default values
  def colorCode = '#A20E10'
  def subject = ''

  if (buildStatus == 'ABORTED') {
    colorCode = '#A2A2A2'
    if (previousBuildStatus != 'SUCCESS') {
      subject = 'Aborted'
    }
  } else if (buildStatus == 'FAILURE') {
    colorCode = '#A20E10'
    if (previousBuildStatus == 'FAILURE') {
      subject = 'Still failing'
    } else {
      subject = 'Broken'
    }
  } else if (buildStatus == 'UNSTABLE') {
    colorCode = '#E5AD00'
    if (previousBuildStatus == 'UNSTABLE') {
      subject = 'Still unstable'
    } else {
      subject = "Unstable"
    }
  } else { // buildStatus == "SUCCESS"
    colorCode = '#34B787'
    if (previousBuildStatus != 'SUCCESS') {
      subject = "Back to normal"
    }
  }
  
  if (subject != '') {
    slackSend (color: colorCode, message: "${env.JOB_NAME} ${env.BUILD_NUMBER} - ${subject} (<a href=\"${env.BUILD_URL}/console\">logs</a>)")

    emailext (
      subject: "${subject}: ${env.JOB_NAME} ${env.BUILD_NUMBER}",
      body: """
        <p><a href='${env.BUILD_URL}'>See console output at ${env.BUILD_URL}</a></p>
        <p>----</p>
        <pre>${currentBuild.rawBuild.log(128).join('<br/>')}</pre>
      """,
      recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
    )
  }

  // Send notifications
  
}