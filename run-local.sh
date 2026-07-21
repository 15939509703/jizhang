#!/bin/zsh

set -a
source "${0:A:h}/.env.local"
set +a

export JAVA_HOME=/Users/lhj/Library/Java/JavaVirtualMachines/ms-21.0.7/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

exec java -jar "${0:A:h}/jizhang-bootstrap/target/jizhang-bootstrap-1.0.0-SNAPSHOT.jar"
