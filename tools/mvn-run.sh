#!/usr/bin/env bash
# 绕过本机 MAVEN_HOME 反斜杠导致的 launcher 加载失败，直接以 Java 调用 Maven classworlds。
unset MAVEN_HOME M2_HOME
export JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.13.11-hotspot"
MVN_HOME="D:/soft-install/apache-maven-3.9.14"
exec "$JAVA_HOME/bin/java" \
  -classpath "$MVN_HOME/boot/plexus-classworlds-2.9.0.jar" \
  "-Dclassworlds.conf=$MVN_HOME/bin/m2.conf" \
  "-Dmaven.home=$MVN_HOME" \
  "-Dmaven.multiModuleProjectDirectory=$(pwd)" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
