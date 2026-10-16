#!/bin/sh
# Runs a client test scene (max 15 min): -Pscene=main (src/selftest/.../ClientScene, default) or -Pscene=stress.
# Screenshots in run/screenshots/scene_*.png; prints the checks and the errors of the run. Every run starts from the
# default configs and a new world. Extra gradle arguments are passed on (-Pscene_lang=ru_ru, -Pwith_compat,
# -Pneo_version=21.1.197 -Pno_jei).
cd "$(dirname "$0")/.."
rm -rf run/saves/ct_scene run/saves/ct_stress run/screenshots/scene_*.png build-scene.log
rm -f run/config/chest_transporter-*.toml
case "$*" in *scene=stress*)
	# the stress scene starts from a broken common config
	mkdir -p run/config
	printf '[modules]\n\tbuilding = "maybe"\n[building]\n\tshelf = 42\n\tbookshelf = "yes"\n' > run/config/chest_transporter-common.toml ;;
esac
perl -e 'alarm 900; exec @ARGV' ./gradlew runScene --console=plain "$@" > build-scene.log 2>&1 &
gradle_pid=$!
while kill -0 $gradle_pid 2>/dev/null; do
	if grep -q -- '---- Minecraft Crash Report ----' build-scene.log 2>/dev/null; then
		sleep 3
		pkill -f 'fml.modFolders=chest_transporter.*' 2>/dev/null
	fi
	sleep 2
done
wait $gradle_pid
echo "exit $?"
grep -E '\[scene\] (CHECK|DONE|PERF)' build-scene.log | sed -E 's/^.*\[scene\] //'
ls run/screenshots/ 2>/dev/null | grep scene_
echo "--- errors"
grep -E '/(ERROR|FATAL)\]|Caused by|Exception' build-scene.log | grep -v 'NoSuchFileException: server.properties' | sed -E 's/^\[[^]]*\] //' | cut -c1-300 | sort | uniq -c | sort -rn | head -30
