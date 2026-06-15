@REM Maven Wrapper startup script for Windows
@echo off
setlocal

set MAVEN_PROJECTBASEDIR=%~dp0
set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"

if not exist %WRAPPER_JAR% (
  echo Downloading Maven Wrapper...
  powershell -NoProfile -Command ^
    "$ProgressPreference='SilentlyContinue';" ^
    "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar' -OutFile '%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar'"
)

set JAVA_HOME=
for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set JAVA_HOME=%%i
if not defined JAVA_HOME for /d %%i in ("C:\Program Files\Java\jdk-21*") do set JAVA_HOME=%%i
if not defined JAVA_HOME for /d %%i in ("C:\Program Files\Microsoft\jdk-21*") do set JAVA_HOME=%%i

java -jar %WRAPPER_JAR% %*
