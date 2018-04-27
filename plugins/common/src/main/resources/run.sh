#!/bin/sh

function join() {
  local IFS=$1
  shift
  echo "$*"
}

MAIN_CLASS=#MAIN_CLASS#

BIN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

ROOT_DIR=$(cd $BIN_DIR/.. && pwd)
LIB_DIR=$ROOT_DIR/lib
APP_DIR=$ROOT_DIR/app

APP_CP=$APP_DIR/*.jar

if [ ! -d $APP_DIR ]; then
  echo "APP does not exist"
  if [ -d "$ROOT_DIR/../classes" ]; then
    echo "classes exists"
    APP_DIR=$ROOT_DIR/../classes
    APP_CP=$APP_DIR
    echo "Using classes/ dir"
  fi
fi

#FAKEREPLACE=$LIB_DIR/fakereplace*.jar

#if [ -f $FAKEREPLACE ]; then
  #FAKEREPLACE=$(ls -1 $FAKEREPLACE)
  #mkdir -p $ROOT_DIR/devtools
  #mv $FAKEREPLACE $ROOT_DIR/devtools/fakereplace.jar
#fi

#if [ -f $ROOT_DIR/devtools/fakereplace.jar ]; then
  #export FAKEREPLACE=$ROOT_DIR/devtools/fakereplace.jar
  #echo "found fakereplace $FAKEREPLACE"
#fi

CLASSPATH=$(join ':' $LIB_DIR/*.jar $APP_CP)

# Setup the JVM
if [ "x$JAVA" = "x" ]; then
    if [ "x$JAVA_HOME" != "x" ]; then
        JAVA="$JAVA_HOME/bin/java"
    else
        JAVA="java"
    fi
fi

SERVER_OPTS=""

while [ "$#" -gt 0 ]
do
    case "$1" in
      -cp)
          CLASSPATH="$CLASSPATH:$2"
          shift
          ;;
      -classpath)
          CLASSPATH="$CLASSPATH:$2"
          shift
          ;;
      --)
          shift
          break;;
      *)
          SERVER_OPTS="$SERVER_OPTS $1"
          ;;
    esac
    shift
done


SERVER_OPTS="$SERVER_OPTS $JAVA_OPTS -cp $CLASSPATH"
exec $JAVA $SERVER_OPTS $MAIN_CLASS $*
