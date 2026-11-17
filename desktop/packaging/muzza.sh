#!/bin/sh
# Muzza for Linux: runs the bundled Java runtime with the app.
HERE="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
exec "$HERE/runtime/bin/java" -Xmx1g -Dmuzza.home="$HERE" -Dfile.encoding=UTF-8 -jar "$HERE/app/muzza.jar" "$@"
