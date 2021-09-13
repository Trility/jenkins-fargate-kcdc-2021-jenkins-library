#!groovy

def genVarString(Map vars = [:]) {
  def varString = ""

  vars.each { name, value ->
    varString += " -var='${name}=${value}'"
  }

  return varString
}

def init() {
  def exitCode = sh script: "terraform init", returnStatus: true
  if (exitCode != 0) {
    log.failure "'terrform init' fail"
  }
}

def plan(String vars) {
  def exitCode = sh script: "terraform plan ${vars} -detailed-exitcode -out=plan.out", returnStatus: true
  return exitCode
}

def apply() {
  if (env.PLAN_ONLY == "false") {
    def exitCode
    exitCode = sh script: "terraform apply -auto-approve plan.out", returnStatus: true
    return exitCode
  } else {
    log.info "PLAN_ONLY selected, skip apply"
    return 0
  }
}

def validateParamters(Map parameters) {
  if (!parameters.containsKey("directory")) {
    log.failure "terraform.run() requires a directory."
  }

  if (!parameters.containsKey("vars")) {
    parameters["vars"] = [:]
  }

  return parameters
}

def validatePlanOnly() {
  if (env.PLAN_ONLY == null) {
    log.info "PLAN_ONLY not supplied, default is 'true'"
    env.PLAN_ONLY = "true"
  }
  log.info "PLAN_ONLY is set to ${env.PLAN_ONLY}"
}

def run(Map parameters) {
  parameters = validateParamters(parameters)
  validatePlanOnly()
  def varString = genVarString(parameters["vars"])

  try {
    node("eng") {
      step([$class: 'WsCleanup'])
      wrap([$class: 'AnsiColorBuildWrapper']) {
        timestamps {
          scmVars = scmCheckout()

          dir(parameters["directory"]) {
            init()

            def totalAttempts = 3
            def sleepTime = 5
            def attempts = 1
            def applyStatus = 0
            def planStatus = 0
            while(attempts<totalAttempts) {
              planStatus = 0
              applyStatus = 0

              planStatus = plan(varString)

              if (planStatus == 0) {
                log.info "No changes detected. Exiting"
                break
              } else if (planStatus == 2) {
                applyStatus = apply()
                if (applyStatus == 0) {
                  log.info "terraform apply successful"
                  break
                } else {
                  attempts++
                  sleepBackoff = sleepTime * attempts
                  if (attempts < totalAttempts) {
                    log.warn "terraform apply failed, retrying in ${sleepBackoff} seconds"
                    sh script: "sleep ${sleepBackoff}"
                  }
                }
              } else {
                attempts++
                sleepBackoff = sleepTime * attempts
                if (attempts < totalAttempts) {
                  log.warn "terraform plan failed to run, retrying in ${sleepBackoff} seconds"
                  sh script: "sleep ${sleepBackoff}"
                }
              }
            }

            if (planStatus != 0 && planStatus != 2) {
              log.failure "terraform plan failed after ${attempts} attempts"
            }

            if (applyStatus != 0) {
              log.failure "terraform apply failed after ${attempts} attempts"
            }
          }

          def plan_only = ""
          if (env.PLAN_ONLY == "true") {
            plan_only = ", PLAN_ONLY"
          }

          currentBuild.result = "SUCCESS"
        }
      }
    }
  } catch (e) {
    currentBuild.result = "FAILED"
    throw e
  }
}
