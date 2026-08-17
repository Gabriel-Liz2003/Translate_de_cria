# Translate de Cria

Tradutor Android em tempo real que posiciona traduções sobre ou perto dos blocos de texto visíveis em outros apps.

## Modos

### Privacidade máxima — Accessibility

Usa `AccessibilityService` e `AccessibilityNodeInfo` para ler **somente o texto que o app aberto disponibiliza na árvore de acessibilidade**. Não faz captura de tela. É o modo recomendado para navegador, redes sociais e interfaces Android tradicionais.

Limitação: jogos, canvas, vídeos, imagens, OpenGL/Vulkan e UIs que não expõem texto à acessibilidade podem não fornecer nenhum conteúdo.

### OCR da tela — somente em RAM

Usa `MediaProjection` + `ImageReader` com consentimento explícito do Android. O frame é copiado para um `Bitmap` em RAM, processado pelo ML Kit e reciclado após o OCR. O `Image` é fechado imediatamente depois da cópia.

- nenhuma screenshot é gravada;
- nenhum vídeo é criado;
- nenhum frame é enviado;
- texto reconhecido não é persistido;
- nenhum texto/imagem capturado vai para logs;
- não há analytics, telemetria ou rastreamento;
- não existem permissões de câmera, microfone ou armazenamento;
- `FLAG_SECURE` e demais proteções do Android são respeitadas.

A permissão `INTERNET` existe somente para baixar modelos do ML Kit. Tradução e OCR são executados no dispositivo. Depois de baixar os modelos EN/JA/ZH/KO → PT, a tradução pode funcionar offline.

## Stack

- Kotlin + Jetpack Compose + Material 3
- `minSdk 26`, `compileSdk/targetSdk 36`
- Android Gradle Plugin 8.13.2 + Gradle 8.13 + JDK 17
- ML Kit Text Recognition v2 (Latin, Japanese, Chinese, Korean) empacotado no APK
- ML Kit Language Identification empacotado no APK
- ML Kit On-device Translation com modelos baixáveis

## Desempenho

A frequência configurável é de 1–5 análises/s. O OCR descarta frames intermediários, processa apenas um frame por vez e compara amostras de luminância para evitar OCR em telas praticamente idênticas. Traduções repetidas usam um cache LRU somente em memória, destruído com a sessão.

No OCR com idioma `Automático`, quatro reconhecedores locais podem ser consultados quando a tela muda. Selecionar EN/JA/ZH/KO manualmente é mais eficiente para jogos.

## Controle flutuante

O botão `T` oferece:

- pausar/continuar;
- traduzir novamente;
- ocultar/mostrar traduções;
- abrir configurações;
- encerrar serviço.

O overlay de tradução é não tocável, preserva aproximadamente a posição/tamanho do bloco original e desloca blocos que colidiriam.

## Limitações reais do Android

- MediaProjection exige consentimento do usuário para cada nova sessão nas versões modernas do Android.
- Apps podem impedir captura com `FLAG_SECURE`; o resultado pode ser uma região preta/vazia.
- Apps podem esconder overlays de terceiros com mecanismos de segurança do sistema.
- Accessibility só pode ler o que o app de destino publica na árvore de acessibilidade.
- Qualidade de tradução on-device pode ser inferior a serviços cloud e traduções não-inglês ↔ não-inglês podem usar inglês como idioma intermediário no ML Kit.

Não há código para contornar essas proteções.

## Build e distribuição

O workflow `.github/workflows/android.yml` executa auditoria de privacidade, testes unitários, lint, build debug e compilação release. Builds de teste são publicados como Artifact.

Releases estáveis exigem a chave persistente configurada nos GitHub Secrets. O workflow recusa publicação se o SHA-256 do certificado não corresponder a `SIGNING_CERT_SHA256.txt`. Consulte [`docs/SIGNING.md`](docs/SIGNING.md).

> Não instale o APK debug como se fosse uma versão estável: ele usa assinatura de teste e `applicationId` com sufixo `.debug` justamente para não interferir com a instalação oficial.
