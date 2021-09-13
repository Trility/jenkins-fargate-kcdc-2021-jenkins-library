#!groovy

// It is assumed these methods are performed inside a node() {} block
def validateCliParamters(Map parameters, String functionName) {
  if (!parameters.containsKey("cliCommand")) {
    log.failure "${functionName} requires a cliCommand"
  }

  if (!parameters.containsKey("region")) {
    parameters['region'] = ""
  }

  if (!parameters.containsKey("role")) {
    parameters['role'] = ""
  }

  if (!parameters.containsKey("hideCommand")) {
    parameters['hideCommand'] = false
  }

  if (parameters['role'] != "" && parameters['region'] == "") {
    log.failure "awsCliCommand requires a region to be set if using a specific role"
  }

  return parameters
}

def getAssumeRoleCode(String region, String role) {
  assumeRoleResult = sh script: "aws --region ${region} sts assume-role --role-arn ${role} --role-session-name jenkinsSession --query 'Credentials' --output text", returnStdout: true
  tokens = assumeRoleResult.tokenize('\t')
  def assumeRoleCode = """
    set +x
    export AWS_ACCESS_KEY_ID=${tokens[0]}
    export AWS_SECRET_ACCESS_KEY=${tokens[2]}
    export AWS_SESSION_TOKEN=${tokens[3]}
    set -x
  """
  return assumeRoleCode
}

def awsCliCommand(Map parameters) {
  parameters = validateCliParamters(parameters, "awsCliCommand")

  def outputFileUUID = UUID.randomUUID().toString()

  def hideCommandStart = ""
  def hideCommandEnd = ""
  if (parameters['hideCommand']) {
    hideCommandStart = "set +x"
    wrapCommandEnd = "set -x"
  }

  def assumeRoleCode = ""
  if (parameters['region'] != "" && parameters['role'] != "") {
    assumeRoleCode = getAssumeRoleCode(parameters['region'], parameters['role'])
  }
  sh """
    ${assumeRoleCode}
    ${hideCommandStart}
    ${parameters['cliCommand']}
    ${hideCommandEnd}
    echo \$? > cli_exit_${outputFileUUID}.txt
  """

  def exitCode = readFile "cli_exit_${outputFileUUID}.txt"
  def cleanupExitCode = sh script: "rm -f cli_exit_${outputFileUUID}.txt", returnStatus: true
  if (cleanupExitCode != 0) {
    log.failure "Failed to clean up"
  }

  if (exitCode.trim() != "0") {
    log.failure "Failed to run the command"
  }
}

def awsCliGetResult(Map parameters) {
  parameters = validateCliParamters(parameters, "awsCliGetResult")

  def outputFileUUID = UUID.randomUUID().toString()

  def hideCommandStart = ""
  def hideCommandEnd = ""
  if (parameters['hideCommand']) {
    hideCommandStart = "set +x"
    hideCommandEnd = "set -x"
  }

  def assumeRoleCode = ""
  if (parameters['region'] != "" && parameters['role'] != "") {
    assumeRoleCode = getAssumeRoleCode(parameters['region'], parameters['role'])
  }
  sh """
    ${assumeRoleCode}
    ${hideCommandStart}
    ${parameters['cliCommand']} --output text > cli_result_${outputFileUUID}.txt
    ${hideCommandEnd}
    echo \$? > cli_exit_${outputFileUUID}.txt
  """
  def retval = readFile "cli_result_${outputFileUUID}.txt"
  def exitCode = readFile "cli_exit_${outputFileUUID}.txt"
  def cleanupExitCode = sh script: "rm -f cli_result_${outputFileUUID}.txt cli_exit_code_${outputFileUUID}.txt", returnStatus: true
  if (cleanupExitCode != 0) {
    log.failure "Failed to clean up"
  }

  if (exitCode.trim() != "0") {
    log.failure "Failed to get the result"
  }

  return retval.trim()
}
