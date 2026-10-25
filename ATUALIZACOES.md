# Atualizações pela app (GitHub)

A app procura a última *Release* de `JoaoAntunesss/cockpit`, compara a versão da etiqueta (tag) com a instalada e, se for maior, descarrega o `.apk` anexado e abre o instalador. Os dados mantêm-se.

## Publicar uma versão nova
1. Em `app/build.gradle.kts` sobe `versionCode` (ex.: 4) e `versionName` (ex.: "1.2").
2. Android Studio: Build > Generate Signed App Bundle / APK > APK. Cria uma chave (keystore) na primeira vez e **guarda-a com cópia de segurança**: todas as versões têm de ser assinadas com a mesma chave, senão o Android recusa a atualização. Escolhe a variante release.
3. No GitHub: Releases > Draft a new release. Etiqueta `v1.2` (igual ao versionName, com v à frente). Título à tua escolha. No texto, escreve as novidades (aparecem na app). Arrasta o `.apk` para a zona de anexos. Publish release.
4. No telemóvel: Definições > Procurar atualizações. A app também avisa uma vez por dia ao abrir.

## Avisos
- Na primeira instalação por esta via, o Android pede para permitir "instalar apps desconhecidas" a esta app.
- O Play Protect pode mostrar um aviso, por o APK não vir da Play Store.
- A primeira versão instalada com uma chave diferente da das seguintes tem de ser desinstalada antes (perde dados locais; faz backup).
- Repositório público = o código fica visível. O `google-services.json` não deve ser enviado (já está no .gitignore).
