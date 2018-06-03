pipelineJob('PHPNuke') {
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
        stage('Clone sources') {
		//  deleteDir()
			git url: 'https://github.com/phpnuke/phpnuke.git'
        }
        stage('Build (php)') {
			docker.withRegistry('','docker_credentials') { 
				docker.image(analysis_image).inside('--hostname \${BUILD_TAG}  -v '+volumeName+':/opt/coverity') { 
					sh '/opt/coverity/analysis/bin/cov-configure '+config+' --php'
					sh '/opt/coverity/analysis/bin/cov-build '+idir+' '+config+' --no-command --fs-capture-search .'            
				}
			}
        }
        stage('Analysis') {
            docker.withRegistry('','docker_credentials') {  		
				docker.image(analysis_image).inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 ') {
					sh '/opt/coverity/analysis/bin/cov-analyze '+idir+' --trial --webapp-security-trial'
				}
			}
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image(analysis_image).inside('--network docker_coverity --hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2  -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSWORD=\${COV_PASSWORD}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COVERITY} --project PHPNuke --stream phpnuke'
                    sh '/opt/coverity/analysis/bin/cov-commit-defects '+idir+' --strip-path \${WORKSPACE} --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream phpnuke'
                }
            }
        }
    }
	
    finally
    {
    stage('cleanup volume') {
        // Comment out the line below to keep the idir volume
        sh 'docker volume rm'+volumeName
    }
    }
}
	  """.stripIndent())      
    }
  }
}
if (!jenkins.model.Jenkins.instance.getItemByFullName('PHPNuke') && System.getenv("AUTO_RUN")) {
 queue('PHPNuke')
}
