#!/bin/bash
# Wrapper script to run Maven with Java 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw "$@"
