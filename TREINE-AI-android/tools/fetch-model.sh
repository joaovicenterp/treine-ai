#!/usr/bin/env bash
# Baixa o modelo de pose do MediaPipe para dentro do APK.
# O arquivo tem ~3 MB e não fica versionado no repositório.
# O CI faz isso sozinho; rode este script só para compilar na sua máquina.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p app/src/main/assets
URL="https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
curl -fSL --retry 3 -o app/src/main/assets/pose_landmarker_lite.task "$URL"
ls -lh app/src/main/assets/pose_landmarker_lite.task
echo "Modelo pronto. Agora: ./gradlew assembleRelease"
