# Cockpit

Diário de bordo para o teu carro, em Android. Consumos, manutenção, despesas, prazos, preços dos combustíveis e parques de estacionamento, tudo no telemóvel e com as cores da marca do teu carro.

[**Descarregar a última versão (APK)**](https://github.com/JoaoAntunesss/cockpit/releases/latest)

## O que faz

- **Combustível:** regista abastecimentos (também parciais) e calcula o consumo real. Avisa de valores estranhos (km, preço, duplicados) e mostra como cada abastecimento se compara com a tua média. Aceita elétricos e híbridos (incluindo plug-in).
- **Manutenção:** avisos por km e por tempo (óleo, correia, pneus...), diário de intervenções com fotos de faturas e estimativa de custo da próxima.
- **Prazos:** seguro, inspeção e IUC, com avisos mesmo com a app fechada. O IUC segue o calendário novo (2026, 2027 e 2028 em diante).
- **Gastos:** gráficos por mês, custo por km, previsão do mês, orçamento mensal, resumo do ano e relatório anual em PDF.
- **Preços:** média nacional por combustível (DGEG) com o totem de LEDs, alerta quando baixa e postos e carregadores perto de ti.
- **Parque:** guarda onde estacionaste, leva-te de volta ao carro e procura parques por perto.
- **Widgets:** o teu carro, preços dos combustíveis, "Abastecer" com um toque e avisos.
- **Cartões:** cartões de combustível e fidelidade com código de barras.
- **Extras:** carro partilhado, comunidade, pesquisa nos históricos, bloqueio com o PIN ou impressão digital do telemóvel, backup em ficheiro e na nuvem, importação por CSV, relatório para venda do carro, português e inglês.
- **Atualização pela app:** Definições > Procurar atualizações.

<!-- Capturas: junta PNGs em docs/screenshots e escreve aqui, por exemplo:
<img src="docs/screenshots/inicio.png" width="240"> -->

## Instalar

1. Abre a página de [Releases](https://github.com/JoaoAntunesss/cockpit/releases) no telemóvel e descarrega o `cockpit.apk`.
2. Abre o ficheiro. O Android pede para permitir instalar apps desconhecidas: aceita.
3. Das vezes seguintes, a app atualiza-se sozinha por Definições > Procurar atualizações.

## Compilar

Requisitos: Android Studio recente, JDK 17.

1. Abre a pasta no Android Studio e deixa o Gradle sincronizar.
2. Opcional: para o login Google e a cópia na nuvem, cria um projeto Firebase e põe o teu `google-services.json` em `app/` (nunca o envies para o GitHub; já está no `.gitignore`). Sem ele, a app compila e funciona só com os dados locais.
3. Opcional: chave pública do Logo.dev em `gradle.properties` (`logoDevKey=...`).
4. Run.

Mais detalhes em [LEIAME.md](LEIAME.md). Publicar versões novas: [ATUALIZACOES.md](ATUALIZACOES.md). As regras da base de dados estão em [REGRAS_FIREBASE.json](REGRAS_FIREBASE.json).

## Tecnologia

Kotlin, Jetpack Compose (Material 3), Room, WorkManager, Firebase (Auth e Realtime Database), OpenStreetMap (osmdroid e Overpass), ML Kit (leitura de faturas) e Coil.

## Privacidade

Os dados ficam no telemóvel. Só saem se entrares com Google e ligares a cópia na nuvem, ou se usares a comunidade ou o carro partilhado. A localização é usada só para postos, carregadores e parques por perto, e para te levar ao carro.

## Créditos

Mapas © colaboradores do OpenStreetMap. Preços dos combustíveis: DGEG. Símbolos das marcas: Simple Icons e Logo.dev.
