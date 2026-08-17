# Changelog

## 0.1.0 — 17/08/2026

- Criada interface Android em Kotlin + Jetpack Compose + Material 3.
- Adicionado modo de privacidade máxima por `AccessibilityService`, sem captura de tela.
- Adicionado modo OCR opcional com `MediaProjection` e frames temporários somente em RAM.
- Adicionado OCR local com Tesseract4Android para inglês, japonês, chinês simplificado e coreano.
- Modelos `tessdata_fast` fixados por commit, verificados por SHA-256 no build e embutidos no APK.
- Substituído ML Kit pelo `TranslationManager` oficial do Android para eliminar a dependência de SDK com telemetria.
- Removida completamente a permissão `INTERNET` do aplicativo.
- Adicionada tradução on-device EN/JA/ZH/KO → português brasileiro, conforme disponibilidade do serviço/modelos do sistema.
- Adicionada detecção automática local de script/idioma e seleção manual de idioma.
- Adicionado overlay por bloco, posicionamento adjacente no OCR, filtro contra leitura do próprio overlay e controle flutuante.
- Adicionados frequência de 1–5 análises/s, detecção de mudança e cache LRU somente em memória.
- Adicionada liberação explícita de `ImageReader`, `VirtualDisplay`, `MediaProjection`, OCR, tradutores, bitmaps, cache e overlays.
- Adicionada auditoria de privacidade no GitHub Actions, testes unitários, lint e builds debug/release.
- Adicionado fluxo de assinatura persistente com validação de `applicationId`, versão e fingerprint antes de qualquer Release estável.
