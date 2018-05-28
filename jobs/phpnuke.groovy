pipelineJob('PHPNuke') {
  definition {
    cps {
      sandbox()
      script("""
node {
    def volumeId
    try {
    
        stage('Setup idir')
        {
	    //	copyArtifacts filter: 'auto_triage/openmrs.csv', fingerprintArtifacts: true, projectName: 'seed-job', selector: lastSuccessful()    
        //  Un comment the line below to remove repo
        //  deleteDir()    
        volumeId=sh(returnStdout: true, script: '/usr/bin/docker volume create').trim() 
        }
        stage('Clone sources') {
            git url: 'https://github.com/phpnuke/phpnuke.git'
        }
        stage('Build (php)') {
      
            docker.image('cov-analysis:2018.03').inside('--hostname \${BUILD_TAG} -v '+volumeId+':/opt/coverity/idirs -v coverity_tools:/opt/coverity') { 
                sh '/opt/coverity/analysis/bin/cov-configure --config /opt/coverity/idirs/coverity_config.xml --php'
                sh '/opt/coverity/analysis/bin/cov-build --dir /opt/coverity/idirs/idir --config /opt/coverity/idirs/coverity_config.xml --no-command --fs-capture-search .'            
            }
        }
        stage('Analysis') {
            docker.image('cov-analysis:2018.03').inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeId+':/opt/coverity/idirs') {
                sh '/opt/coverity/analysis/bin/cov-analyze --dir /opt/coverity/idirs/idir --trial --webapp-security-trial'
            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image('cov-analysis:2018.03').inside('--network docker_coverity --hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeId+':/opt/coverity/idirs -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSPHRASE=\${COV_PASSPHRASE}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password coverity --project PHPNuke --stream phpnuke'
                    sh '/opt/coverity/analysis/bin/cov-commit-defects --dir /opt/coverity/idirs/idir --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream phpnuke'
                }
            }
        }
    }
    finally
    {
    stage('cleanup volume') {
        // Comment out the line below to keep the idir volume
        sh 'docker volume rm '+volumeId
    }
    }
}
	  """.stripIndent())      
    }
  }
}

 queue('PHPNuke')

