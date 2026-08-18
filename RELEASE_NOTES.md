# Translate de Cria v0.1.1 — 18/08/2026

Correção da primeira versão de testes do tradutor de tela Android.

## Correções

- Corrigido crash ao tocar em **Configurações de tradução do Android** quando a ROM não fornece `TranslationManager`/`TranslationService` compatível.
- A tela de configurações do sistema agora só pode ser aberta quando o Android realmente fornece um `PendingIntent` válido.
- Falhas do OEM ao abrir essa tela são tratadas sem encerrar o app.
- O `TranslationEngine` não derruba a sessão quando o serviço de tradução do sistema está ausente.
- O menu flutuante expandido agora é vertical e legível em telas estreitas.

## Recursos mantidos

- Tradução por Accessibility sem captura de tela.
- OCR opcional por MediaProjection, processado somente em RAM.
- OCR local Tesseract para inglês, japonês, chinês simplificado e coreano.
- Tradução EN/JA/ZH/KO → português brasileiro pelo serviço on-device do Android, quando disponível no aparelho.
- Traduções posicionadas por bloco e botão flutuante de controle.
- Frequência configurável de 1–5 análises/s, detecção de mudanças e cache somente em memória.

## Privacidade

- O APK não possui permissão `INTERNET`.
- Nenhuma screenshot ou vídeo é gravado.
- Frames do OCR existem temporariamente apenas em RAM e os bitmaps são reciclados após o OCR.
- Texto reconhecido não é persistido.
- Conteúdo capturado não é enviado nem colocado em logs.
- Não há analytics, telemetria ou rastreamento.
- Não são solicitadas permissões de câmera, microfone ou armazenamento.
- `FLAG_SECURE` e proteções do Android não são contornados.

## Compatibilidade importante

Algumas ROMs/OEMs não oferecem o serviço oficial `TranslationManager` com modelos de tradução on-device. Nesses aparelhos, a v0.1.1 informa a limitação corretamente e não tenta abrir configurações inexistentes. Uma alternativa de tradução local independente do serviço do fabricante será avaliada sem enviar o conteúdo da tela para servidores.
