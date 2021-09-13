#!groovy

def info(message) {
  echo "INFO: ${message}"
}

def warn(message) {
  echo "WARN: ${message}"
}

def failure(message) {
  error("${message}")
}
