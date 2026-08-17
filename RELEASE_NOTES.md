# Translate de Cria v0.1.0 — 17/08/2026

Primeira versão do tradutor de tela Android.

## Destaques

- Tradução por Accessibility sem captura de tela.
- OCR opcional por MediaProjection, processado somente em RAM.
- OCR local Tesseract para inglês, japonês, chinês simplificado e coreano.
- Tradução EN/JA/ZH/KO → português brasileiro pelo serviço on-device do Android, quando disponível no aparelho.
- Traduções posicionadas por bloco e botão flutuante de controle.
- Frequência configurável de 1–5 análises/s, detecção de mudanças e cache somente em memória.
- Proteção contra feedback do próprio overlay no modo OCR.

## Privacidade

- O APK não possui permissão `INTERNET`.
- Nenhuma screenshot ou vídeo é gravado.
- Frames do OCR existem temporariamente apenas em RAM e os bitmaps são reciclados após o OCR.
- Texto reconhecido não é persistido.
- Conteúdo capturado não é enviado nem colocado em logs.
- Não há analytics, telemetria ou rastreamento.
- Não são solicitadas permissões de câmera, microfone ou armazenamento.
- `FLAG_SECURE` e proteções do Android não são contornados.

## Distribuição

A Release estável só será publicada após configurar a chave de assinatura persistente e registrar o SHA-256 oficial do certificado. Enquanto isso, builds debug podem ser baixados nos Artifacts do GitHub Actions exclusivamente para testes.
