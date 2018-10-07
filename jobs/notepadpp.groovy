pipelineJob('NotePadPlusPlus') {
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
        stage('Clone sources') {
		    deleteDir()
			git url: 'https://github.com/notepad-plus-plus/notepad-plus-plus.git'
			print "["+commit+"]"
			if (!commit.contains("LATEST"))
			{
			 sh 'git checkout '+commit   
			}
			stash stashName
        }
        node('windows')
        {
            stage('Build C/C++(on windows)') {
                unstash stashName
			    bat 'cov-configure --config '+config+' --msvc'
			    bat 'echo %COMPUTERNAME% > hostname.txt' 
		        try {    
            	    bat 'set CL=/D_ALLOW_RTCc_IN_STL && cov-build --dir '+idir+' --config '+config+' msbuild.exe "PowerEditor\\\\visual.net\\\\notepadPlus.vcxproj"  /t:rebuild /p:PlatformToolset=V140 /p:Configuration="Unicode Debug" /p:Platform=x64 /p:TreatWarningAsError=false '            
		        }
            	catch (err){
                    echo "Caught Exception: "+err
                }
                bat 'ls'
   	            stash stashName
            }
        }
        stage('Analysis') {
            unstash stashName
            docker.image(analysis_image).inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2') {
                sh '/opt/coverity/analysis/bin/cov-manage-emit --dir '+idir+' add-other-hosts'
                sh '/opt/coverity/analysis/bin/cov-analyze --dir '+idir+' --trial --webapp-security-trial --disable-fb'
            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image(analysis_image).inside(' --hostname \${BUILD_TAG} --network docker_coverity --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSWORD=\${COV_PASSWORD}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COV_PASSWORD} --project "NotepadPlusPlus" --stream notepadplusplus'
                    def commitCommand='/opt/coverity/analysis/bin/cov-commit-defects --dir '+idir+' --strip-path \${WORKSPACE} --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream notepadplusplus'
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

if (!jenkins.model.Jenkins.instance.getItemByFullName('NotePadPlusPlus')&& System.getenv("AUTO_RUN")) {
 queue('NotePadPlusPlus')
}