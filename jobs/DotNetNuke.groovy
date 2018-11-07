pipelineJob('DotNetNuke') {
   description('Archived version of DotNetNuke bulletin board')
   logRotator {
        numToKeep(10)
        artifactNumToKeep(1)
   }
   definition {
    parameters {
        stringParam('Commit', 'LATEST', 'Which commit do you want to build?')
		stringParam('Backdate', 'NONE', 'Do you wish to backdate this commit (Format YYYYMMDD)?')
    }
  
    cps {
      sandbox()
      script("""

node ('master') {
    def stashName="stash_\${BUILD_TAG}"
    def analysis_image="\${DEFAULT_ANALYSIS_TAG}:\${DEFAULT_ANALYSIS_VERSION}"
	def idir='idir'
	def config='coverity_config.xml'
    def backdate="\${Backdate}"
    def commit="\${Commit}"


    try {
        stage('Download Source') {
		    deleteDir()
			sh 'curl -f https://raw.githubusercontent.com/dnnsoftware/Dnn.Releases.Archive.5x/master/05.00.00%20RC2/DotNetNuke_05.00.00_Source_RC2.zip --output DotNetNuke.zip'
		    sh 'unzip DotNetNuke.zip'
		    sh 'rm -rf DotNetNuke.zip'
			stash stashName
        }
        node('windows')
        {
            stage('Build VB(on windows)') {
                unstash stashName
			    bat 'cov-configure --config '+config+' --cs'
			    bat 'cov-configure --config '+config+' --vb'
			    bat 'echo %COMPUTERNAME% > hostname.txt' 
		        try {
		            try {
		            bat 'devenv /upgrade DotNetNuke_VS2008.sln'
		            }
		            catch (err){
                        echo "Caught Exception: "+err
                    }        
		            try {
		                bat 'cov-build --dir '+idir+' --config '+config+' msbuild DotNetNuke_VS2008.sln'            
		            }
		            catch (err){
                        echo "Caught Exception: "+err
                    }        
                    bat 'cov-analyze --dir '+idir+' --trial --webapp-security-trial --disable-fb --strip-path %WORKSPACE%'
		        }
            	catch (err){
                    echo "Caught Exception: "+err
                }
                bat 'ls'
   	            stash stashName

            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image(analysis_image).inside(' --hostname \${BUILD_TAG} --network docker_coverity --mac-address 08:00:27:ee:25:b2 -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSWORD=\${COV_PASSWORD}') {
                    unstash stashName
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COV_PASSWORD} --project "DotNetNuke" --stream dotnetnuke'
                    sh '/opt/coverity/analysis/bin/cov-manage-emit --dir '+idir+' reset-host-name '
                    def commitCommand='/opt/coverity/analysis/bin/cov-commit-defects --dir '+idir+' --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream dotnetnuke'
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
        error("Caught Exception: "+err+" Review log to see error condition")
    }
   
}
      """.stripIndent())      
    }
  }
}
listView('Windows Jobs') {
    description('All jobs that run on Windows')
    filterBuildQueue()
    filterExecutors()
    jobs {
        name('NotepadPlusPlus')
        
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

if (!jenkins.model.Jenkins.instance.getItemByFullName('DotNetNuke')&& System.getenv("AUTO_RUN")) {
 queue('DotNetNuke')
}