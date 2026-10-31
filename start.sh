#!/usr/bin/env sh
# macOS / Linux launcher: ./start.sh (play) or ./start.sh build
cd "$(dirname "$0")" || exit 1
if [ "$1" = "build" ]; then
	./gradlew build && echo "Mod jar: build/libs/" && ls build/libs
else
	./gradlew runClient
fi
