pipelineJob('megamek') {
  definition {
    cps {
      sandbox()
      script("""

node {
    def volumeId
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
      
            docker.image('clittlej/sig-emea-ses:analysis-2018.03').withRun('--hostname \${BUILD_TAG} -v '+volumeId+':/opt/coverity/idirs -v coverity_tools:/opt/coverity') { c ->
                docker.image('webratio/ant').inside('--hostname \${BUILD_TAG} -v '+volumeId+':/opt/coverity/idirs -v coverity_tools:/opt/coverity:ro -e JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8') { 
                    sh '/opt/coverity/analysis/bin/cov-configure --config /opt/coverity/idirs/coverity_config.xml --java'
                    sh '/opt/coverity/analysis/bin/cov-build --dir /opt/coverity/idirs/idir  --config /opt/coverity/idirs/coverity_config.xml ant'            
                    
                }            
            }
        }
        stage('Analysis') {
            docker.image('clittlej/sig-emea-ses:analysis-2018.03').inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeId+':/opt/coverity/idirs') {
                sh '/opt/coverity/analysis/bin/cov-analyze --dir /opt/coverity/idirs/idir --trial'
            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image('clittlej/sig-emea-ses:analysis-2018.03').inside(' --hostname \${BUILD_TAG} --network docker_coverity --mac-address 08:00:27:ee:25:b2 -v '+volumeId+':/opt/coverity/idirs -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSPHRASE=\${COV_PASSPHRASE}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password coverity --project MegaMek --stream megamek'
                    sh '/opt/coverity/analysis/bin/cov-commit-defects --dir /opt/coverity/idirs/idir --strip-path \${WORKSPACE} --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream megamek'
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
    sh 'docker volume rm '+volumeId
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
