@REM
@REM Gradle wrapper script for Windows
@REM

@if "%DEBUG%" == "" @echo off
@REM Set default for JAVA_HOME if not already set.
@if "%JAVA_HOME%" == "" (
    @set JAVA_HOME=C:\Program Files\Java\jdk-11
)

@REM Set default for GRADLE_HOME if not already set.
@if "%GRADLE_HOME%" == "" (
    @set GRADLE_HOME=%~dp0\gradle\wrapper
)

@REM Set default for GRADLE_OPTS if not already set.
@if "%GRADLE_OPTS%" == "" (
    @set GRADLE_OPTS=-Xmx64m -Xms64m
)

@REM Set default for JAVA_OPTS if not already set.
@if "%JAVA_OPTS%" == "" (
    @set JAVA_OPTS=
)

@REM Set default for CLASSPATH if not already set.
@if "%CLASSPATH%" == "" (
    @set CLASSPATH=%GRADLE_HOME%\gradle-wrapper.jar
)

@REM Execute Gradle.
"%JAVA_HOME%\bin\java" %JAVA_OPTS% %GRADLE_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
