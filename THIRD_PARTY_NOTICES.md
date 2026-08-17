# Third-party notices

## Tesseract4Android

- Projeto: `adaptech-cz/Tesseract4Android`
- Versão usada: `4.9.0`
- Licença: Apache License 2.0
- Uso no projeto: engine OCR local executada dentro do aplicativo.

O código e os binários dessa dependência permanecem sujeitos à sua licença original.

## tessdata_fast

- Projeto: `tesseract-ocr/tessdata_fast`
- Commit fixado: `87416418657359cb625c412a48b6e1d6d41c29bd`
- Licença: Apache License 2.0
- Arquivos incluídos no APK durante o build:
  - `eng.traineddata`
  - `jpn.traineddata`
  - `chi_sim.traineddata`
  - `kor.traineddata`

O workflow verifica o SHA-256 de cada arquivo antes do empacotamento para garantir que os modelos correspondam exatamente ao conteúdo esperado do commit fixado.
