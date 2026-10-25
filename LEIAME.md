# Diário de Bordo (Android, Kotlin + Jetpack Compose)

1. Android Studio > File > Open > escolhe esta pasta (DiarioDeBordo).
2. Espera o Gradle Sync acabar (descarrega o Gradle 8.9 e as dependências).
3. Liga o telemóvel (depuração USB) ou cria um emulador, e carrega em Run.

## O que tem
- Garagem com vários carros; a marca do carro muda as cores da app toda (Theme.kt, enum Brand).
- Abastecimentos com consumo real L/100 km (só entre depósitos cheios) e gráfico.
- Diário de intervenções (data, km, tipo, quem fez, peças e mão de obra).
- Avisos de manutenção por km e/ou meses; registar uma intervenção do mesmo tipo reinicia o aviso.
- Gastos por mês (combustível, mecânica, fixas) e custo por km.
- Dados guardados no telemóvel com Room (funciona sem rede).

## Próximos passos possíveis
- Sincronizar com Firebase Firestore (as entidades em Data.kt têm a mesma estrutura planeada).
- Fotos de peças e faturas.
- Notificações dos avisos.

## Preços dos combustíveis
O ecrã Combustível mostra o preço médio nacional de hoje para o combustível do carro (mais barato, mais caro e nº de postos).
Vem de `GET https://api.apiaberta.pt/v1/fuel/prices` (ver FuelPrices.kt). Fica guardado no telemóvel para aparecer sem rede.
No formulário de abastecimento aparece a média de hoje ao lado do preço por litro que pagaste.
É a média do país, não o preço de um posto em concreto.

## Logótipos das marcas
Ordem: ficheiro local, depois logótipo online, depois a inicial da marca.

**Online (opcional, Logo.dev).** O Clearbit Logo API fechou em dezembro de 2025, por isso não serve. O Logo.dev é o substituto:
1. Cria conta grátis em logo.dev e copia a chave pública (começa por `pk_`).
2. Põe no `gradle.properties` do projeto: `logoDevKey=pk_xxxxxxxx`
3. Faz Sync. A app vai buscar o logótipo de cada marca (Coil) e guarda-o em cache.
Alguns planos grátis pedem um link de atribuição ao Logo.dev na app; vê as regras deles.

**Local.** Copia o ficheiro da marca para `app/src/main/res/drawable/` com o nome
logo_peugeot, logo_opel, logo_citroen, logo_renault, logo_vw, logo_bmw, logo_mercedes, logo_toyota, logo_ford, logo_fiat
(.png, .webp ou .xml). Tem prioridade sobre o online e funciona sem rede.

## Postos perto de mim
No ecrã Combustível, o botão «Postos perto de mim» pede a localização e mostra os postos do raio escolhido (5, 10 ou 20 km),
por preço ou por distância, com a diferença para a média nacional e quanto poupas num depósito. Tocar num posto abre-o no mapa.
Os preços por posto vêm da DGEG (precoscombustiveis.dgeg.gov.pt, API PesquisarPostos). Ver Stations.kt.
A rota /v1/fuel/stations da API Aberta foi testada e não serve: ignora a localização e trazia preços de fevereiro/março.

## Escolher o carro pelo catálogo do site
Em «Novo carro» há o botão «Preencher a partir do catálogo do site»: Marca > Modelo > Ano > Versão.
Lê o índice /catalogoIndice e a ficha /catalogo/{chave} do Firebase do site (leitura pública, por REST, sem login). Ver Catalog.kt.
Preenche marca (e as cores da app), modelo, ano, motor e combustível quando é claro. Tudo continua editável.
Nota: o catálogo atual só tem 13 marcas (sem Peugeot, Opel, Citroën, Renault, Fiat) e alguns dados técnicos
parecem não ser reais, por isso confirma sempre os campos antes de guardar.

## Documentos e prazos
Em Manutenção > «Documentos e prazos»: seguro, inspeção, IUC, livrete e outros, com data de validade,
seguradora, apólice, custo, notas e um print/foto (fica guardado só no telemóvel, em files/docs).
A cor muda a verde, amarelo ou vermelho conforme os dias que faltam (por defeito avisa 30 dias antes).
Os que precisam de atenção aparecem também no Início. Os avisos de manutenção continuam personalizáveis
(nome, km e/ou meses) e há atalhos prontos (óleo, correia, travões, pneus…). A base de dados passou a versão 2
com migração, por isso os dados que já tens não se perdem.

## Notificações
A app avisa, mesmo fechada, de seguro/inspeção a acabar (no dia limite escolhido, 7 dias, 1 dia, no próprio dia e se expirou)
e de manutenções em breve ou atrasadas. Verifica de 12 em 12 horas. No Android 13 ou superior, aceita a permissão de notificações quando a app pedir.

## Login Google e cópia na nuvem (precisa de configurar a Firebase uma vez)
Usa o mesmo projeto do teu site (carros-dc76c). Sem estes passos a app funciona na mesma, só sem nuvem.
1. Firebase Console > projeto carros-dc76c > Definições do projeto > «Adicionar aplicação» > Android.
   Nome do pacote: com.cockpit.app
   SHA-1: no Android Studio, separador Gradle (à direita) > DiarioDeBordo > app > Tasks > android > signingReport, e copia o SHA1 da variante debug.
2. Authentication > Método de início de sessão > ativa «Google».
3. Volta às definições da app Android, descarrega o google-services.json (só depois de ter o SHA-1 e o Google ativado) e põe-no na pasta app/ (ao lado do build.gradle.kts da app).
4. Realtime Database > Regras: ACRESCENTA (sem apagar as regras do site) dentro de "rules":
     "diarioBordo": {
       "$uid": {
         ".read": "auth != null && auth.uid === $uid",
         ".write": "auth != null && auth.uid === $uid"
       }
     }
5. Sync no Android Studio e corre. No topo da app, o ícone da conta abre o ecrã para entrar.
Guarda: carros, abastecimentos, intervenções, despesas, avisos e documentos com as imagens (reduzidas para ~1400 px).
Num telemóvel novo, ao entrar sem carros a app traz tudo da nuvem sozinha.

## Km estimados
Pelo ritmo dos últimos meses a app estima os km de hoje, usa-os nos avisos e pede-te no Início para confirmares ou corrigires. Desliga-se em Definições.

## Fotos e faturas
Intervenções e despesas aceitam foto ou fatura (ficam no telemóvel e vão na cópia da nuvem).

## Exportar
Menu ⋮ > Definições e exportar: PDF do histórico (bom para mostrar a um comprador) ou CSV para o Excel. Também há Comparar carros.

## Atalhos
Carrega muito tempo no ícone da app: Abastecer, Intervenção, Despesa, Postos e mapa.


## Novidades: totem de preços, aspeto e marcas
- Separador Combustível: painel preto estilo totem de posto, com dígitos LED (último dígito mais pequeno) e etiqueta colorida por combustível. O teu combustível fica realçado.
- Definições > Aspeto: modo Sistema / Escuro / Claro e cor dos dígitos (vermelho, âmbar, verde, azul gelo, branco, rosa ou a cor da marca).
- Mais de 40 marcas. Os logótipos de 34 marcas vêm dentro da app (res/drawable/logo_*.xml, Simple Icons, CC0), funcionam sem internet.
- Sem ficheiro de logótipo (aparecem via Logo.dev se tiveres a chave, senão a inicial): Mercedes-Benz, Cupra, Land Rover, Jaguar, Alfa Romeo, Lancia, Lexus, BYD.

## Cartões das bombas
Aba «Cartões»: guarda os cartões de fidelização (Galp, BP, Repsol, Moeve, Prio, Shell, Esso, Continente, Auchan, Intermarché). «Ler código com a câmara» usa o leitor da Google Play Services (sem permissão de câmara; precisa de Play Services). No posto abres o cartão e o código aparece grande, com o brilho no máximo. Entram na cópia na nuvem. Logos locais: Shell, Continente, Auchan, Intermarché; os outros via Logo.dev ou inicial. Base de dados passa à versão 4 (migração automática).

## Inglês
Definições > Língua: Sistema, Português ou English. «Sistema» usa português se o telemóvel estiver em português, senão inglês. Os textos estão em Strings.kt (dicionário PT > EN) e as frases com valores usam t("pt", "en"). Para acrescentar uma frase nova: escreve tr("texto em português") e junta a tradução no mapa de Strings.kt. Nota: as categorias já guardadas (Óleo e filtros, Seguro, IUC...) e os nomes dos tipos de documento ficam em português.

## Comunidade (fase 1)
Ícone das pessoas no topo. Precisa de login Google. Cada pessoa cria um perfil opcional com um nickname único (3 a 20 caracteres), escolhe que carros mostra e se os km aparecem exatos ou em intervalo. Só ficam públicos marca, modelo, ano, combustível, km e consumo médio (nunca matrícula, custos, documentos ou localização). Há lista com pesquisa por nickname e é possível apagar o perfil.
Antes de usar: na consola Firebase (projeto app-dashboardcarros) > Realtime Database > Regras, cola o conteúdo de REGRAS_FIREBASE.json e publica. Estas regras substituem as anteriores e mantêm a cópia privada de cada utilizador.
As mensagens (pedidos, aceitar, conversas, bloquear e denunciar) são a fase 2.

## Comunidade (fase 2): pedidos e mensagens
No perfil de alguém há «Pedir para falar». A outra pessoa vê o pedido em «Pedidos» e aceita ou recusa. Depois de aceitar, falam por mensagens de texto (máx. 1000 caracteres, sem fotos) em «Conversas». Há Bloquear e Denunciar (a denúncia guarda as últimas 10 mensagens em /reports, que só tu vês na consola Firebase). Apagar o perfil apaga também a lista de conversas.
IMPORTANTE: volta a colar REGRAS_FIREBASE.json nas regras do Realtime Database (mudaram). Limites: as mensagens só aparecem quando a app está aberta (não há notificação de mensagem nova, isso pede Cloud Functions e o plano Blaze); não há limite de mensagens por minuto.

## Logos das bombas
Em res/drawable-nodpi estão os PNG das bombas que enviaste (galp, bp, repsol, moeve, prio) e os vetoriais de Shell, Continente, Auchan e Intermarché. Para trocar um, substitui o ficheiro station_<chave>.png mantendo o nome. Petroprix tambem esta la. Falta o da Esso.

## Apagar conta
Conta e nuvem > «Apagar conta e dados da nuvem»: apaga a cópia privada, o perfil público, o nickname, a lista de conversas e a conta Google da app (Firebase Auth). Os dados do telemóvel ficam. Mensagens já enviadas continuam visíveis para quem as recebeu. Se o Firebase pedir login recente, termina a sessão, entra e repete.

## Logos extra
Adicionados em res/drawable-nodpi: BYD, Lexus, Jaguar, Land Rover, Alfa Romeo, Cupra, Mercedes-Benz e Esso. Lancia incluída: já não falta nenhum logo de carro.

## Carros elétricos e postos de carregamento
- Em Carro > Comb. há agora «Elétrico». A bateria (kWh) substitui o depósito, os «Litros» passam a kWh e o consumo aparece em kWh/100 km.
- No separador Combustível, os elétricos veem o cartão «Postos de carregamento» em vez do totem de preços.
- Os carregadores vêm do OpenStreetMap (Overpass API, sem chave): nome, operador, potência e fichas quando existem. Verde no mapa = rápido (50 kW ou mais).
- A localização é enviada ao servidor Overpass só para a pesquisa (atualiza a política de privacidade se a publicares).

## Histórico antigo, introdução e animações
- Nos formulários novos (abastecimento, intervenção, despesa) há «Guardar e adicionar outro»: guarda, limpa o formulário e mantém a data, para meter histórico seguido.
- Definições > «Importar histórico (CSV)»: lê o mesmo formato do export (Data;Tipo;Km;Descrição;Litros;Custo;Notas). Ignora repetidos. As datas aceitam dd/MM/aaaa ou aaaa-MM-dd.
- Registos antigos já não mexem nos avisos: só atualizam o aviso se forem mais recentes que o último.
- Introdução de 4 ecrãs na primeira abertura (Onboarding.kt) e transições suaves entre separadores.

## Lote B: widget, OCR, alerta de preço, cópia local, moderação
- **Widget:** toca longo no ecrã inicial > Widgets > Cockpit. Mostra o carro, os km e o aviso mais urgente.
- **Ler fatura / conta-quilómetros:** botão com a câmara nos formulários de abastecimento, intervenção e despesa, e no diálogo de km. O texto é lido no telemóvel (ML Kit). Confirma sempre os valores.
- **Alerta de preço:** Definições > «Avisar quando o combustível baixar». Usa o combustível destacado no totem.
- **Cópia local:** Definições > «Guardar cópia em ficheiro» / «Restaurar de um ficheiro». Inclui fotos e cartões.
- **Limites (no telemóvel):** 20 mensagens por minuto, 15 pedidos e 5 denúncias por dia.
- **Painel de denúncias:** cola o novo REGRAS_FIREBASE.json na consola. Depois cria à mão em Realtime Database o nó `admins/<o teu uid>` com o valor `true` (o uid vê-se em Authentication > Users). Quem estiver em admins vê «Painel de denúncias» na Comunidade, com «Dispensar» e «Banir» (remove o perfil e impede de voltar a publicar).
- **Perfil:** campo «Sobre ti» (até 140 caracteres).
- Novas dependências: ML Kit text-recognition (precisa de sincronizar o Gradle).

## Híbridos
- Combustíveis novos: «Híbrido (Gasolina 95)», «Híbrido (Gasóleo)», «Híbrido plug-in (Gasolina 95)», «Híbrido plug-in (Gasóleo)». Preços e postos usam o combustível entre parênteses.
- Nos plug-in, o formulário de abastecimento tem «Carga elétrica (kWh)». As cargas ficam fora do consumo de combustível e aparecem no histórico com ⚡.
- A base de dados passou à versão 5 (migração automática; nada se perde).

## Pneus, orçamento, custo real, resumo do ano e relatório de venda
- **Pneus** (separador Manutenção): jogos montados e guardados, km por jogo, avisos de desgaste, idade e rotação. Fazem parte da cópia na nuvem e da cópia em ficheiro.
- **Orçamento mensal**, **preço de compra**, **km na compra** e **valor atual**: no formulário do carro. Barra na Home, aviso aos 80% e aos 100%, e cartão «Custo real do carro» em Gastos.
- **Resumo do ano**: botão em Gastos, com partilha em texto.
- **Relatório para venda**: Definições, PDF com manutenção, pneus e prazos. Matrícula e valores são opcionais.
- O widget atualiza ao guardar registos.
- A base de dados passou à versão 6 (migração automática).

## Batch C — Widget de preços e LEDs nítidos
- Novo widget "preços dos combustíveis" (PriceWidget.kt): totem com os preços médios, dígitos LED desenhados em bitmap, cor LED e combustível favorito da app; toque abre a app; atualiza sozinho e ao atualizar preços na app.
- LEDs (Led.kt): halo reduzido (menos desfocado), segmentos sólidos e nítidos.

## Batch D — Acabamento de interface
- Splash screen (androidx.core:core-splashscreen) com o ícone, ícone monocromático (Android 13+), pré-visualização dos dois widgets.
- Estados vazios com ilustração e botão de ação; placeholders a brilhar (shimmer) em vez do círculo a rodar.
- Deslizar para a esquerda apaga abastecimentos, intervenções e despesas; aviso "Guardado" e "Registo apagado · Desfazer" (snackbar).
- Acessibilidade: dígitos LED lidos pelo TalkBack. Em ecrãs largos o conteúdo fica centrado (máx. 640 dp).

## Batch E — Aba "Parque"
- Nova aba "Parque" (Parking.kt): guardar onde deixaste o carro (com nota de piso/lugar), levar-me ao carro, e procurar parques de estacionamento num raio de 1,5 km (OpenStreetMap/Overpass), com filtros Todos/Grátis/Garagem e mapa.
- O mapa dos postos deixou de ter os botões de estacionar (o ponto amarelo do carro continua a aparecer).
- A barra de navegação mostra só o nome do separador selecionado, para caber 6 separadores.

## Batch F — Retoques de design
- Cores mais claras (verde/vermelho dos avisos, etiquetas do painel), botão de registar só com "+", dica quando ainda não há média, matrícula em branco passa a "Adicionar matrícula" (toca para editar).

## Batch G — Abastecimento e nomes dos combustíveis
- Formulário de abastecimento: escolha entre "Km do conta-quilómetros" e "Km desde o último" (a app soma ao último registo e mostra o valor do conta-quilómetros).
- Totem e widget de preços: escondem gasóleo colorido e de aquecimento; "Gasóleo B15 (biodiesel)" com etiqueta B15; o combustível escolhido passa a ignorar os escondidos.

## Batch H — Design
- Home: cartão "o que importa agora" (prazo ou aviso mais urgente, em grande), gasto do mês com contagem animada, cartões com contornos mais suaves, tiles maiores e sem contorno.
- Abastecimento: resumo grande (total e preço por litro) e posto recolhido em "Mais detalhes".
- Gastos: tocar numa coluna mostra o detalhe do mês (combustível, mecânica, fixas).

## Batch I — Visual "carro moderno"
- Quadrante e odómetro animam ao abrir (a ponta do arco brilha), cartões com contorno em gradiente da cor da marca, luz ambiente da marca no topo dos ecrãs.
- Home: "Estado do carro" com anéis de serviço animados (em vez das barras), entrada escalonada dos blocos.

## Batch J — Ponteiro e cores da marca
- Quadrante com ponteiro animado; cada marca tem agora 2.ª e 3.ª cores do logótipo (Theme.kt, mapa EXTRA) usadas no ponteiro, no contorno e brilho do painel e nas barras dos títulos.

## Batch K
- Média logo no 1.º abastecimento: campo opcional "km feitos com o depósito anterior" (cria um ponto de partida sem litros).
- IUC (Manutenção): mês da matrícula + total anual, mostra o próximo prazo com as regras 2026/2027/2028+. Fontes secundárias, os dias exatos não estão confirmados: confirmar no Portal das Finanças.
- Custo estimado da próxima intervenção nos avisos (média das últimas 3 da mesma categoria).
- Carro partilhado (Definições): código de 8 caracteres, sincronização manual por união. Apagar não se propaga. Colar o novo REGRAS_FIREBASE.json na consola (nó "shared").
- Não compilado por mim.

## Batch L
- Avisos no formulário de abastecimento: km menor que o último, salto > 3 000 km, preço por litro fora do normal, mais que o depósito, duplicado. Não bloqueiam.
- Notificação do IUC (≤ 31 dias e ≤ 7 dias do fim do mês de pagamento), se o mês da matrícula estiver definido.
- Não compilado por mim.

## Batch M
- Atualização pela app via GitHub (ver ATUALIZACOES.md). Versão 1.1 (código 3). Permissão REQUEST_INSTALL_PACKAGES e FileProvider para updates/.

## Batch N
- Versão 1.2: cartão "Custo por km" e "Previsão do mês" no ecrã inicial.

## Batch O
- Versão 1.3: alerta de consumo anormal (notificação e cartão no ecrã inicial quando o consumo sobe 15% ou mais).
