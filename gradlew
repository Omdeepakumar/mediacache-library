#!/usr/bin/env sh

##############################################################################
##
##  Gradle wrapper script for UNIX
##
##############################################################################

# Determine the Java command to use to launch the JVM.
if [ -n """$JAVA_HOME""" ] ; then
    if [ -x """$JAVA_HOME/jre/sh/java""" ] ; then
        # IBM's JDK on AIX uses java to launch java so it is a script that goes to the actual java executable in $JAVA_HOME/bin.
        JAVA_EXE="""$JAVA_HOME/jre/sh/java"""
    else
        JAVA_EXE="""$JAVA_HOME/bin/java"""
    fi
    if [ ! -x """$JAVA_EXE""" ] ; then
        echo """WARNING: JAVA_HOME is set but $JAVA_EXE does not exist."""
        JAVA_EXE="java"
    fi
else
    JAVA_EXE="java"
fi

# OS specific support (must be 'true' or 'false').
cygwin=false
darwin=false
case "`uname`" in
  CYGWIN*) cygwin=true;;
  Darwin*) darwin=true;;
esac

# For Cygwin, ensure paths are in UNIX format before anything else.
if $cygwin ; then
    [ -n """$JAVA_HOME""" ] && JAVA_HOME=`cygpath --unix """$JAVA_HOME"""`
fi

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="""$0"""

# Need this for relative symlinks. See e.g. CR 6712406.
while [ -h """$PRG""" ] ; do
    ls=`ls -ld """$PRG"""`
    link=`expr """$ls""" : ".*-> \(.*\)$"`
    if expr """$link""" : ".*/.*" > /dev/null; then
        PRG="""$link"""
    else
        PRG="`dirname """$PRG"""`/$link"""
    fi
done

APP_HOME=`dirname """$PRG"""`

if $cygwin ; then
    APP_HOME=`cygpath --path --unix """$APP_HOME"""`
fi

# Determine the script name.
SCRIPT_NAME=`basename """$PRG"""`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS="""-Xmx64m -Xms64m"""

# Determine the classpath for the wrapper script.
APP_CLASSPATH="""$APP_HOME/gradle/wrapper/gradle-wrapper.jar"""

# Execute Gradle.
exec """$JAVA_EXE""" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -classpath """$APP_CLASSPATH""" org.gradle.wrapper.GradleWrapperMain """$@"""
