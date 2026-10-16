#!/bin/sh
# Multiplayer check (max 15 min): starts the dev server (run-net/, the dev player "Dev" is an operator), waits for it,
# runs the client scene selftest/NetScene that joins it, then stops the server and prints the checks and errors of
# both. Extra gradle arguments are passed on to both (-Pneo_version=21.1.197 -Pno_jei).
cd "$(dirname "$0")/.."
pkill -f 'fml.modFolders=chest_transporter' 2>/dev/null
rm -rf run-net/world build-netserver.log build-netclient.log
rm -f run-net/config/chest_transporter-*.toml run/config/chest_transporter-*.toml
mkdir -p run-net/defaultconfigs && echo "eula=true" > run-net/eula.txt
# only diamond trucks take spawners on this server, the scene checks the refusal
printf '[pickup]\n\tspawnerTiers = ["diamond"]\n' > run-net/defaultconfigs/chest_transporter-server.toml
printf 'online-mode=false\nspawn-protection=0\ndifficulty=peaceful\n' > run-net/server.properties
# The offline UUID of "Dev"
uuid=$(python3 -c 'import hashlib,uuid;h=bytearray(hashlib.md5(b"OfflinePlayer:Dev").digest());h[6]=h[6]&0x0f|0x30;h[8]=h[8]&0x3f|0x80;print(uuid.UUID(bytes=bytes(h)))')
echo "[{\"uuid\":\"$uuid\",\"name\":\"Dev\",\"level\":4,\"bypassesPlayerLimit\":false}]" > run-net/ops.json
# the client scenes may have left another language in the options
[ -f run/options.txt ] && sed -i '' 's/^lang:.*/lang:en_us/' run/options.txt
./gradlew runNetServer --console=plain "$@" > build-netserver.log 2>&1 &
i=0
until grep -qE 'Done \(|Minecraft Crash Report|BUILD FAILED' build-netserver.log 2>/dev/null || [ $i -gt 300 ]; do sleep 2; i=$((i+1)); done
perl -e 'alarm 600; exec @ARGV' ./gradlew runNetClient --console=plain "$@" > build-netclient.log 2>&1
pkill -f 'fml.modFolders=chest_transporter' 2>/dev/null; sleep 3; pkill -9 -f 'fml.modFolders=chest_transporter' 2>/dev/null
wait 2>/dev/null
grep -E '\[scene\] (CHECK|DONE)' build-netclient.log | sed -E 's/^.*\[scene\] //'
echo "--- server errors"
grep -E '/(ERROR|FATAL)\]|Caused by|Exception|lost connection|kicked' build-netserver.log | sed -E 's/^\[[^]]*\] //' | cut -c1-300 | sort | uniq -c | sort -rn | head -20
echo "--- client errors"
grep -E '/(ERROR|FATAL)\]|Caused by|Exception|Disconnected' build-netclient.log | sed -E 's/^\[[^]]*\] //' | cut -c1-300 | sort | uniq -c | sort -rn | head -20
