# Reimagui R3 — Home 5%, relógio-galeria e Spotify

Preparação local de 2026-10-28; nenhuma operação na TV. Base estável: `6aa063da`,
branch `feat/reimagui-idle-art-v144`; desenvolvimento: `feat/reimagui-r3-clock-spotify`.
O conteúdo de CODEX_STATUS.md foi confirmado no GitHub pelo blob
`27d4e95d55ce5e0a47cfc902f32c014b71f64935`, igual ao local.

## Entrega inicial: opacidade

Preparado privadamente em `work/reimagui-r3-opacity5/`, ignorado pelo Git:
`CANDIDATE_CONFIG_5.json`, `ROLLBACK_CONFIG_10_EXACT.json` e manifesto com hashes.
A origem é o readback da retomada bem-sucedida de 25 de outubro, não o original de 30%.

```diff
-  "idle_art_mode_opacity": 10
+  "idle_art_mode_opacity": 5
```

Validação local: JSON válido, diferença semântica de uma chave, 15 segundos preservados,
todos os restantes bytes preservados e transformação inversa igual à origem byte a byte.
As 90 pinturas não foram copiadas nem alteradas. Recuperação pelo comando não muda no
código; o resultado físico a 5% ainda não foi testado. Não alterar o default global de 30%.
Não usar o aplicador de tema completo. Uma futura sessão autorizada deve verificar que
a configuração ainda coincide com a base de 10%, registar as seis escolhas e só então
aplicar a chave; divergência exige parar e preparar nova base. Reversão restaura o JSON
exato de 10%; não promete restaurar preferências internas inacessíveis.

## Arquitetura encontrada na base estável e ativação

| Componente | Responsabilidade atual |
|---|---|
| `MainActivity` | Agenda `ClockActivity` por `home_idle_clock_minutes`; zero desliga. Idle Art de 15s é independente. |
| `ClockActivity` | Infla `dream_clock`, inicia/para a cena com o ciclo da Activity; tecla termina e é consumida. |
| `ClockDream` | DreamService selecionável nas definições Android; não interativo, inicia/para a mesma cena. |
| `ClockScene` | Relógio/data móveis, brilho ou imagem; decode fora da UI, invalidação ao parar. |
| `RemoteKeys` | Ação screensaver pede o screensaver do sistema via Somnambulator; fallback para ClockActivity. |

O JSON instalado e relido a 10% não tem chaves de relógio. No código atual os defaults
são `home_idle_clock_minutes=0`, `clock_wallpaper=false`, dim 50 e glow ligado.
Não foi encontrada uma recolha de settings secure entre os ficheiros consultados de work.
**O screensaver efetivamente selecionado na TV permanece desconhecido.** Não inferir
ClockDream pela sua declaração no manifest nem equiparar o código atual ao APK histórico.
Numa sessão posterior, confirmar visualmente a seleção e o modo de ativação; se for
um screensaver de outro pacote, alterações em ClockScene não o afetarão.

Na base estável, ClockScene escolhe a imagem única ou a primeira da coleção; não acompanha
a rotação das 90 pinturas. O fundo BitmapDrawable não configura explicitamente um modo
de ajuste que garanta as proporções. Há também layout alternativo `layout-iw/dream_clock.xml`.

## Requisitos atualizados: relógio como galeria (2026-10-28)

- Apresentar a coleção atual de 90 obras em ecrã inteiro, sem deformação, preservando
  ficheiros, ordem configurada e `wallpapers/captions.txt` em leitura apenas.
- Mostrar título e artista a partir das legendas existentes, reutilizando a apresentação
  da Home sempre que possível. Não inferir metadados nem reescrever as legendas.
- Manter `wallpaper_rotation_minutes=10` e a continuidade da obra e do prazo entre Home
  e relógio, nos dois sentidos. Entrar no relógio não inicia uma nova sequência.
- Relógio sempre visível, composição discreta, com espaço reservado às legendas e ao
  Spotify (capa, título e artista), exclusivamente nesta tela.
- Sem painéis pretos sólidos, película branca, blur ou vídeo. Pintura com alpha 1 e
  dim zero no perfil Reimagui; texto com sombra, sem véu global sobre a imagem.
- Idle Art continua separado: 15s e 5% apenas na navegação Home; nunca na pintura,
  legenda da obra, relógio ou Spotify. A preparação de uma chave permanece independente
  das futuras opções do relógio-galeria.

**Ajuste visual proposto:** reutilizar `CENTER_CROP` da Home para preencher o ecrã sem
esticar. Isto preserva proporções, mas pode recortar margens de obras com outro formato.
Substitui a proposta inicial FIT_CENTER com margens. Mostrar simultaneamente toda a obra
sem recorte e preencher um ecrã de proporção diferente não é possível sem margens ou
alteração da imagem; o critério aqui é continuidade com a Home, com recorte explicitado.

## Desenho aprovado e implementação local

| Classe | O que já existe | Reutilização |
|---|---|---|
| `WallpaperRotation` | Lista ordenada, seleção sequencial/shuffle, procura e vencimento | Usar diretamente; não criar um segundo seletor no relógio. |
| `WallpaperPlayer` | CENTER_CROP, decode limitado, rotação, legendas e estado persistido | Instanciar no fundo dedicado da cena; adaptar ciclo de vida/estado de forma partilhada. |
| `WallpaperCaptions` | Parser filename + TAB + texto, limite de 80 caracteres | Usar o mesmo mapa por nome da imagem efetivamente apresentada. |
| `ClockScene` | Cena comum aos dois relógios, decode próprio de uma imagem e movimento | No modo galeria, delegar fundo/legenda ao player e retirar o decode duplicado desse caminho. |

### Estado e prazo partilhados

WallpaperPlayer já usa SharedPreferences `wallpaper_rotation` com `collection`, `image`
e `changed`. `collectionKey` deriva de nomes e tamanhos; a mudança de coleção limpa o
estado. Usar exatamente a mesma coleção efetiva de Config nos dois ecrãs, incluindo
ordem e filtros existentes, sem reconstruir uma lista distinta ou copiar as pinturas.
Uma escolha futura de imagem única/nenhuma deve ser respeitada, não anulada silenciosamente.
O perfil atual segue o tema com a coleção de 90 obras.

O player já conserva `changed` ao restaurar a seleção existente com instante válido.
Ainda assim, criar outro player exige coordenar `update()`, que captura o estado antes
do decode assíncrono: um resultado atrasado pode substituir uma seleção mais recente.
Preservar e tornar explícita a distinção entre **restaurar a obra selecionada** e
**avançar a seleção**, gravando o instante apenas quando há avanço real, nunca numa
simples entrada/retoma. A partilha deve ter um único responsável por confirmar avanços.

Implementação escolhida: WallpaperGallery mantém uma única sessão/player por configuração,
com leases para Home, ClockActivity e ClockDream. GalleryOwners dá prioridade ao relógio;
reparenting da mesma ImageView e legenda transfere a imagem já carregada. Sem segunda cópia
por ecrã, segundo decoder ou timer concorrente. O contexto do player é o da aplicação;
a referência a cada contentor termina no fecho da lease. WallpaperWork limita a uma
operação de decode pendente e invalida resultados depois de pausa/fecho; uma transferência
direta entre hosts ativos conserva o decode. O snapshot separado proposto inicialmente
não foi necessário: só o player único confirma a seleção. Configuração diferente retira
a sessão anterior antes de criar a nova, sem transportar Views entre sessões.

Ao entrar antes do prazo, mostrar a mesma obra e conservar o tempo restante. Ao vencer,
avançar uma vez segundo WallpaperRotation; depois de uma longa ausência, não reproduzir
em rajada todas as mudanças perdidas. Ao regressar à Home, mostrar a seleção do relógio
sem reiniciar o prazo. Cobrir mudança de coleção, ficheiro ilegível, relógio do sistema
recuado e recriação do processo com as regras de vencimento existentes.

Antes desta implementação, a Home chamava `update()` a cada minuto, podendo acrescentar
até cerca de um minuto ao intervalo configurado. Com galeria ligada, só o player partilhado
agenda callbacks: no máximo um minuto para verificar grupos horários, ou o tempo restante
se o prazo chegar antes. O tick da Home não avança a galeria. Sem proprietário ativo,
o timer para; ao retomar, a seleção e o prazo são reconciliados com as preferências.
A cadência de movimento do relógio é independente da cadência das obras e da música.

### Fundo, legenda e composição

ClockScene cria em runtime um FrameLayout dedicado à galeria sob o relógio,
compatível com os layouts normal e `layout-iw`, sem duplicar XML. Spotify não foi adicionado. O player recebe esse contentor, nunca o decor inteiro;
a legenda deve ficar acima da imagem e em zona reservada, sem colisões com a hora/capa.
Reutilizar `WallpaperPlayer.makeCaption/showCaption`, extraindo apenas estilo/posicionamento
se necessário: fonte regular, texto claro, sombra e transição já usados pela Home.
A posição pode adaptar-se ao relógio sem duplicar o parser nem introduzir painéis sólidos.

O formato atual guarda título/artista numa única string, não em campos separados.
Preservar a string; não dividir automaticamente por hífen ou travessão. A limitação atual
de 80 caracteres e a elipse visual exigem teste: se ocultarem título/artista, propor uma
extensão compatível do leitor/apresentação, mantendo o ficheiro original intacto.
Só atualizar legenda depois de a imagem correspondente estar realmente apresentada;
sem legenda válida, ocultá-la em vez de manter a da obra anterior.

No modo galeria Reimagui, não aplicar `clock_wallpaper_dim=50` herdado nem o glow sobre
as pinturas: reutilizar dim zero da Home e preservar a opção de cor da coleção. O modo
antigo permanece disponível com a galeria desligada. Relógio não desaparece a cada minuto:
substituir nesse modo o fade até alpha zero por deslocamento discreto com limites de
posição e zonas reservadas. Evitar centro da pintura e sobreposição dos textos; a
legibilidade e o impacto sobre cada obra exigirão validação visual, não são garantidos
pela análise de código.

### Sequência aprovada (resultado executado abaixo)

1. Entrega offline da galeria, antes de Spotify: opção desligada por defeito; controlador
   partilhado de estado/prazo, player reutilizado em ClockScene e legenda sincronizada.
2. `ClockActivity` e `ClockDream` continuam a gerir entrada/saída; start/stop da cena
   controlam player/callbacks. Fechar executor e libertar recursos na destruição/detach;
   cancelamento de decode e callbacks precisa de geração também em stop/restart rápido.
3. Preservar a primeira imagem síncrona da Home com a opção desligada; usar carregamento
   inicial assíncrono na sessão partilhada, com resultado invalidável. Não decodificar 90 obras de
   uma vez. Testar memória na transição: a Home parada pode ainda reter o seu bitmap.
4. Só depois adicionar o protótipo Spotify com dados fictícios e estados explícitos,
   integrado nas zonas reservadas; rede/autenticação permanecem no componente separado.

Ficheiros previstos: WallpaperPlayer, eventual controlador partilhado de estado,
ClockScene, ClockActivity/ClockDream, MainActivity apenas para integrar o prazo comum,
ambos os layouts, Config/config.xml e documentação das opções. WallpaperRotation e
WallpaperCaptions serão reutilizados sem alterações se os testes confirmarem suficiência.
Testes JVM previstos: entrada aos 9 min e mudança aos 10, retorno com prazo preservado,
90→1 em ordem sequencial, shuffle sem repetição imediata, troca de coleção, decode tardio,
stop/start, erro de imagem, correspondência imagem/legenda, texto longo e isolamento Idle Art.
Mocks não provam ciclo de vida Android, legibilidade ou consumo: estes exigem validação própria.

**Estado em 2026-10-28:** galeria implementada e compilada localmente, desligada por defeito
(`clock_gallery=false`). A opção não escolhe nem ativa screensaver; identificação da tela
configurada permanece pendente. Spotify não implementado. As 90 pinturas, as legendas,
a configuração ativa de 10% e o candidato separado de 5% não foram modificados.

## Spotify: desenho técnico proposto

Componente Android separado, fora do launcher principal, para OAuth/rede/cache limitada.
ClockScene consome apenas metadados sanitizados e capa local por IPC explícito protegido
por assinatura; subscrição apenas enquanto o relógio está visível, sem polling na Home.
A localização desse projeto e o contrato IPC serão definidos antes de acrescentar código
de rede; não o apresentar como módulo-card da Home.

- Consultar a conta em `GET /me/player/currently-playing`, scope
  `user-read-currently-playing`. A origem é a conta, não notificações locais da TV;
  reprodução noutro dispositivo é objetivo a validar com a conta real.
- Authorization Code com PKCE S256 e state aleatório; browser externo, redirect registado
  de acordo com as regras Spotify. Sem client secret no APK. Tokens no armazenamento
  privado do componente, protegidos com Keystore, sem logs/backups/exportação.
- Atualização moderada apenas com subscritor visível; parar ao fechar o relógio. Tratar
  ausência de item/204, pausa, anúncios/tipos desconhecidos, expiração e offline;
  401 permite uma renovação, 403 pede intervenção, 429 respeita Retry-After e quota.
  Expirar dados antigos em vez de mostrar eternamente a última música.
- Descarregar uma capa limitada por vez, validar tamanho/tipo e origem, manter proporções;
  não partilhar tokens, nomes de dispositivos ou resposta integral com o launcher.
- A documentação atual exige Premium do proprietário em development mode, até cinco
  utilizadores autorizados. Não foi verificado dashboard, conta ou acesso efetivo.
  Não há autenticação, chamadas autenticadas nem compatibilidade validada nesta sessão.
- Rever atribuição/link Spotify na apresentação antes da integração real. A documentação
  proíbe sincronizar gravações com visuais; a rotação das pinturas não seguirá músicas.

Fontes oficiais consultadas em 2026-10-28:
[reprodução atual](https://developer.spotify.com/documentation/web-api/reference/get-the-users-currently-playing-track),
[PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow),
[redirects](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri),
[quotas e development mode](https://developer.spotify.com/documentation/web-api/concepts/quota-modes).

## Sequência e critérios de validação

| Etapa | Entrega / prova exigida | Estado |
|---|---|---|
| R3.0 | Branch, mapa, plano, candidato 5% e reversão 10% | Preparado localmente |
| R3.1a | Galeria offline nos dois relógios: player, estado/prazo e legendas partilhados | Implementado localmente; testes JVM/build/lint aprovados; TV pendente |
| R3.1b | Protótipo visual Spotify no relógio, testes de estados/limites | Proposto |
| R3.2 | Componente Spotify, PKCE, IPC protegido, testes com respostas simuladas | Planeado |
| R3.3 | Conta real e reprodução noutro dispositivo; sem instalar na TV nesta fase | Pendente |
| R3.4 | Sessão TV autorizada: identificar relógio, instalar/testar separadamente e aplicar 5% | Não autorizado agora |

Antes de commit ou push: apresentar ficheiros, diff completo, resultados e riscos e obter
aprovação explícita. Para alterações Android, build/test/lint e tamanho do APK antes de
propor instalação. Teste físico deve cobrir comando real, legibilidade, proporções,
rotação, saída/retorno e consumo. Não repetir auditorias R1/R2 encerradas.

## Resultado local R3.1a — 2026-10-28

- JDK 17 / SDK 34, Gradle offline: `testReleaseUnitTest lintRelease assembleRelease` passou.
- 273 testes JVM, zero falhas/erros/ignorados; 11 novos testes. Cobertura nova de políticas:
  Home→relógio→Home, Dream sobre Home retomada, prioridade e callbacks tardios, pausa/retoma,
  fecho, uma operação pendente, rejeição de imagens, preservação do prazo e volta 90→1.
- Inclui regressão dos testes existentes de legendas, tamanhos, moods e Idle Art.
- 44 testes Python pertinentes aprovados: 25 de chaves/tabela de temas, 3 de temas,
  11 de legendas e 5 de links. Não repetida a suíte R2 com limitações já classificadas.
- Lint: zero erros/fatais, 80 avisos; não se afirma que todos são anteriores sem baseline.
- APK local: 512.417 bytes, abaixo de 524.288 (margem 11.871). Não instalado nem publicado.
- `git diff --check` sem erros. Sem novas dependências, permissões, rede ou Spotify.
- O primeiro build encontrou um erro Java de inicialização, corrigido antes do build final.

Ficheiros de produção: `ClockActivity.java`, `ClockDream.java`, `ClockScene.java`,
`Config.java`, `MainActivity.java`, `WallpaperPlayer.java`, `WallpaperRotation.java`,
novos `WallpaperPlayback.java`, `WallpaperGallery.java`, `GalleryOwners.java`,
`WallpaperWork.java`, `WallpaperDecodeGuard.java` e `res/values/config.xml`. Todos sob `launcher/app/src/main/`.
Testes: `GalleryLifecycleTest.java` e `WallpaperDecodeGuardTest.java` (novos),
e `WallpaperRotationTest.java` sob
`launcher/app/src/test/java/com/gathin/tvhome/`. Documentação: este plano,
[continuidade](CODEX_STATUS.md) e [chaves de temas](../themes/README.md).

### Limites da validação

Testes JVM exercitam as políticas reais com eventos e falhas simulados; não executam
BitmapFactory, reparenting de Views, Activity/DreamService ou renderização Android.
Nenhum teste instrumentado, screenshot, comando real ou medição de memória/desempenho
na TV foi feito. Preservou-se o código de despertar; não se afirma validação física.
CENTER_CROP pode recortar margens. O parser mantém o limite de 80 caracteres; no relógio
são permitidas duas linhas, sem inventar título/artista separados. Legibilidade exige
revisão visual futura. O carregamento inicial da galeria é assíncrono e pode mostrar
brevemente a cor de fundo se não houver imagem em memória; erro em todas as imagens
conserva a última imagem válida, ou a cor de fallback, sem repetir decodes indefinidamente.

A sessão retém uma imagem atual e pode descodificar a seguinte durante a rotação;
não retém as 90 obras. Pressão de memória liberta a imagem quando não há host ativo.
Uma descodificação nativa já em curso pode terminar depois de stop/close; o resultado
é descartado e não altera a seleção. Mudança de configuração substitui a sessão;
a duração dessa libertação nativa não foi medida. A primeira imagem da Home no modo
antigo continua síncrona; a galeria usa carregamento assíncrono também num arranque frio.

Revisão local disponível como patch completo em `work/reimagui-r3-review.patch`, incluindo
ficheiros novos, sem staging/commit. Requer aprovação antes de commit ou push. A instalação
futura requer sessão autorizada e confirmação do screensaver efetivamente selecionado.

## Revisão final dirigida — 2026-10-28

### Referências e ciclo de vida

Inspeção estática: a sessão e as Views partilhadas usam application context. O player
mantém apenas o contentor atual; attach remove imagem/legenda do pai anterior. Sem
proprietário ativo, stop cancela o timer e detach põe root a null. MainActivity.onDestroy,
ClockActivity.onDestroy e ClockDream.onDetachedFromWindow fecham as leases; o último
fecho liberta o player e a referência estática shared. GalleryOwners só guarda hosts
ativos: uma Home ainda resumed sob Dream é retida legitimamente até pause/close.
Uma lease fechada pode continuar no próprio host até este ser recolhido, mas já não é
alcançável a partir da sessão estática. Não identificado um caminho para reter uma
Activity destruída na transição normal. Não é prova por heap dump nem teste instrumentado.

### Exclusividade e defeito corrigido

Timer de rotação único por sessão: removeCallbacks antes de reagendar, stop sem proprietário,
e update da lease da Home é vazio. O tick de movimento do relógio é independente e não
altera a seleção. WallpaperWork impede dois loads pendentes no mesmo player; apenas um
player ativo confirma imagem/changed. A sessão retirada é fechada antes da substituição.

**Defeito encontrado:** shutdownNow não garante interrupção de BitmapFactory. A sessão
retirada podia ainda descodificar enquanto uma sessão nova iniciava outro decode.
Corrigido com WallpaperDecodeGuard, serialização no processo em torno de todo o decode
(incluindo conversão do bitmap) dos WallpaperPlayers. Trabalho interrompido que aguarda
a guarda não inicia decode. O resultado de uma sessão fechada continua a ser descartado
antes de tocar no estado persistido. A guarda não bloqueia a UI no modo galeria, cujo
carregamento é assíncrono; no modo antigo, o primeiro decode síncrono pode esperar por
um decode anterior em curso numa troca de modo/configuração. Nenhuma nova thread/serviço.

Apenas reexecutados os testes afetados: 3 de WallpaperDecodeGuard e 7 de GalleryLifecycle,
todos aprovados; sem repetição das suítes completas ou dos 44 testes Python anteriores.
Build/lint offline atualizados pela correção: sucesso, zero erros/fatais e 80 avisos.
APK final local: **512.545 B**, margem **11.743 B** até ao limite. Os resultados anteriores
273 JVM/44 Python permanecem históricos; os XML atuais contêm os 10 testes dirigidos.

### Ativação futura na Xiaomi (proposta, não aplicada)

O APK atualmente instalado não recebe esta funcionalidade só por mudar JSON; será
necessária instalação futura do código revisto, numa sessão expressamente autorizada.
Manter a branch estável e não alterar HOME, helpers de comando ou permissões.

Opção recomendada para o primeiro ensaio: `ClockActivity`, acionada internamente pela
Home, sem depender dos menus de screensaver da Xiaomi. Depois de recolher e guardar
os bytes atuais, preparar apenas estas opções adicionais no JSON, sem reaplicar o tema:

```json
{
  "clock_gallery": true,
  "home_idle_clock_minutes": 2
}
```

Este fragmento **não substitui o config completo**. Dois minutos é proposta para aprovação,
não uma escolha já feita. Preservar `idle_art_mode_seconds=15`, opacidade atual 10%,
rotação 10 minutos, dim zero, legendas e demais escolhas; o candidato 5% fica separado.
Cronologia prevista desde a última interação: aos 15s atenua só a navegação da Home;
aos 120s abre o relógio-galeria. A rotação conta desde a última mudança de pintura,
não desde a entrada no relógio. Ao despertar, o tratamento existente consome a tecla
e retorna à Home; a nova inatividade inicia os dois contadores independentes.

Antes do ensaio, confirmar o screensaver do sistema e o prazo de suspensão: um deles
pode antecipar-se ou interromper o relógio. Não alterar esses valores sem autorização.
Alternativa posterior: selecionar ClockDream nas definições que o firmware oferecer,
mantendo `home_idle_clock_minutes=0` para usar só o gatilho do sistema. O menu e o
componente efetivamente selecionado não foram confirmados nesta Xiaomi; não prometer
compatibilidade. Não ativar simultaneamente ambos os gatilhos no primeiro ensaio.
Reversão das opções: restaurar os bytes guardados, incluindo a ausência original das
chaves se aplicável; não tocar nas pinturas/legendas nem usar o aplicador de tema.

### Fronteira de publicação

Lista fechada de 19 ficheiros: Java de produção/testes, config.xml e três documentos
(este plano, CODEX_STATUS.md e themes/README.md). O índice permanece vazio. APK/build,
work, backup, shots e chaves ADB estão fora dessa lista e ignorados; nenhum ficheiro
desses diretórios está rastreado. Alterações em texto revistas sem credenciais ou
identificadores privados encontrados. Não confiar só no gitignore: usar caminhos
explícitos da lista e rever o staged diff após eventual autorização.

Diff completo final e lista exata estão preparados localmente em
`work/reimagui-r3-final-review.patch` e `work/reimagui-r3-publish-files.txt`.
O patch inclui ficheiros novos e alterações documentais desde a base estável; esses
artefactos de revisão ficam fora do commit. Destino proposto: branch isolada existente
`feat/reimagui-r3-clock-spotify`, nunca a branch estável. Commit/push aguardam nova
aprovação explícita. Spotify continua fora deste código; será a etapa seguinte.
