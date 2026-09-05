#!/usr/bin/env bash
#
# Stands the whole demo up: the server, then two game windows on this machine.
#
#   tools/present.sh          server + two windows
#   tools/present.sh 3        server + three windows
#   tools/present.sh stop     shut everything down
#
# The windows are started only once the server is actually accepting
# connections. A client that starts first falls back to offline play and stays
# that way for its whole run, which during a demo looks like the versus screen
# being broken.
set -u

cd "$(dirname "$0")/.." || exit 1

PORT=5252
GAME=build/install/plants-vs-zombies/bin/plants-vs-zombies
LOGS=build/present
WINDOWS=${1:-2}

stop_everything() {
    pkill -f 'ir\.sharif\.pvz\.Main' 2>/dev/null
    sleep 1
    if lsof -nP -iTCP:$PORT -sTCP:LISTEN >/dev/null 2>&1; then
        echo "something is still holding port $PORT"
        exit 1
    fi
    echo "server and windows are closed."
    exit 0
}

[ "$WINDOWS" = "stop" ] && stop_everything

if lsof -nP -iTCP:$PORT -sTCP:LISTEN >/dev/null 2>&1; then
    echo "port $PORT is already in use — run 'tools/present.sh stop' first."
    exit 1
fi

echo "building..."
./gradlew installDist --quiet || exit 1
mkdir -p "$LOGS"

echo "starting the server..."
"$GAME" --server "$PORT" > "$LOGS/server.log" 2>&1 &

# wait for it to be listening rather than guessing at a sleep
for _ in $(seq 1 40); do
    lsof -nP -iTCP:$PORT -sTCP:LISTEN >/dev/null 2>&1 && break
    sleep 0.5
done
if ! lsof -nP -iTCP:$PORT -sTCP:LISTEN >/dev/null 2>&1; then
    echo "the server did not come up; see $LOGS/server.log"
    exit 1
fi
echo "  listening on port $PORT"

for window in $(seq 1 "$WINDOWS"); do
    echo "opening window $window..."
    "$GAME" > "$LOGS/window$window.log" 2>&1 &
    sleep 3
done

# a client that failed to reach the server says so on its first line
sleep 6
offline=$(grep -l "starting offline" "$LOGS"/window*.log 2>/dev/null | wc -l | tr -d ' ')
if [ "$offline" != "0" ]; then
    echo "WARNING: $offline window(s) started offline — versus will not work in them."
else
    echo
    echo "ready: server + $WINDOWS windows, all online."
    echo "sign in to each window with a different account, then Versus."
    echo "close it all with: tools/present.sh stop"
fi
