#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/tests
case "$(javac -version 2>&1)" in
  "javac 1.8."*) javac -encoding UTF-8 -source 8 -target 8 -cp projet.jar -d build/tests projet/test/voyage/TestsPont.java ;;
  *) javac -encoding UTF-8 --release 8 -Xlint:-options -cp projet.jar -d build/tests projet/test/voyage/TestsPont.java ;;
esac
java -cp 'projet.jar:build/tests' voyage.TestsPont "$@"
