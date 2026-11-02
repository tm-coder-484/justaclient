#!/usr/bin/env sh
# macOS / Linux launcher: ./start.sh [1.21.11|26.3] [build]
#   ./start.sh            play 1.21.11
#   ./start.sh 26.3       play 26.3 (needs Java 25)
#   ./start.sh 26.3 build make the 26.3 mod jar
cd "$(dirname "$0")" || exit 1
MC=1.21.11
if [ "$1" = "26.3" ] || [ "$1" = "1.21.11" ]; then
	MC=$1
	shift
fi
if [ "$MC" = "26.3" ]; then
	NEED=25
	PROJECT="-p mc26.3"
	LIBS=mc26.3/build/libs
else
	NEED=21
	PROJECT=""
	LIBS=build/libs
fi
JV=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
JMAJ=${JV%%.*}
if [ -z "$JMAJ" ] || [ "$JMAJ" -lt "$NEED" ] 2>/dev/null; then
	echo "Minecraft $MC needs Java $NEED or newer (found: ${JV:-none})."
	echo "Install it from https://adoptium.net/temurin/releases/?version=$NEED"
	exit 1
fi
if [ "$1" = "build" ]; then
	# shellcheck disable=SC2086
	./gradlew $PROJECT build && echo "Mod jar: $LIBS/" && ls "$LIBS"
else
	# shellcheck disable=SC2086
	./gradlew $PROJECT runClient
fi
