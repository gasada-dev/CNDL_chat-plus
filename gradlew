#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P) || exit 1

if [ -n "$JAVA_HOME" ]; then
	JAVA_COMMAND="$JAVA_HOME/bin/java"
else
	JAVA_COMMAND=java
fi

exec "$JAVA_COMMAND" \
	-classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
	org.gradle.wrapper.GradleWrapperMain "$@"
