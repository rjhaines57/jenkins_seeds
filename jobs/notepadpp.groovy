pipelineJob('NotePad++') {
  definition {
    cps {
      sandbox()
      script("""
node {
    def volumeId
    try {
    
        stage('Setup idir volume')
        {
		//copyArtifacts filter: 'config/notepadpp.tgz', fingerprintArtifacts: true, projectName: 'seed-job', selector: lastSuccessful()    
       //  deleteDir()    
            volumeId=sh(returnStdout: true, script: '/usr/bin/docker volume create').trim()
        }
        stage('retrieve idir') {

            docker.image('clittlej/sig-emea-ses:data-2018.03').inside() { 
                sh 'cp /idirs/notepadpp.tgz .'
            }
        }
        stage('Analysis') {
            docker.image('cov-analysis:2018.03').inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeId+':/opt/coverity/idirs') {
			    sh 'tar zxvf notepadpp.tgz && mv idir /opt/coverity/idirs'
				sh '/opt/coverity/analysis/bin/cov-manage-emit --dir /opt/coverity/idirs/idir reset-host-name'
                sh '/opt/coverity/analysis/bin/cov-analyze --dir /opt/coverity/idirs/idir --trial'
            }
        
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image('cov-analysis:2018.03').inside(' --hostname \${BUILD_TAG} --network docker_coverity --mac-address 08:00:27:ee:25:b2 -v '+volumeId+':/opt/coverity/idirs -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSPHRASE=\${COV_PASSPHRASE}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password coverity --project NotePadPlusPlus --stream notepadpp'
                    sh '/opt/coverity/analysis/bin/cov-commit-defects --dir /opt/coverity/idirs/idir --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream notepadpp'
                }
            }
        }
    }
	
    finally
    {
    stage('cleanup volume') {
    // Delete volume
    sh 'docker volume rm '+volumeId
    }
    }
}
      
      """.stripIndent())      
    }
  }
}
if (!jenkins.model.Jenkins.instance.getItemByFullName('NotePad++')&& System.getenv("AUTO_RUN")) {
 queue('NotePad++')
}
