pipeline {
    agent any 
    environment {
        IMAGE_REPO = "235364608836.dkr.ecr.eu-central-1.amazonaws.com/wordsmith-web"
        AWS_REGION = "eu-central-1"
        SNS_TOPIC_ARN = "arn:aws:sns:eu-central-1:235364608836:jenkins-notification"
        ECS_CLUSTER = "WordsmithWebCluster"
        SERVICE_NAME = "wordsmith-web-service"
        TASK_DEFINITION_FAMILY = "WordsmithWeb"
    }

    parameters {
        string(name: "IMAGE_TAG", description: "The image tag to be deployed", defaultValue: "1.1.0.2")
    }

    stages {
        stage("Deploy to ECS") {
            steps {
                script {
                    withAWS([credentials: 'aws-creds', region: "${env.AWS_REGION}"]) {
                        // Extract existing task definition
                        def taskDefJson = sh(
                            script: """
                                aws ecs describe-task-definition \\
                                --task-definition ${env.TASK_DEFINITION_FAMILY} \\
                                --query "taskDefinition"
                            """,
                            returnStdout: true
                        ).trim()
                        
                        // Parse the JSON
                        def taskDef = readJSON(text: taskDefJson)
                    
                        // Update the image tag
                        taskDef.containerDefinitions.each { containerDef ->
                            containerDef.image = "${env.IMAGE_REPO}:${params.IMAGE_TAG}".toString()
                        }
                       
                        // Remove unwanted fields
                        taskDef.remove("taskDefinitionArn")
                        taskDef.remove("revision")
                        taskDef.remove("status")
                        taskDef.remove("requiresAttributes")
                        taskDef.remove("compatibilities")
                        taskDef.remove("registeredAt")
                        taskDef.remove("registeredBy")
                        
                        
                        
                        // Save updated task definition to a file
                        writeFile file: 'newTaskDef.json', text: writeJSON(returnText: true, json: taskDef, pretty: 4)
                       
                        // Register new task definition
                        def registerOutput = sh(
                            script: "aws ecs register-task-definition --cli-input-json file://newTaskDef.json",
                            returnStdout: true
                        ).trim()
                        
                      
                        def taskDefOutput = readJSON(text: registerOutput)
                        def newTaskDefArn = taskDefOutput.taskDefinition.taskDefinitionArn
                        
                        sh  "echo ${newTaskDefArn}"

                        // Force a new deployment with the new task definition
                        sh """
                            aws ecs update-service \\
                            --cluster ${env.ECS_CLUSTER} \\
                            --service ${env.SERVICE_NAME} \\
                            --task-definition ${newTaskDefArn} \\
                            --force-new-deployment
                        """
                    }
                }
            }
        }
    }
    
    post {
        always {
            cleanWs()
        }
    }
}
