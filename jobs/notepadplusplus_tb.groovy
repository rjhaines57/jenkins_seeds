pipelineJob('NotePadPlusPlus_tb') {
  definition {
    parameters {
        stringParam('Start', '1095', 'How many days back to you wish to start?(Default 3 years)')
		stringParam('Increment', '21', 'How many days between builds(Default 21)')
    }
  
    cps {
      script("""
import java.io.*
import java.lang.*
import java.io.File
import java.util.Calendar.* 

@NonCPS
def getCommits()
{
ProcessBuilder p = new ProcessBuilder("git","log","--pretty=%H,%cd","--date=iso","--max-count=999999");
File directory=new File("\${WORKSPACE}")
p.directory(directory);

def out = new StringBuffer()

Process pro = p.start()

pro.consumeProcessOutput(out,out)
pro.waitFor()

return out
}

node {
    
	stage('clone')
	{
		git url: 'https://github.com/notepad-plus-plus/notepad-plus-plus.git'    
	}   
    stage('Iterate')
	{
		def numOfDays="\${Increment}".toInteger()
		def start="\${Start}".toInteger()
		def output=getCommits()
		def today=new Date();
		def currentDate=today - start
		print "Starting at date:"+currentDate

		def commits=output.readLines()
		commits.reverse().each {

			def (commit,dateString)= it.tokenize(',')
			def date = new Date().parse("yyyy-MM-dd HH:mm:ss Z",dateString)
 
			if (date>(currentDate+numOfDays))
			{
				currentDate=date
				def printDate=date.format( 'YMMdd' ) 
				print "Commit:"+commit+" Date:"+printDate
				build job: 'NotePadPlusPlus', propagate: false,parameters: [string(name: 'Commit', value: commit), string(name: 'Backdate', value: printDate)]
			}
		}		
	}	  
}
      """.stripIndent())      
    }
  }
}
listView('Trend Build') {
    description('All jobs that build trend date')
    jobs {
        name('NotepadPlusPlus_tb')
        
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