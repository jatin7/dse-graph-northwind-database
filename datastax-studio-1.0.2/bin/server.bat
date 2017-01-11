@echo off

setlocal ENABLEDELAYEDEXPANSION

cd %~dp0..\

rem Find the jar and the deps
for %%i in (. target) do (
    for %%f in (%%i\studio-server*.jar) do (
	set SERVER_JAR=%%~ff
    )
    set LIB_DIRECTORY=%%i\lib
    if exist !SERVER_JAR! goto foundServerJar
)

:foundServerJar

for %%i in (!LIB_DIRECTORY!) do (
    for %%f in (%%i\studio-api*.war) do (
	set API_WAR=%%~ff
    )
    for %%f in (%%i\*ide*gremlin*.war) do (
	set GREMLIN_IDE_WAR=%%~ff
    )
)

set SERVER_CLASSPATH=!SERVER_JAR!
for %%i in (!LIB_DIRECTORY!\*jar) do set SERVER_CLASSPATH=!SERVER_CLASSPATH!;%%~fi


rem Find the ui dir
for %%i in (ui target\studio-ui) do (
    set UI_DIR=%%~fi
    if exist !UI_DIR! goto foundUiDir
)


:foundUiDir

rem See if a setenv.bat file exists, if so source it
for %%i in (bin\setenv*.bat) do (
    call %%~fi
)

set MAIN_CLASS=com.datastax.studio.server.Bootstrap

java %STUDIO_JVM_ARGS% -cp !SERVER_CLASSPATH! !MAIN_CLASS! !UI_DIR! !API_WAR! !DEVC_WAR! !GREMLIN_IDE_WAR!
