#!groovy

def auth(String aws_account_id, String region="us-west-2") {
  stage("ECR Authentication for ${region}") {
    sh """
      aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${aws_account_id}.dkr.ecr.${region}.amazonaws.com
    """
  }
}
