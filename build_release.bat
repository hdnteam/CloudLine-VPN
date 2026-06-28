@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Building Release APK...
call gradlew.bat assembleRelease
echo.
echo Exit code: %ERRORLEVEL%
