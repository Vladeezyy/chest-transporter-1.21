#!/bin/sh
# The Fabric release jar on a production (intermediary-mapped) Fabric server with Fabric API (Loom's prodServer task,
# fabric/run-prod/): waits for "Done" or a crash (max 5 min), stops it, prints the result and the errors.
cd "$(dirname "$0")/.."
rm -f build-prodfabric.log
./gradlew :fabric:prodServer --console=plain > build-prodfabric.log 2>&1 &
i=0
until grep -qE 'Done \(|crash report has been saved|Minecraft Crash Report|Failed to start|BUILD FAILED|Incompatible mods' build-prodfabric.log 2>/dev/null || [ $i -gt 150 ]; do sleep 2; i=$((i+1)); done
sleep 3
pkill -f 'fabric.addMods=.*chest-transporter-1.21' 2>/dev/null; sleep 3; pkill -9 -f 'fabric.addMods=.*chest-transporter-1.21' 2>/dev/null
wait 2>/dev/null
grep -E 'Loading [0-9]+ mods|chest_transporter|Done \(|crash report|Failed to start|BUILD FAILED|Incompatible mods' build-prodfabric.log | head -8 | cut -c1-200
echo "--- errors"
grep -E '/ERROR|/FATAL|ERROR\]|FATAL\]|Exception|Caused by' build-prodfabric.log | grep -vE 'server.properties|UnixException' | cut -c1-250 | sort | uniq -c | sort -rn | head -20
