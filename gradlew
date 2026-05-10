#!/bin/sh
# Gradle wrapper script for Unix

# Resolve the Gradle home directory
APP_HOME=$(cd "$(dirname "$0")" && pwd)

# Use JAVA_HOME if set
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

exec "$JAVACMD" \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
