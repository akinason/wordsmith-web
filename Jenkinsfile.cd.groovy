pipeline {
    agent any 
    environment {
        IMAGE_REPO = "235364608836.dkr.ecr.eu-central-1.amazonaws.com/wordsmith-web"
        AWS_REGION = "eu-central-1"
        SNS_TOPIC_ARN = "arn:aws:sns:eu-central-1:235364608836:jenkins-notification"
        ECS_CLUSTER_ARN = "arn:aws:ecs:eu-central-1:235364608836:cluster/WordsmithWebCluster"
        SERVICE_NAME = "wordsmith-web-service"
        TASK_DEFINITION_FAMILY = "WordsmithWeb"
    }

    parameters{
        string(name: "IMAGE_TAG", description: "The  image tag to be deployed")
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

                        def taskDef = readJSON text: taskDefJson

                        // Update image tag in the container defintion
                        taskDef.containerDefinitions.each { containerDef ->
                            containerDef.image = "${env.IMAGE_REPO}:${params.IMAGE_TAG}"
                        }

                        // Remove unwanted fields from the task Definition
                        // NB: These fields will be created by AWS when we create the new task def.
                        taskDef.remove("taskDefinitionArn")
                        taskDef.remove("revision")
                        taskDef.remove("status")
                        taskDef.remove("requiredAttributes")
                        taskDef.remove("compatibilities")
                        taskDef.remove('registeredAt')
                        taskDef.remove("registeredBy")

                        newTaskDefJson = writeJSON returnText: true, json: taskDef 
                        def newTaskDefArn = sh(
                            script: """
                                aws ecs register-task-definition \\
                                --cli-input-json '${newTaskDefJson}'
                            """,
                            returnStdout: true
                        ).trim()

                        def newRevision = newTaskDefArn.split(":")[-1].trim()

                        sh """
                            aws ecs update-service \\
                            --cluster ${env.ECS_CLUSTER} \\
                            --service ${env.SERVICE_NAME} \\
                            --task-definition ${TASK_DEFINITION_FAMILY}:${newRevision}
                        """
                    }
                }
            }
        }
    }

}