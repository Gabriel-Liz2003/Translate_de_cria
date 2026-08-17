# Assinatura persistente

A versão oficial deve usar **uma única chave de assinatura por toda a vida do aplicativo**. Nunca substitua silenciosamente uma chave perdida: uma APK assinada por outra chave não atualizará instalações existentes.

## Secrets esperados

Em **Settings → Secrets and variables → Actions**, crie:

- `ANDROID_KEYSTORE_B64` — keystore oficial codificado em Base64;
- `ANDROID_KEYSTORE_PASSWORD` — senha do keystore;
- `ANDROID_KEY_ALIAS` — alias da chave;
- `ANDROID_KEY_PASSWORD` — senha da chave.

O keystore e as senhas **não podem ser commitados**.

## Fingerprint oficial

Depois de criar a chave oficial uma única vez, obtenha o SHA-256 do certificado público com `keytool`/`apksigner` e substitua `UNCONFIGURED` em `SIGNING_CERT_SHA256.txt` pelo digest hexadecimal, sem espaços.

O GitHub Actions:

1. reconstrói o keystore temporariamente em `$RUNNER_TEMP`;
2. assina a APK release;
3. verifica a assinatura com `apksigner`;
4. compara o SHA-256 com `SIGNING_CERT_SHA256.txt`;
5. valida `applicationId`, `versionCode` e `versionName`;
6. só então permite Artifact/Release estável.

Se o fingerprint for diferente, o workflow falha antes da publicação.
