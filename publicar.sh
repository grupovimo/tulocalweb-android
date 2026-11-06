#!/bin/sh
# Pasa apps/android del monorepo (mostrador-v2) a este repo, commitea en los dos y empuja (compila en Actions).
# Igual que tengolibre-android/publicar.sh. «[skip ci]» en el mensaje para no compilar.
set -e
M=~/Trabajo/vimo/mostrador-v2; R=~/Trabajo/vimo/tulocalweb-android; MSJ="$1"
A="$M/apps/android/app/src/test/resources"
# La lista de pantallas de iOS para la prueba de paridad (ParidadTest): cada .swift tiene que tener su .kt.
(cd "$M/apps/ios/TuLocalWeb/Pantallas" && find . -name '*.swift' | sed 's|^\./||' | sort) > "$A/paridad.txt"
# Las respuestas de la API simulada, las mismas que lee iOS (apps/pruebas).
mkdir -p "$A/pruebas" && cp "$M/apps/pruebas/respuestas.json" "$A/pruebas/"
rsync -a --delete --exclude .git --exclude publicar.sh --exclude local.properties --exclude build --exclude .gradle --exclude .kotlin "$M/apps/android/" "$R/"
PIE="

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_019W2t1DphCtMkajKz7vZJLk"
cd "$M" && git add apps/android apps/pruebas && (git diff --cached --quiet || git commit -qm "Android: $MSJ$PIE") && git push -q origin main
cd "$R" && git add -A && (git diff --cached --quiet || git commit -qm "$MSJ$PIE")
T=$(gh auth token -u grupovimo)
git -c credential.helper= -c "http.extraheader=Authorization: basic $(printf 'x-access-token:%s' "$T" | base64)" push -q origin main
git log --oneline -1
