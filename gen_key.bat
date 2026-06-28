@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
"%JAVA_HOME%\bin\keytool.exe" -genkey -v -keystore cloudline-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias cloudline -storepass cloudline2024 -keypass cloudline2024 -dname "CN=CloudLine VPN, OU=HDNTEAM, O=HDNTEAM, L=Tehran, ST=Tehran, C=IR"
