# Cockpit: kit para a Google Play

## Nome e descrições
**Nome (máx. 30):** Cockpit: diário do carro

**Descrição curta (máx. 80):**
PT: Consumos, manutenção, despesas e prazos do teu carro num só sítio.
EN: Fuel, maintenance, expenses and deadlines for your car in one place.

**Descrição completa (PT):**
O Cockpit é o diário de bordo do teu carro. Regista abastecimentos, manutenções, pneus, seguro, IUC e inspeção, e vê quanto gastas e quanto consome.

• Consumo médio, custo por km e gastos por mês, com gráficos
• Avisos de óleo, inspeção, seguro e IUC, mesmo com a app fechada
• Preços de combustível perto de ti e mapa de postos (dados da DGEG)
• Carros elétricos: carregamentos em kWh e mapa de carregadores (OpenStreetMap)
• Ler faturas e o conta-quilómetros com a câmara
• Cartões de fidelização de combustível com código de barras
• Widget no ecrã inicial
• Histórico antigo: importa um CSV ou adiciona vários registos seguidos
• Cópia de segurança em ficheiro ou na tua conta Google
• Comunidade opcional: mostra o teu carro com um nickname e fala com outros condutores
• Português e inglês, modo claro e escuro, mais de 40 marcas com as suas cores

Os teus dados ficam no telemóvel. A nuvem e a comunidade só funcionam se entrares com Google, e são opcionais.

**Full description (EN):** same list in English: logbook, reminders, fuel prices and map, electric cars and chargers, receipt and odometer scanning, fuel cards, widget, history import, backups, optional community, PT/EN, light/dark.

## Dados para o formulário «Segurança dos dados»
- Localização aproximada: usada para postos e carregadores, não guardada por nós. Os carregadores são pedidos ao servidor Overpass (OpenStreetMap), que recebe a posição.
- Informações pessoais (email, nickname) e dados do carro: só com login Google e cópia ou comunidade ligadas; encriptados em trânsito; o utilizador pode apagar a conta e os dados na app.
- Fotos: ficam no telemóvel; vão para a nuvem só se ligar a cópia.
- Mensagens da comunidade: guardadas no Firebase entre os dois utilizadores.
- Sem anúncios, sem venda de dados.
- Link da política de privacidade: o documento publicado (obrigatório).

## Classificação de conteúdo
Tem mensagens entre utilizadores: responde «sim» a conteúdo gerado por utilizadores. Idade mínima da comunidade: 16 anos (já pedida na app). Tem denúncia, bloqueio e moderação.

## Antes de publicar
1. Conta de programador Google Play (25 USD, pagamento único).
2. Gerar o ficheiro assinado: Android Studio > Build > Generate Signed App Bundle (AAB). Cria uma keystore e GUARDA-A com a palavra-passe em dois sítios: se a perderes não podes atualizar a app.
3. Adicionar a impressão digital SHA-1 da keystore de lançamento no Firebase (Definições do projeto > a tua app Android), senão o login Google falha na versão da loja.
4. Ícone 512x512, imagem de destaque 1024x500, pelo menos 2 capturas de ecrã de telemóvel.
5. Páginas: política de privacidade com URL público, e o email de contacto.
6. Contas novas de programador: teste fechado com 12 testadores durante 14 dias antes de poderes publicar em produção.
7. Mudar as regras do Firebase para as do ficheiro REGRAS_FIREBASE.json e criar o nó admins.
8. Mudar a chave Logo.dev secreta que ficou exposta.
