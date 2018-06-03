pipelineJob('NotePad++') {
  definition {
    cps {
      sandbox()
      script("""
node {
    // Set volume Name to \${BUILD_TAG} for each build gives new volume and therefore clean
    // environment. Use \${JOB_NAME} for incremental builds
    def volumeName='\${BUILD_TAG}'
    def analysis_image="\${DEFAULT_ANALYSIS_TAG}:\${DEFAULT_ANALYSIS_VERSION}"
	def idir_base='/opt/coverity/idirs'
	def idir=idir_base+'/idir'
	def config=idir_base+'/coverity_config.xml'

    try {
    
        stage('Setup idir volume')
        {
		//copyArtifacts filter: 'config/notepadpp.tgz', fingerprintArtifacts: true, projectName: 'seed-job', selector: lastSuccessful()    
       //  deleteDir()    
            volumeId=sh(returnStdout: true, script: '/usr/bin/docker volume create').trim()
        }
        stage('retrieve idir') {
			docker.withRegistry('','docker_credentials') {  
				docker.image('clittlej/sig-emea-ses:data-2018.03').inside() { 
					sh 'cp /idirs/notepadpp.tgz .'
				}
			}
        }
        stage('Analysis') {
            docker.withRegistry('','docker_credentials') {  		
				docker.image(analysis_image).inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity') {
					sh 'tar zxvf notepadpp.tgz && mv idir /opt/coverity/idirs'
					sh '/opt/coverity/analysis/bin/cov-manage-emit --dir '+idir+' reset-host-name'
					sh '/opt/coverity/analysis/bin/cov-analyze --dir '+idir+' --trial'
				}
			}
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image(analysis_image).inside(' --hostname \${BUILD_TAG} --network docker_coverity --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSWORD=\${COV_PASSWORD}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COVERITY} --project NotePadPlusPlus --stream notepadpp'
                    sh '/opt/coverity/analysis/bin/cov-commit-defects --dir '+idir+' --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream notepadpp'
                }
            }
        }
    }
	
    finally
    {
    stage('cleanup volume') {
    // Delete volume
    sh 'docker volume rm '+volumeName
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
