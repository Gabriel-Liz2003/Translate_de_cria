# Translate de Cria

Tradutor Android em tempo real que detecta textos visíveis e posiciona traduções sobre ou próximas aos blocos originais.

## Privacidade

O projeto foi desenhado para que **conteúdo da tela não saia do aparelho**.

- o APK não declara permissão `INTERNET`;
- não solicita câmera, microfone ou armazenamento;
- não inclui analytics, telemetria ou rastreamento;
- não grava screenshots nem vídeo;
- não persiste texto reconhecido;
- pipelines de Accessibility/OCR não registram conteúdo em logs;
- cache de traduções existe somente em RAM e é apagado ao encerrar a sessão;
- `FLAG_SECURE` e demais proteções do Android não são contornadas.

O armazenamento privado usado pelo OCR contém **somente os modelos Tesseract**, nunca frames ou textos capturados.

## Modos

### Privacidade máxima — Accessibility

Usa `AccessibilityService` e `AccessibilityNodeInfo` para ler somente textos que o aplicativo aberto disponibiliza pela árvore de acessibilidade. Não faz captura de tela.

É o modo preferencial para navegador, redes sociais e interfaces Android tradicionais. Jogos, canvas, vídeos, imagens e UIs que não expõem texto à acessibilidade podem não fornecer conteúdo.

### OCR da tela — somente em RAM

Usa `MediaProjection` + `ImageReader` após consentimento explícito do Android.

Fluxo de cada frame:

1. `Image` é adquirido do `ImageReader`;
2. pixels são copiados temporariamente para um `Bitmap` em RAM;
3. `Image` é fechado imediatamente;
4. Tesseract executa OCR local;
5. resultados nativos/imagem interna do Tesseract são limpos com `clear()`;
6. `Bitmap` é reciclado antes da etapa de tradução;
7. nenhum pixel ou texto reconhecido é gravado ou transmitido.

O OCR usa Tesseract4Android e modelos `tessdata_fast` para inglês, japonês, chinês simplificado e coreano. Os modelos são baixados **durante o build**, a partir de um commit fixo, têm SHA-256 verificado e são embutidos no APK.

## Tradução local

A tradução usa o framework oficial `TranslationManager` do Android e solicita tradutores on-device EN/JA/ZH/KO → `pt-BR`.

O app consulta quais pares estão disponíveis no dispositivo e oferece acesso às configurações de tradução do sistema quando o fabricante disponibiliza essa tela. Se o aparelho não oferecer um `TranslationService`/modelo compatível, o app informa a limitação em vez de enviar texto a uma API remota.

A detecção automática inicial usa heurísticas locais por script Unicode. Selecionar manualmente EN/JA/ZH/KO costuma reduzir custo e ambiguidades no OCR.

## Stack

- Kotlin + Jetpack Compose + Material 3
- `minSdk 31`, `compileSdk/targetSdk 36`
- Android Gradle Plugin 8.13.2 + Gradle 8.13 + JDK 17
- Tesseract4Android 4.9.0
- `tessdata_fast` fixado no commit `87416418657359cb625c412a48b6e1d6d41c29bd`
- Android `TranslationManager` para tradução on-device

## Desempenho

A frequência configurável é de 1–5 análises/s. O OCR:

- mantém no máximo dois buffers do `ImageReader`;
- processa apenas um frame por vez;
- descarta frames intermediários;
- compara amostras de luminância para evitar OCR de tela praticamente idêntica;
- reutiliza traduções por um cache LRU de sessão em memória;
- tenta colocar traduções OCR adjacentes ao texto e filtra regiões dos próprios overlays para reduzir feedback visual.

## Controle flutuante

O botão `T` oferece:

- pausar/continuar;
- traduzir novamente;
- ocultar/mostrar traduções;
- abrir configurações;
- encerrar serviço.

As traduções são renderizadas por bloco, preservando aproximadamente posição e dimensão do texto e evitando colisões simples entre overlays.

## Encerramento e rotação

Ao desativar OCR, o serviço libera `ImageReader`, `VirtualDisplay`, `MediaProjection`, Tesseract, tradutores, bitmaps, cache, coroutines, thread de captura e overlays. Redimensionamentos/rotação reutilizam o `VirtualDisplay` da sessão com nova superfície, em vez de iniciar uma segunda captura.

## Limitações reais

- Accessibility só lê o que o app de destino publica na árvore de acessibilidade.
- MediaProjection depende de consentimento do usuário e do que o Android permite capturar.
- Conteúdo protegido por `FLAG_SECURE` pode ficar vazio/preto e não é contornado.
- Alguns apps podem impedir overlays de terceiros.
- Disponibilidade e qualidade da tradução on-device dependem do serviço/modelos oferecidos pelo Android/fabricante do aparelho.
- O modo automático de OCR é mais pesado e heurístico; idioma manual é recomendado em jogos.

## Build, testes e distribuição

`.github/workflows/android.yml` executa:

1. download dos modelos OCR fixados + verificação SHA-256;
2. auditoria estática de privacidade;
3. testes unitários;
4. Android Lint;
5. `assembleDebug`;
6. `assembleRelease`;
7. upload do APK debug como Artifact.

A auditoria falha se o Manifest ganhar `INTERNET`, câmera, microfone ou armazenamento; se ML Kit/Firebase/MediaRecorder reaparecerem; ou se houver `Log.*` nos pipelines de captura.

### Assinatura oficial

Releases estáveis exigem uma chave persistente nos GitHub Secrets e o fingerprint público em `SIGNING_CERT_SHA256.txt`. Antes de publicar, o workflow valida assinatura, `applicationId`, `versionCode`, `versionName` e SHA-256 do certificado. Consulte [`docs/SIGNING.md`](docs/SIGNING.md).

Enquanto a chave oficial estiver `UNCONFIGURED`, a publicação estável é bloqueada intencionalmente. O APK debug é apenas para testes e usa `applicationId` `com.gabrielliz.translatedecria.debug`, portanto não deve ser tratado como a instalação oficial atualizável.

## Licenças de terceiros

Consulte [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
