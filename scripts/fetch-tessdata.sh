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
declare -A EXPECTED_SHA256=(
  [eng.traineddata]="7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"
  [jpn.traineddata]="1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d"
  [chi_sim.traineddata]="a5fcb6f0db1e1d6d8522f39db4e848f05984669172e584e8d76b6b3141e1f730"
  [kor.traineddata]="6b85e11d9bbf07863b97b3523b1b112844c43e713df8b66418a081fd1060b3b2"
)

mkdir -p "$OUT_DIR"

for file in "${FILES[@]}"; do
  target="$OUT_DIR/$file"
  echo "Fetching pinned OCR model: $file"
  curl --fail --silent --show-error --location --retry 3 \
    "$BASE_URL/$file" \
    --output "$target"
  test -s "$target"

  actual="$(sha256sum "$target" | awk '{print $1}')"
  expected="${EXPECTED_SHA256[$file]}"
  if [[ "$actual" != "$expected" ]]; then
    rm -f "$target"
    echo "SHA-256 mismatch for $file"
    exit 1
  fi
  echo "$file verified: $actual"
done

echo "OCR models fetched and verified from tessdata_fast commit $TESSDATA_COMMIT"
