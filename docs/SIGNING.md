# Assinatura persistente

A versão oficial deve usar **uma única chave de assinatura por toda a vida do aplicativo**. Nunca substitua silenciosamente uma chave perdida: uma APK assinada por outra chave não atualizará instalações existentes.

## Chave oficial

A chave oficial inicial foi criada em **17/08/2026**. Somente o fingerprint público é versionado no repositório.

SHA-256 oficial do certificado:

`6771C0C5739586BB293AA25276E06ECD7D7050FA63181B364024416A10C68653`

O arquivo privado `.jks`, sua representação Base64 e as senhas **nunca devem ser commitados**.

## Secrets esperados

Em **Settings → Secrets and variables → Actions**, crie exatamente estes Repository Secrets:

- `ANDROID_KEYSTORE_B64` — conteúdo Base64 do keystore oficial;
- `ANDROID_KEYSTORE_PASSWORD` — senha do keystore;
- `ANDROID_KEY_ALIAS` — alias da chave;
- `ANDROID_KEY_PASSWORD` — senha da chave.

### Pelo celular

Abra o repositório no navegador do Android e use **Settings → Secrets and variables → Actions → New repository secret**. Cadastre os quatro valores acima individualmente. O GitHub não permite recuperar o valor de um Secret depois de salvo; isso é esperado.

Mantenha também uma cópia privada do arquivo `.jks` e das credenciais fora do repositório. Se essa chave for perdida, futuras APKs não poderão atualizar instalações assinadas por ela.

## Proteção por fingerprint

`SIGNING_CERT_SHA256.txt` contém somente o SHA-256 público do certificado oficial. O GitHub Actions:

1. reconstrói o keystore temporariamente em `$RUNNER_TEMP`;
2. assina a APK release;
3. verifica a assinatura com `apksigner`;
4. compara o SHA-256 com `SIGNING_CERT_SHA256.txt`;
5. valida `applicationId`, `versionCode` e `versionName`;
6. só então permite Artifact/Release estável.

Se o fingerprint for diferente, o workflow falha antes da publicação. Nunca altere o fingerprint para fazer uma chave diferente "passar" na validação.

## Disparo da Release

O workflow mantém `workflow_dispatch` para publicação manual. Para permitir que o agente conclua a distribuição sem depender do botão da interface do GitHub, um push em `main` também pode solicitar publicação **somente quando a mensagem do commit começar com `release:`**.

Essa regra evita disparos acidentais por comentários, documentação ou mensagens que apenas mencionem a palavra release. O prefixo `release:` **não ignora nenhuma proteção**: se os Secrets estiverem ausentes, o job falha; se o fingerprint, `applicationId`, `versionCode` ou `versionName` não corresponderem ao esperado, a Release não é criada.

O fluxo recomendado continua sendo: branch de release → testes/PR → merge em `main` com título começando por `release:` → assinatura e verificação → GitHub Release.
