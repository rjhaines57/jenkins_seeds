pipelineJob('Jenkins') {
  definition {
    cps {
      sandbox()
      script("""
node {
    def volumeId
    def idir_volume=\${BUILD_TAG}
    try {
    
        stage('Clean directory')
        {
		copyArtifacts filter: 'auto_triage/openmrs.csv', fingerprintArtifacts: true, projectName: 'seed-job', selector: lastSuccessful()    
        //  deleteDir()    
        }
        stage('Clone sources') {
            git url: 'https://github.com/openmrs/openmrs-core.git'
			sh 'git checkout 9f12d2f6c1c8ebbaa51c996cb209528d2110ab03'
        }
        stage('Build (Java & Javascript)') {
      
            docker.image('clittlej/sig-emea-ses:analysis-2018.03').withRun('--hostname \${BUILD_TAG} -v \${BUILD_TAG}:/opt/coverity') { c ->
                docker.image('maven:3.5.3-jdk-8').inside('--hostname \${BUILD_TAG} -e HOME=\${WORKSPACE} -v maven:\${WORKSPACE}/.m2 -v \${BUILD_TAG}:/opt/coverity') { 
                    stage('Build'){
                        sh '/opt/coverity/analysis/bin/cov-configure --config /opt/coverity/idirs/coverity_config.xml --java'
                        sh '/opt/coverity/analysis/bin/cov-configure --config /opt/coverity/idirs/coverity_config.xml --javascript'                
                        sh '/opt/coverity/analysis/bin/cov-build --dir /opt/coverity/idirs/idir  --config /opt/coverity/idirs/coverity_config.xml mvn -Duser.home=\${HOME} -DskipTests=true -Dmaven.compiler.forceJavacCompilerUse=true -Dlicense.skip=true clean install  '            
                        sh '/opt/coverity/analysis/bin/cov-build --dir /opt/coverity/idirs/idir --config /opt/coverity/idirs/coverity_config.xml --no-command --fs-capture-search webapp/src'            
                    }
                }            
            }
        }
        
        stage('Analysis') {
            docker.image('clittlej/sig-emea-ses:analysis-2018.03').inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v \${BUILD_TAG}/opt/coverity') {
                sh '/opt/coverity/analysis/bin/cov-analyze --dir /opt/coverity/idirs/idir --trial --webapp-security-trial --disable-fb'
            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image('clittlej/sig-emea-ses:analysis-2018.03').inside(' --hostname \${BUILD_TAG} --network docker_coverity --mac-address 08:00:27:ee:25:b2 -v \${BUILD_TAG}:/opt/coverity -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSPHRASE=\${COV_PASSPHRASE}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password coverity --project OpenMRS --stream openmrs'
                    sh '/opt/coverity/analysis/bin/cov-commit-defects --dir /opt/coverity/idirs/idir --strip-path \${WORKSPACE} --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream openmrs'
                }
            }
        }
	//	stage('Coverity Results') {
    //        coverityResults connectInstance: 'Test Server', connectView: 'Outstanding Security Risks', projectId: 'OpenMRS', unstable:true
    //    }
		
    }
    finally
    {
        stage('cleanup volume') {
            // Delete volume
            sh 'docker volume rm \${BUILD_TAG}'
        }
    }
}
      	  """.stripIndent())      
    }
  }
}
if (!jenkins.model.Jenkins.instance.getItemByFullName('Jenkins') && System.getenv("AUTO_RUN")) {
 queue('Jenkins')
}
