@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo JAVA_HOME=%JAVA_HOME%
java -version
echo.
echo Starting Gradle build...
call gradlew.bat assembleDebug
echo.
echo Exit code: %ERRORLEVEL%
