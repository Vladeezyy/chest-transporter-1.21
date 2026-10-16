#!/bin/sh
# Functional self-test (src/selftest/.../SelfTest.java) on a fresh dev server world: waits for the result line
# (max 10 min), stops the server, prints the result and every error/warning that is not known vanilla noise.
cd "$(dirname "$0")/.."
pkill -f 'fml.modFolders=chest_transporter.*server' 2>/dev/null
rm -rf run/world build-selftest.log
mkdir -p run && echo "eula=true" > run/eula.txt
./gradlew runSelftest --console=plain "$@" > build-selftest.log 2>&1 &
i=0
until grep -qE 'Chest Transporter selftest\] (PASS|FAILED)|Minecraft Crash Report|Failed to start|BUILD FAILED|FAILED TO BIND' build-selftest.log 2>/dev/null || [ $i -gt 300 ]; do sleep 2; i=$((i+1)); done
sleep 5
pkill -f 'fml.modFolders=chest_transporter' 2>/dev/null; sleep 2; pkill -9 -f 'fml.modFolders=chest_transporter' 2>/dev/null
wait 2>/dev/null
[ $i -gt 300 ] && echo "TIMEOUT (no result in 10 min)"
grep -E 'Chest Transporter selftest\] (PASS|FAILED|FAIL )|Minecraft Crash Report|Failed to start|BUILD FAILED' build-selftest.log | sed -E 's/^\[[^]]*\] //'
echo "--- errors / warnings (vanilla dev noise filtered)"
grep -E '/(ERROR|FATAL|WARN)\]|Caused by|Exception' build-selftest.log \
  | grep -vE 'Chest Transporter selftest|server.properties|uses unexpected schema|OFFLINE/INSECURE|online-mode|hackers to connect|no attempt to authenticate|LanServerPinger|UnixException|Ambiguity between arguments' \
  | sed -E 's/^\[[^]]*\] //' | cut -c1-300 | sort | uniq -c | sort -rn | head -40

# result: 0 if all checks passed
grep -q "Chest Transporter selftest\] PASS" build-selftest.log
