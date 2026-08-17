#!/usr/bin/env bash
set -euo pipefail

TESSDATA_COMMIT="87416418657359cb625c412a48b6e1d6d41c29bd"
BASE_URL="https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/${TESSDATA_COMMIT}"
OUT_DIR="app/src/main/assets/tessdata"
FILES=(
  eng.traineddata
  jpn.traineddata
  chi_sim.traineddata
  kor.traineddata
)

mkdir -p "$OUT_DIR"

for file in "${FILES[@]}"; do
  target="$OUT_DIR/$file"
  echo "Fetching pinned OCR model: $file"
  curl --fail --silent --show-error --location --retry 3 \
    "$BASE_URL/$file" \
    --output "$target"
  test -s "$target"
done

echo "OCR models fetched from tessdata_fast commit $TESSDATA_COMMIT"
sha256sum "${FILES[@]/#/$OUT_DIR/}"
