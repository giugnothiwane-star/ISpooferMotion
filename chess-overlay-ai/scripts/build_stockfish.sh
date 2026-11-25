#!/usr/bin/env bash
# Compile le moteur Stockfish officiel (GPLv3) pour Android ARM64 avec le NDK,
# puis le place dans app/src/main/jniLibs/arm64-v8a/libstockfish.so.
# Android n'autorise l'exécution de binaires que depuis le dossier des bibliothèques
# natives de l'application : le nom "lib*.so" est donc obligatoire.
#
# Usage : scripts/build_stockfish.sh [android|host]
#   android (défaut) : binaire ARMv8 + dotprod (Snapdragon 8 Gen 2 du Galaxy S23 Ultra)
#   host             : binaire x86-64 pour les tests d'intégration sur la machine de build
set -euo pipefail

SF_TAG="${SF_TAG:-sf_17.1}"
NET_BIG="nn-1c0000000000.nnue"
NET_SMALL="nn-37f18f62d772.nnue"
TARGET="${1:-android}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${SF_WORK_DIR:-$ROOT/build/stockfish-src}"
JOBS="$(nproc 2>/dev/null || echo 4)"

if [ ! -d "$WORK/.git" ]; then
  rm -rf "$WORK"
  git clone --depth 1 --branch "$SF_TAG" https://github.com/official-stockfish/Stockfish.git "$WORK"
fi

cd "$WORK/src"
# Réseaux NNUE officiels (le nom du fichier est le début de son empreinte SHA-256).
for net in "$NET_BIG" "$NET_SMALL"; do
  if [ ! -f "$net" ]; then
    curl -fsSL --retry 4 -o "$net" "https://raw.githubusercontent.com/official-stockfish/networks/master/$net" \
      || curl -fsSL --retry 4 -o "$net" "https://tests.stockfishchess.org/api/nn/$net"
  fi
  expected="${net#nn-}"; expected="${expected%.nnue}"
  actual="$(sha256sum "$net" | cut -c1-12)"
  if [ "$actual" != "$expected" ]; then
    echo "Empreinte invalide pour $net ($actual)"; rm -f "$net"; exit 1
  fi
done

if [ "$TARGET" = "android" ]; then
  NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-${ANDROID_HOME:-/opt/android-sdk}/ndk/27.2.12479018}}"
  export PATH="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
  make clean >/dev/null
  make -j"$JOBS" build ARCH=armv8-dotprod COMP=ndk
  llvm-strip stockfish || true
  OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
  mkdir -p "$OUT"
  cp stockfish "$OUT/libstockfish.so"
  echo "OK : $OUT/libstockfish.so"
else
  make clean >/dev/null
  make -j"$JOBS" build ARCH=x86-64-sse41-popcnt COMP=gcc || make -j"$JOBS" build ARCH=x86-64 COMP=gcc
  strip stockfish || true
  OUT="$ROOT/build/stockfish-host"
  mkdir -p "$OUT"
  cp stockfish "$OUT/stockfish"
  echo "OK : $OUT/stockfish"
fi
