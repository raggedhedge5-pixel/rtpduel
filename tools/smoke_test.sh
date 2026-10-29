#!/usr/bin/env bash
# Boots a REAL server (Paper or Folia) of the given Minecraft version with the plugin installed,
# starts it with -Drtpduels.selftest=true (a built-in check that only runs then) and verifies it passes.
#
#   tools/smoke_test.sh <paper|folia> <minecraft-version> <path-to-RTPDuels.jar>
#
# Needs: curl, jq, java (the right version for that Minecraft version - the workflow sets it up).
# Exit codes: 0 = pass, 1 = fail, 2 = no server build exists for that version (skipped).

set -u

PROJECT="${1:?project (paper|folia)}"
VERSION="${2:?minecraft version}"
JAR="${3:?path to plugin jar}"
UA="RTPDuels-CI/1.0 (https://github.com/${GITHUB_REPOSITORY:-example/rtp-duels})"

WORK="$(mktemp -d)"
cd "$WORK" || exit 1
mkdir -p plugins
cp "$JAR" plugins/RTPDuels.jar

echo "== Looking up $PROJECT $VERSION on the PaperMC downloads API"
BUILDS="$(curl -fsSL -H "User-Agent: $UA" "https://fill.papermc.io/v3/projects/$PROJECT/versions/$VERSION/builds" || true)"
URL="$(echo "$BUILDS" | jq -r 'if type=="array" then ((map(select(.channel=="STABLE"))[0] // .[0]) | .downloads."server:default".url) else empty end' 2>/dev/null || true)"
if [ -z "$URL" ] || [ "$URL" = "null" ]; then
  echo "No $PROJECT build found for $VERSION - skipping."
  exit 2
fi
echo "== Downloading $URL"
curl -fsSL -H "User-Agent: $UA" -o server.jar "$URL" || { echo "download failed"; exit 1; }

echo "eula=true" > eula.txt
cat > server.properties <<'PROPS'
online-mode=false
view-distance=3
simulation-distance=3
spawn-protection=0
max-players=2
motd=rtpduels-smoke-test
PROPS

mkfifo console.in
# keep the fifo open for writing so the server never sees EOF
exec 3<>console.in
java -Xmx1G -Drtpduels.selftest=true -jar server.jar nogui <console.in > server.log 2>&1 &
SERVER_PID=$!

wait_for() { # pattern timeoutSeconds
  local pattern="$1" timeout="$2" waited=0
  while [ "$waited" -lt "$timeout" ]; do
    if grep -q -E "$pattern" server.log 2>/dev/null; then return 0; fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then return 2; fi
    sleep 2; waited=$((waited + 2))
  done
  return 1
}

RESULT=1
if wait_for 'Done \(' 420; then
  echo "== Server started"
  if wait_for 'SELFTEST RESULT' 120; then
    if grep -q 'SELFTEST RESULT: PASS' server.log \
       && grep -q 'RTP Duels enabled' server.log \
       && ! grep -q -E 'Error occurred while enabling RTPDuels|Could not load .plugins/RTPDuels' server.log; then
      RESULT=0
    fi
  else
    echo "!! selftest never reported a result"
  fi
else
  echo "!! server did not finish starting"
fi

echo "stop" >&3
for _ in $(seq 1 30); do kill -0 "$SERVER_PID" 2>/dev/null || break; sleep 2; done
kill "$SERVER_PID" 2>/dev/null || true

echo "== Relevant log lines"
grep -E 'RTPDuels|RTP Duels|SELFTEST|ERROR|Exception|Done \(' server.log | head -80

if [ "$RESULT" -ne 0 ]; then
  echo "== Last 60 log lines"
  tail -n 60 server.log
fi
exit "$RESULT"
