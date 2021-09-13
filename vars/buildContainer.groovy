#!groovy

def run(String containerName, String directory) {
  node ("docker") {
    try {
      wrap([$class: 'AnsiColorBuildWrapper']) {
        timestamps {
          def scmVars = scmCheckout()
          def tags = ["latest"]

          def aws_account_id = aws.awsCliGetResult(
            cliCommand: "aws sts get-caller-identity --query 'Account' --output text"
          )

          dir(directory) {
            sh """
              export DOCKER_CONFIG=/kaniko/.docker
              export PATH=/kaniko:\$PATH

              /kaniko/executor \
                --context \$(pwd) \
                --dockerfile Dockerfile \
                --destination ${aws_account_id}.dkr.ecr.${env.AWS_REGION}.amazonaws.com/${containerName}:latest \
                --force
            """
          }

          currentBuild.result = "SUCCESS"
        }
      }
    } catch (e) {
      currentBuild.result = "FAILED"
      throw e
    }
  }
}
