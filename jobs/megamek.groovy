pipelineJob('megamek') {
  description('MegaMek is a networked Java clone of BattleTech, a turn-based sci-fi boardgame for 2+ players. Fight using giant robots, tanks, and/or infantry on a hex-based map. http://www.megamek.org')
   logRotator {
        numToKeep(10)
        artifactNumToKeep(1)
   }
   definition {
    parameters {
        stringParam('Commit', '63ee78c71bd33fd39a71dd908efdc0c80a2d18f7', 'Which commit do you want to build?')
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
    def backdate="\${Backdate}"
    def commit="\${Commit}"


    try {
        stage('Clone sources') {
            git  branch: 'rel-0-36-0', url: 'https://github.com/MegaMek/megamek.git'
			print "["+commit+"]"
			if (!commit.contains("LATEST")) {
			 sh 'git checkout '+commit   
			}
        }
        stage('Build (Java)') {
            docker.withRegistry('','docker_credentials') {        
				docker.image(analysis_image).withRun('--hostname \${BUILD_TAG}  -v '+volumeName+':/opt/coverity') { c ->
					docker.image('webratio/ant').inside('--hostname \${BUILD_TAG}  -v '+volumeName+':/opt/coverity -e JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8') { 
						sh '/opt/coverity/analysis/bin/cov-configure --config '+config+' --java'
						sh '/opt/coverity/analysis/bin/cov-build --dir '+idir+'  --config '+config+' ant'     
					}            
				}
			}
        }
        stage('Analysis') {
            docker.image(analysis_image).inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity') {
        	    sh '/opt/coverity/analysis/bin/cov-analyze --dir '+idir+' --trial'
            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image(analysis_image).inside(' --hostname \${BUILD_TAG} --network docker_coverity --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSWORD=\${COV_PASSWORD}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COV_PASSWORD} --project MegaMek --stream megamek'
					def commitCommand='/opt/coverity/analysis/bin/cov-commit-defects --dir '+idir+' --strip-path \${WORKSPACE} --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream megamek'
                    if (!backdate.contains("NONE"))
                    {
                        commitCommand=commitCommand+" --backdate "+backdate            
                    }
                    sh commitCommand 

                }
            }
        }
//		stage('Coverity Results') {
//            coverityResults connectInstance: 'Test Server', connectView: 'Outstanding Security Risks', projectId: 'MegaMek', unstable:true
//        }
		
    }
    catch (err){
        echo "Caught Exception: "+err
    }
    finally
    {
        stage('Cleanup volume') {
        // Delete volume
        sh 'docker volume rm '+volumeName
        }
    }
}      """.stripIndent())      
    }
  }
}
if (!jenkins.model.Jenkins.instance.getItemByFullName('megamek') && System.getenv("AUTO_RUN")) {
 queue('megamek')
}
