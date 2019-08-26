node{
       if (env.CHANGE_ID) {
              def APPLICATION_NAME = "${env.JOB_NAME}".split('/').first()
              deploy("${APPLICATION_NAME}","${CHANGE_ID}",FALSE)
       }else{
              stage('Get deployment details') {
                     timeout(time: 30, unit: 'MINUTES') {
                            script{
                                   def PR = input message: "Please enter PR name with Build ID", ok: "Done", parameters: [
                                          string(name: 'PR WITH BUILD ID', defaultValue: 'PR-', description: 'Enter the application name')
                                   ]
                                   print PR
                                   env.PR=PR	
                                   def APPLICATION_NAME = "${env.JOB_NAME}".split('/').first()
                                   deploy("${APPLICATION_NAME}","${PR}",TRUE)
                            }
                     }
              }
              
       }
}
