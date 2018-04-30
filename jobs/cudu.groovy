job('cudu') {
    scm {
        
    }
    triggers {
    }
    steps {
		shell('''#!/bin/bash

# Fixed configuration from the container. DO NOT CHANGE
COV_HOME=/opt/coverity


#configuration for this recipe
PROJECT=cudu
STREAM=${PROJECT}
REPO=https://github.com/fev/cudu.git
WAR=webapp/target/*.war
FS_CAPTURE_SEARCH="backend frontend"

#Language/Compiler Selection
#Choose from: java javascript python php gcc clang ruby swift scala vb
#Use space separated list
#e.g CONFIGURE="java php javascript"
CONFIGURE="java javascript"

. ${COV_HOME}/build_helper.sh
''')
    
	
	}
 
}

queue('cudu')
