#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/classes
case "$(javac -version 2>&1)" in
  "javac 1.8."*) set -- -source 8 -target 8 ;;
  *) set -- --release 8 -Xlint:-options ;;
esac
javac -encoding UTF-8 "$@" -d build/classes projet/src/voyage/*.java
jar cfe projet.jar voyage.Main -C build/classes .
echo 'JAR cree : projet.jar'
