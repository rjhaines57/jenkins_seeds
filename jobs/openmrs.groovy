pipelineJob('OpenMRS') {
  definition {
    cps {
      sandbox()
      script("""
node {
    def volumeName='\${BUILD_TAG}'
    def analysis_image="\${DEFAULT_ANALYSIS_TAG}:\${DEFAULT_ANALYSIS_VERSION}"
	def idir_base='/opt/coverity/idirs'
	def idir=idir_base+'/idir'
	def config=idir_base+'/coverity_config.xml'
    
	try {
    
        stage('Copy autotriage data')
        {
			copyArtifacts filter: 'auto_triage/openmrs.csv', fingerprintArtifacts: true, projectName: 'seed-job', selector: lastSuccessful()    
        }
        stage('Clone sources') {
			// deleteDir()  
			git url: 'https://github.com/openmrs/openmrs-core.git'
			sh 'git checkout 9f12d2f6c1c8ebbaa51c996cb209528d2110ab03'
        }
        stage('Build (Java & Javascript)') {
            docker.withRegistry('','docker_credentials') {  
				docker.image(analysis_image).withRun('--hostname \${BUILD_TAG} -v '+volumeName+':/opt/coverity') { c ->
					docker.image('maven:3.5.3-jdk-8').inside('--hostname \${BUILD_TAG} -e HOME=\${WORKSPACE} -v '+volumeName+':/opt/coverity') { 
						stage('Build'){
							sh 'ls -al \${HOME}'
							sh '/opt/coverity/analysis/bin/cov-configure --config '+config+' --java'
							sh '/opt/coverity/analysis/bin/cov-configure --config '+config+' --javascript'                
							sh '/opt/coverity/analysis/bin/cov-build --dir '+idir+'  --config '+config+' mvn -DskipTests=true -Dmaven.compiler.forceJavacCompilerUse=true -Dlicense.skip=true clean install  '            
							sh '/opt/coverity/analysis/bin/cov-build --dir '+idir+' --config '+config+' --no-command --fs-capture-search webapp/src'  
							try {
								sh '/opt/coverity/analysis/bin/cov-import-scm --dir '+idir+' --scm git --filename-regex \${WORKSPACE}'
							}
							catch (err)  { echo "cov-import-scm returned something unsavoury, moving on:"+err}						
						}
					}            
				}
			}
        }
        
        stage('Analysis') {
            docker.image(analysis_image).inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity ') {
                sh '/opt/coverity/analysis/bin/cov-analyze --dir '+idir+' --trial --webapp-security-trial --disable-fb'
            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image(analysis_image).inside(' --hostname \${BUILD_TAG} --network docker_coverity --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSWORD=\${COV_PASSWORD}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COV_PASSWORD} --project OpenMRS --stream openmrs'
                    sh '/opt/coverity/analysis/bin/cov-commit-defects --dir '+idir+' --strip-path \${WORKSPACE} --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream openmrs'
                }
            }
        }
	//	stage('Coverity Results') {
    //        coverityResults connectInstance: 'Test Server', connectView: 'Outstanding Security Risks', projectId: 'OpenMRS', unstable:true
    //    }
		
    }
    catch (err){
      echo "Caught Exception: "+err
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
if (!jenkins.model.Jenkins.instance.getItemByFullName('OpenMRS') && System.getenv("AUTO_RUN")) {
  queue('OpenMRS')
}
