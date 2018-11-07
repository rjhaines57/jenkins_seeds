pipelineJob('PHPNuke') {
  description('PHP-Nuke is a web-based automated news publishing and content management system based on PHP and MySQL http://www.phpnuke.ir/')
   logRotator {
        numToKeep(10)
        artifactNumToKeep(1)
   }
   definition {
    parameters {
        stringParam('Commit', '988b71c099f0e669459e3c3093a535957ec73ed1', 'Which commit do you want to build?')
		stringParam('Backdate', 'NONE', 'Do you wish to backdate this commit (Format YYYYMMDD)?')
    }
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
    def backdate="${Backdate}"
    def commit="${Commit}"

    try {
        stage('Clone sources') {
		//  deleteDir()
			git url: 'https://github.com/phpnuke/phpnuke.git'
        }
        stage('Build (php)') {
			docker.withRegistry('','docker_credentials') { 
				docker.image(analysis_image).inside('--hostname \${BUILD_TAG}  -v '+volumeName+':/opt/coverity') { 
					sh '/opt/coverity/analysis/bin/cov-configure --config '+config+' --php'
					sh '/opt/coverity/analysis/bin/cov-build --dir '+idir+' --config '+config+' --no-command --fs-capture-search .'            
				}
			}
        }
        stage('Analysis') {
            docker.withRegistry('','docker_credentials') {  		
				docker.image(analysis_image).inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity') {
					sh '/opt/coverity/analysis/bin/cov-analyze --dir '+idir+' --trial --webapp-security-trial'
				}
			}
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image(analysis_image).inside('--network docker_coverity --hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSWORD=\${COV_PASSWORD}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COV_PASSWORD} --project PHPNuke --stream phpnuke'
					def commitCommand='/opt/coverity/analysis/bin/cov-commit-defects --dir '+idir+' --strip-path \${WORKSPACE} --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream phpnuke'
                    if (!backdate.contains("NONE"))
                    {
                        commitCommand=commitCommand+" --backdate "+backdate            
                    }
                    sh commitCommand 
                }
            }
        }
    }
    catch (err){
        echo "Caught Exception: "+err
    }
    finally
    {
        stage('cleanup volume') {
            // Comment out the line below to keep the idir volume
            sh 'docker volume rm '+volumeName
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
