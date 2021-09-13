#!groovy

def call() {
  def scmVars

  stage("Checkout") {
    scmVars = checkout scm
  }

  return scmVars
}
