pipelineJob('megamek') {
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
    
        stage('Setup idir')
        {
		copyArtifacts filter: 'auto_triage/openmrs.csv', fingerprintArtifacts: true, projectName: 'seed-job', selector: lastSuccessful()    
       //  deleteDir()    
        volumeId=sh(returnStdout: true, script: '/usr/bin/docker volume create').trim() 
        }
        stage('Clone sources') {
            git  branch: 'rel-0-36-0', url: 'https://github.com/MegaMek/megamek.git'
			sh 'git checkout 63ee78c71bd33fd39a71dd908efdc0c80a2d18f7'
        }
        stage('Build (Java)') {
            docker.withRegistry('','docker_credentials') {        
				docker.image(analysis_image).withRun('--hostname \${BUILD_TAG}  -v '+volumeName+':/opt/coverity') { c ->
					docker.image('webratio/ant').inside('--hostname \${BUILD_TAG}  -v '+volumeName+':/opt/coverity:ro -e JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8') { 
						sh '/opt/coverity/analysis/bin/cov-configure --config '+config+' --java'
						sh '/opt/coverity/analysis/bin/cov-build --dir '+idir+'  --config '+config+' ant'     
						try {
							sh '/opt/coverity/analysis/bin/cov-import-scm --dir '+idir+' --scm git --filename-regex \${WORKSPACE}'
						}
						catch (err)  { echo "cov-import-scm returned something unsavoury, moving on:"+err}												
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
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COVERITY} --project MegaMek --stream megamek'
                    sh '/opt/coverity/analysis/bin/cov-commit-defects --dir'+idir+' --strip-path \${WORKSPACE} --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream megamek'
                }
            }
        }
		stage('Coverity Results') {
            coverityResults connectInstance: 'Test Server', connectView: 'Outstanding Security Risks', projectId: 'OpenMRS', unstable:true
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
if (!jenkins.model.Jenkins.instance.getItemByFullName('megamek') && System.getenv("AUTO_RUN")) {
 queue('megamek')
}
