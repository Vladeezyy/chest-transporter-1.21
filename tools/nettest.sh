#!/bin/sh
# Multiplayer check (max 15 min): starts the dev server ($LOADER/run-net/, the dev player "Dev" is an operator), waits for it,
# runs the client scene selftest/NetScene that joins it, then stops the server and prints the checks and errors of
# both. Extra gradle arguments are passed on to both (-Pneo_version=21.1.197 -Pno_jei).
cd "$(dirname "$0")/.."
# LOADER=neoforge (default) or LOADER=fabric
LOADER=${LOADER:-neoforge}
# processes of the dev runs: NeoForge passes the mod folders, Fabric's dev launcher the loom cache of the project
case $LOADER in
	neoforge) PROC='fml.modFolders=chest_transporter' ;;
	fabric) PROC='chest-transporter-1.21/fabric/.gradle/loom-cache' ;;
	*) echo "LOADER must be neoforge or fabric"; exit 2 ;;
esac
pkill -f "$PROC" 2>/dev/null
rm -rf $LOADER/run-net/world build-netserver.log build-netclient.log
rm -f $LOADER/run/config/chest_transporter-*.toml
mkdir -p $LOADER/run-net/config && echo "eula=true" > $LOADER/run-net/eula.txt
# only diamond trucks take spawners on this server, the scene checks the refusal
printf '[pickup]\n\tspawnerTiers = ["diamond"]\n' > $LOADER/run-net/config/chest_transporter-server.toml
printf 'online-mode=false\nspawn-protection=0\ndifficulty=peaceful\n' > $LOADER/run-net/server.properties
# The offline UUID of "Dev"
uuid=$(python3 -c 'import hashlib,uuid;h=bytearray(hashlib.md5(b"OfflinePlayer:Dev").digest());h[6]=h[6]&0x0f|0x30;h[8]=h[8]&0x3f|0x80;print(uuid.UUID(bytes=bytes(h)))')
echo "[{\"uuid\":\"$uuid\",\"name\":\"Dev\",\"level\":4,\"bypassesPlayerLimit\":false}]" > $LOADER/run-net/ops.json
# the client scenes may have left another language in the options
[ -f $LOADER/run/options.txt ] && sed -i '' 's/^lang:.*/lang:en_us/' $LOADER/run/options.txt
./gradlew :$LOADER:runNetServer --console=plain "$@" > build-netserver.log 2>&1 &
i=0
until grep -qE 'Done \(|Minecraft Crash Report|crash report has been saved|BUILD FAILED' build-netserver.log 2>/dev/null || [ $i -gt 300 ]; do sleep 2; i=$((i+1)); done
perl -e 'alarm 600; exec @ARGV' ./gradlew :$LOADER:runNetClient --console=plain "$@" > build-netclient.log 2>&1
pkill -f "$PROC" 2>/dev/null; sleep 3; pkill -9 -f "$PROC" 2>/dev/null
wait 2>/dev/null
grep -E '\[scene\] (CHECK|DONE)' build-netclient.log | sed -E 's/^.*\[scene\] //'
echo "--- server errors"
grep -E '/(ERROR|FATAL)\]|Caused by|Exception|lost connection|kicked' build-netserver.log | sed -E 's/^\[[^]]*\] //' | cut -c1-300 | sort | uniq -c | sort -rn | head -20
echo "--- client errors"
grep -E '/(ERROR|FATAL)\]|Caused by|Exception|Disconnected' build-netclient.log | sed -E 's/^\[[^]]*\] //' | cut -c1-300 | sort | uniq -c | sort -rn | head -20
