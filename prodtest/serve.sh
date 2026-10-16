#!/bin/sh
# Runs the NeoForge server in the current dir until "Done" or a crash (max 10 min), then stops it. Output: server.out
rm -f server.out
( i=0; until grep -qE 'Done \(|Crash report|Failed to start|Exception in server tick' server.out 2>/dev/null || [ $i -gt 300 ]; do sleep 2; i=$((i+1)); done; sleep 3; echo stop; sleep 30 ) | sh run.sh nogui > server.out 2>&1
grep -E 'Done \(|Crash report|Failed to start' server.out | head -3
grep -E '/(ERROR|FATAL)\]' server.out | sed -E 's/^\[[^]]*\] //' | cut -c1-250 | sort | uniq -c | sort -rn | head -20
