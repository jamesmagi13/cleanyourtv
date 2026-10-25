# Reimagui — R2: classificação histórica e verificação passiva proposta

Atualização: 2026-10-25 21:41 UTC. Resultados R2 publicados: `27dade59593166f7231728d2d2ebc9284ff37749`.
[Continuidade](CODEX_STATUS.md) · [Recuperação R1](REIMAGUI_R1_RECOVERY.md).
Nenhum teste repetido em R2; recolha ADB autorizada e concluída apenas com leituras.
Backups e código de produção preservados. Resultados da secção 5 publicados nos três documentos
aprovados; preparação e resultados das secções 6–8 são locais, ainda sem novo commit/push.
Primeira tentativa revertida; única retomada corrigida executada, com JSON 10%, nova
Activity e foco confirmados. Teste físico aprovado pelo utilizador; fase concluída, secções 7/8.
Decisão do utilizador em 2026-10-25: segunda cópia física recomendada, não bloqueadora;
o risco residual no disco do Mac foi compreendido e aceite. Não repetir a R1 nem os testes classificados.

## 1. Fonte e limites da classificação

Foi consultado o registo local histórico do processo unittest da R1: 874 testes em
90,820 s, saída 1, `FAILED (failures=3, errors=12, skipped=12)`. Embora uma parte
anterior da saída tenha sido truncada, os **15 blocos finais de traceback estão
completos** no registo. Não foi necessário repetir testes nem verificar novamente os backups.
Não publicar os logs brutos: podem conter caminhos pessoais e dados de outras ferramentas.

### Três falhas de contrato da árvore local

Todas são falhas reais das asserções na árvore local; não são bloqueios do sandbox.
Os leitores percorrem diretórios físicos, incluindo a pasta privada `reimagui_fase1`,
apesar de esta estar ignorada pelo Git e ausente da árvore do commit.

| ID / teste | Mensagem histórica | Causa e evidência | Classificação / intervenção |
|---|---|---|---|
| F1 — `test_addons.AddonFolderTest.test_every_addon_follows_the_rules` (subteste `reimagui_fase1`) | `AssertionError: False is not true : an add-on is its own Gradle project` | Não existem `settings.gradle` nem `settings.gradle.kts` nessa pasta; [asserção](../tests/test_addons.py#L55), [enumeração](../tests/test_addons.py#L22) | Incompatibilidade local com o contrato de add-ons; não demonstra regressão do launcher. Aplicar ação F. |
| F2 — `test_addons.AddonFolderTest.test_every_addon_has_a_screenshot` (subteste `reimagui_fase1`) | `AssertionError: False is not true : no screenshot.jpg: add one of the add-on on a real TV, see addons/README.md` | Falta `screenshot.jpg`; [asserção](../tests/test_addons.py#L71) | Incompatibilidade local com o contrato de add-ons; aplicar ação F. |
| F3 — `test_manage.ManageTest.test_every_addon_folder_is_listed_with_its_package` | `AssertionError: Items in the first set but not the second:` / `'reimagui_fase1' : addons/README.md rows and addons/ folders` | Diretório físico não tem entrada no catálogo; [comparação](../tests/test_manage.py#L18) | Incompatibilidade entre pasta privada e catálogo; aplicar ação F. |

**Ação F:** não criar projetos, screenshots ou entradas fictícias para fazer os testes passar.
Nenhuma correção de produção ou novo teste está justificado. Numa tarefa futura autorizada,
usar uma árvore isolada apenas com ficheiros versionados e repetir somente F1–F3. Se
falharem também aí, investigar uma inconsistência real do repositório. Não mover/apagar
a pasta privada nem alterar backups nesta fase. A ausência de regressão global não fica provada.

### Doze erros de preparação dos testes

**Mensagem E**, presente em cada um dos 12 tracebacks:
`PermissionError: [Errno 1] Operation not permitted` no `socket.bind` de loopback.
O erro surge antes da chamada à funcionalidade que o teste pretende avaliar.

| ID / teste | Ponto exato do traceback e evidência | Causa / decisão |
|---|---|---|
| E1 — `test_adb_closed.AdbClosedTest.test_cli_port_overrides_endpoint_port` | Mensagem E; [linha 68](../tests/test_adb_closed.py#L68), `server.bind` | Preparação bloqueada pelo ambiente; ação E |
| E2 — `test_adb_closed.AdbClosedTest.test_closed_endpoint_with_port` | Mensagem E; [linha 51](../tests/test_adb_closed.py#L51), `probe.bind` | Preparação bloqueada pelo ambiente; ação E |
| E3 — `test_adb_closed.AdbClosedTest.test_closed_port` | Mensagem E; [linha 34](../tests/test_adb_closed.py#L34), `probe.bind` | Preparação bloqueada pelo ambiente; ação E |
| E4 — `test_adb_closed.AdbClosedTest.test_endpoint_from_tv_host_env` | Mensagem E; [linha 59](../tests/test_adb_closed.py#L59), `probe.bind` | Preparação bloqueada pelo ambiente; ação E |
| E5 — `test_adb_closed.AdbClosedTest.test_endpoint_port_in_tv_host_env_overrides_tv_port_env` | Mensagem E; [linha 77](../tests/test_adb_closed.py#L77), primeiro `server.bind` | Preparação bloqueada pelo ambiente; ação E |
| E6 — `test_adb_closed.AdbClosedTest.test_endpoint_with_port` | Mensagem E; [linha 42](../tests/test_adb_closed.py#L42), `server.bind` | Preparação bloqueada pelo ambiente; ação E |
| E7 — `test_adb_closed.AdbClosedTest.test_open_port` | Mensagem E; [linha 25](../tests/test_adb_closed.py#L25), `server.bind` | Preparação bloqueada pelo ambiente; ação E |
| E8 — `test_adb_closed.AdbClosedTest.test_tv_port_env_used_when_tv_host_has_no_port` | Mensagem E; [linha 90](../tests/test_adb_closed.py#L90), `server.bind` | Preparação bloqueada pelo ambiente; ação E |
| E9 — `test_relay.RelayTest.test_a_port_already_in_use_is_reported` | Mensagem E; [linha 107](../tests/test_relay.py#L107), `taken.bind` | Preparação bloqueada pelo ambiente; ação E |
| E10 — `test_relay.RelayTest.test_an_unreachable_tv_closes_the_client_and_says_why` | Mensagem E; [linha 99](../tests/test_relay.py#L99) → `free_port`, [linha 43](../tests/test_relay.py#L43) | Preparação bloqueada pelo ambiente; ação E |
| E11 — `test_relay.RelayTest.test_bytes_go_both_ways` | Mensagem E; [linha 66](../tests/test_relay.py#L66) → `echo_server`, [linha 21](../tests/test_relay.py#L21) | Preparação bloqueada pelo ambiente; ação E |
| E12 — `test_relay.RelayTest.test_listens_on_loopback_only` | Mensagem E; [linha 83](../tests/test_relay.py#L83) → `echo_server`, [linha 21](../tests/test_relay.py#L21) | Preparação bloqueada pelo ambiente; ação E |

**Ação E:** nenhum patch de produção, novo teste ou skip automático justificado. Há uma
lacuna de cobertura: estes erros não provam que `adb-closed.py` ou `relay.py` funcionem
nem revelam defeitos nesses caminhos. Se for necessário validar essa cobertura, obter
autorização para executar apenas E1–E12 num ambiente que permita sockets locais, com
os alvos de teste locais previstos pelo código. Não repetir a suíte completa.
Os outros cenários de cada ferramenta não devem ser generalizados a estes 12 casos.

## 2. Catálogo de leitura autorizado; execução efetiva na secção 5

Pré-condições: TV com ADB previamente autorizado, alvo confirmado pela pessoa e aprovação
específica do utilizador para a ligação e recolha. Substituir todos os placeholders.
Se for preciso reabrir o transporte já autorizado, propor apenas após essa aprovação (L):

```sh
adb connect '<tv-endpoint>'
```

Não emparelhar, ativar depuração ou alterar autorizações da TV. Se não houver acesso,
relatar a condição para decidir o próximo passo; não testar endpoints por tentativa.
O âmbito é o utilizador Android 0; se o atual for outro, parar e adaptar o plano.
Antes dos ficheiros, cruzar o pacote esperado `com.gathin.tvhome` com a consulta HOME
da secção E; se houver divergência, parar e rever os alvos em vez de assumir outro pacote.
Guardar respostas completas apenas em novos ficheiros privados, sem sobrescrever backups.
Os comandos não alteram intencionalmente estado persistente, foco ou atividade da TV;
leituras podem consumir CPU/I/O e gerar registos normais do sistema.

Riscos: **L** = leitura de metadados/carga baixa; **P** = leitura com possível conteúdo
privado; **I** = leitura com I/O adicional; **C** = condicional à permissão de acesso.

**Ordem prática, sem executar todo o catálogo abaixo:**

1. Confirmar utilizador e HOME (A/E) para validar o alvo; recolher primeiro a configuração
   real em B e a versão instalada em A. Registar se coincidem com o contexto observado na TV.
2. Ler as preferências em C apenas se acessíveis; identificar as opções que podem mudar.
3. Preparar a reversão da secção 3 com os bytes recolhidos, sem aplicar nada.
4. Hash do APK, cópia do binário e diagnóstico adicional de HOME são complementos;
   não exigir correspondência por commit nem executar todos os comandos por rotina.
5. R1 e classificação F/E já concluídas: não repetir hashes das 90 pinturas nem a suíte.
   A secção D fica reservada para divergências observadas ou necessidade de recuperação.

### A. Identidade, pacote e APK ativo — L/P; hash também I

```sh
adb -s '<tv-endpoint>' shell am get-current-user
adb -s '<tv-endpoint>' shell pm path --user 0 com.gathin.tvhome
adb -s '<tv-endpoint>' shell dumpsys package com.gathin.tvhome
adb -s '<tv-endpoint>' shell sha256sum '<apk-path-returned-by-pm>'
```

Registar pacote, versão, código, estado enabled/suspended, flags e todos os caminhos
de APK devolvidos, incluindo splits; repetir o hash para cada caminho. Não inferir a
identidade a partir de `2.7`/código `8`, comuns aos APKs locais. Comparar hashes com o
manifesto privado R1 já existente. Igualdade prova identidade com o artefacto, não um
commit de código: os metadados VCS locais não certificam esse vínculo.
Se a versão/pacote e a configuração estiverem identificados, um hash do APK inacessível
não impede por si só preparar a mudança isolada de uma chave já suportada.

Se a identificação binária for realmente necessária e estiver no âmbito autorizado,
mas `sha256sum` não existir e o APK puder ser lido, alternativa de leitura da TV,
com escrita **apenas no Mac** num nome privado novo (P/I/C):

```sh
adb -s '<tv-endpoint>' pull '<apk-path-returned-by-pm>' '<new-private-local-file>'
```

Depois, hash e inspeção de recursos/assinatura são feitos localmente. Se o acesso for
recusado, registar DESCONHECIDO; não usar root, mudar permissões ou instalar ferramentas.
Antes de `pull`, confirmar no Mac que o destino não existe: o comando pode substituir
um ficheiro local existente. Nunca apontar o destino para os backups R1.

### B. Configuração persistida — P/C

```sh
adb -s '<tv-endpoint>' shell ls -ld /sdcard/Android/data/com.gathin.tvhome/files
adb -s '<tv-endpoint>' shell cat /sdcard/Android/data/com.gathin.tvhome/files/config.json
adb -s '<tv-endpoint>' shell cat /sdcard/Android/data/com.gathin.tvhome/files/theme_revision
adb -s '<tv-endpoint>' shell sha256sum /sdcard/Android/data/com.gathin.tvhome/files/config.json
```

Verificar `idle_art_mode_seconds`, `idle_art_mode_opacity`, `wallpaper_rotation_minutes`,
`wallpaper_dim`, `wallpaper`, `reduced_motion` e as opções de HOME/helpers. Chave ausente
exige consultar o default do **APK ativo**; não usar o default do checkout por suposição.
`theme_revision` pode estar legitimamente ausente. `Permission denied` não significa
configuração inexistente e não autoriza outro método de acesso.

### C. Preferências e cópia interna — P/C, provável indisponibilidade em release

Só propor estas leituras se o pacote ativo for debuggable e `run-as` estiver autorizado
pelo sistema; assinatura com debug key não implica uma aplicação debuggable.

```sh
adb -s '<tv-endpoint>' shell run-as com.gathin.tvhome cat shared_prefs/launcher_settings.xml
adb -s '<tv-endpoint>' shell run-as com.gathin.tvhome cat files/config.json
```

Não escrever preferências. Se recusado, manter DESCONHECIDO, sem mudar build/permissões.
Isso não cria automaticamente um requisito de reinstalação nem bloqueia toda a preparação:
documentar as opções visíveis/relatadas e acordar como repô-las antes de uma futura escrita.
Na R2, a tentativa normal abaixo foi recusada; `run-as` não foi tentado porque o APK
não é debuggable. Nenhum acesso alternativo com privilégios foi usado.

```sh
adb -s '<tv-endpoint>' shell cat /data/user/0/com.gathin.tvhome/shared_prefs/launcher_settings.xml
```

Recolher `columns`, `animations`, `advanced_effects`, `rows_below_dock`, `wallpaper_choice`,
`dock_opacity`, `theme_signature` e `settings_revision`. Os seis primeiros podem ser
apagados por [Config.syncThemeChoices](../launcher/app/src/main/java/com/gathin/tvhome/Config.java#L653).
A cópia interna pode ser usada antes da montagem do armazenamento externo:
[readOverlay](../launcher/app/src/main/java/com/gathin/tvhome/Config.java#L699).

**Limite de prova:** `Config.load` é chamado na criação da Activity e guarda valores em
memória ([MainActivity](../launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java#L381)).
Ler JSON/XML e APK identifica o estado persistido acessível, mas não prova que a Activity
atual carregou esses mesmos bytes nem que as preferências em disco já refletem a memória.
Distinguir «persistido confirmado», «comportamento observado» e eventual incerteza em
memória. Cruzar os ficheiros acessíveis, o launcher identificado e a observação atual
da pessoa; não exigir instrumentação ou reinício para uma certeza absoluta. Se houver
contradição concreta entre ficheiros e comportamento, esclarecê-la antes da mudança.

### D. Coleção — P/I/C

```sh
adb -s '<tv-endpoint>' shell ls -l /sdcard/Android/data/com.gathin.tvhome/files/wallpapers
adb -s '<tv-endpoint>' shell 'sha256sum /sdcard/Android/data/com.gathin.tvhome/files/wallpapers/reimagui-*.jpg'
adb -s '<tv-endpoint>' shell sha256sum /sdcard/Android/data/com.gathin.tvhome/files/wallpapers/captions.txt
```

Primeiro rever a listagem. Comparar nomes e hashes dos 90 ficheiros, não só a contagem;
identificar também imagens extra reconhecidas pelo [loader](../launcher/app/src/main/java/com/gathin/tvhome/WallpaperRotation.java#L21).
Comparar legenda com o manifesto privado R1. A leitura completa representa cerca de
34 MB de I/O; escolher uma janela autorizada sem interferir com reprodução. Se houver
recusa ou nomes inesperados, parar e registar a divergência; não limpar nem substituir.
Ter 90 imagens não prova rotação: uma preferência pode selecionar uma única pintura.

### E. Possibilidade de recuperação de HOME — L/P

```sh
adb -s '<tv-endpoint>' shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s '<tv-endpoint>' shell cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s '<tv-endpoint>' shell dumpsys activity activities
adb -s '<tv-endpoint>' shell pm list packages --user 0 -d
adb -s '<tv-endpoint>' shell settings get secure enabled_accessibility_services
adb -s '<tv-endpoint>' shell settings get secure accessibility_enabled
```

Cruzar HOME resolvido, candidatos enabled, estado do launcher, atividade focada e helpers.
Se outro app estiver à frente, registar isso; não trazer Home para a frente. Para um
candidato stock identificado por evidência, propor ainda (L/P):

```sh
adb -s '<tv-endpoint>' shell dumpsys package '<identified-stock-home-package>'
adb -s '<tv-endpoint>' shell pm path --user 0 '<identified-stock-home-package>'
```

Não presumir o pacote stock pelo fabricante. Estas leituras mostram caminhos possíveis,
mas não certificam que HOME arrancará após falha, que um APK poderá ser reinstalado ou
que os botões físicos funcionam. Não executar reboot, teste de falha ou navegação.

### F. Comandos excluídos e critério de paragem

`content read` não integra o conjunto estrito: pode iniciar o provider e chamar
[Config.dir](../launcher/app/src/main/java/com/gathin/tvhome/Config.java#L373); o próprio
[ThemeProvider.file](../launcher/app/src/main/java/com/gathin/tvhome/ThemeProvider.java#L24)
cria diretórios em falta mesmo na via de leitura. O mesmo se aplica a iniciar as
definições do launcher, que chamam `Config.load` e podem escrever cache/preferências.
Se a leitura direta estiver vedada no Android 14, a configuração pode continuar
DESCONHECIDA; um método alternativo exige revisão e autorização próprias.

Excluir `theme.sh`, escrita/deleção via provider, instalação, permissões, root, alterações
de pacotes/settings, `force-stop`, `set-home-activity`, início de Activity/serviço,
injeção de teclas, emparelhamento e reboot. Não publicar outputs completos de package,
activity, settings, JSON ou XML: contêm informação privada; publicar apenas conclusões redigidas.

## 3. Preparação da reversão 30% → 10%, sem implementação

1. Recolher a configuração efetivamente usada como base e conservar os bytes exatos,
   SHA-256, data e versão instalada num destino privado novo; nunca substituir a R1.
   Se a leitura direta for recusada, apresentar uma alternativa dirigida e pedir aprovação
   para o acesso necessário. Não adotar a candidata local como se viesse da TV.
2. Identificar os seis overrides que podem ser limpos, por leitura quando permitida ou
   registo das escolhas atuais visíveis/relatadas. Definir com a pessoa como repô-las
   se diferirem do JSON; não criar outro APK para inspecionar preferências.
3. Numa futura fase autorizada, preparar um candidato com diferença semântica apenas em
   `idle_art_mode_opacity: 30→10`; preservar 15s, rotação de 10 min, dim e todas as outras
   chaves conforme a base confirmada. Não normalizar nem substituir a coleção.
4. Reversão futura: repor os bytes exatos da base recolhida e as escolhas afetadas,
   com leitura de retorno e comparação de hash. A configuração original da R1 não tem
   Idle Art e não é a reversão adequada por simples suposição.
5. Antes de uma futura escrita, acordar paragem/reversão se surgirem ilegibilidade,
   foco inconsistente, falha de reposição a 100% ou alteração inesperada das opções.
   Recuperação de HOME segue a R1 se necessário; não reinstalar APKs por rotina.

Na recolha R2, foi criada uma cópia exata para reversão. Depois, o candidato de 10% foi
preparado e testado conforme as secções 6–8: primeira tentativa revertida para 30%,
retomada concluída e aprovada fisicamente com 10% ativo.
A aplicação é dirigida apenas à configuração,
nunca por `theme.sh`, reset do tema ou substituição de APK.

## 4. Critérios para avançar

- **GO R2 documental:** 15/15 casos classificados a partir de logs, roteiro e riscos preparados.
- **Reexecução seletiva:** apenas se for preciso fechar cobertura e após resolver as
  limitações de preparação; nenhum novo teste ou alteração de código necessário agora.
- **Leitura da TV:** autorizada para R2, incluindo ligação ao alvo confirmado. Não autoriza escrita.
- **Segunda cópia física:** recomendada; ausência não bloqueia, conforme decisão do utilizador.
- **Base confirmada:** pacote/versão/hash do launcher e configuração persistida recolhidos.
- **Preparação cumprida:** reversão JSON disponível e seis escolhas visuais registadas
  pelo utilizador antes da escrita; preferências internas continuam inacessíveis.
- **Teste concluído:** JSON 10% e recarga verificados; utilizador confirmou atenuação após
  15s, recuperação pelo comando, navegação e acesso às pinturas/aplicações.
- Hash do APK, correspondência por commit e certeza absoluta sobre memória não são
  bloqueadores autónomos; resolver apenas divergências concretas relevantes à mudança.
- Preservar os backups e o [plano R1](REIMAGUI_R1_RECOVERY.md); nenhuma fase seguinte iniciada.

## 5. Resultados da recolha passiva de 2026-10-25

Resultados publicados no commit acima com apenas os três documentos aprovados; ficheiros
remotos verificados por SHA Git. A recolha ocorreu entre 20:21 e 20:25 UTC, reutilizando
a única ligação ADB já autorizada. Não foi preciso executar `adb connect`.
As consultas de identidade usaram `getprop` apenas para modelo, versão Android, SDK e
build, além do utilizador atual. O modelo corresponde ao alvo, Android 14/API 34, utilizador 0.

| Verificação | Resultado | Evidência / limite |
|---|---|---|
| Sistema e HOME | CONFIRMADO | HOME resolve para `com.gathin.tvhome/.MainActivity`; outra aplicação estava à frente, sem navegação |
| Launcher instalado | CONFIRMADO | Versão 2.7/código 8, um APK; SHA-256 igual ao build preservado no manifesto privado R1; diferente do backup APK anterior |
| Configuração persistida | CONFIRMADO | Leitura direta, JSON válido: 15s/30%, rotação 10 min, wallpaper ligado e dim zero; hash remoto igual aos bytes guardados |
| Preferências relevantes | ACESSO RECUSADO | `Permission denied` na leitura normal; release não debuggable; sem root, grants ou contorno; `theme_revision` ausente |
| Pinturas e legendas | PRESENÇA CONFIRMADA | 90 nomes/tamanhos esperados, zero imagens extra; hash das legendas igual à R1; não foi repetido hash do conteúdo dos 90 JPEGs |
| Reversão JSON | PREPARADA NO MAC | Cópia dos bytes exatos recolhidos; configuração com hash estável no início/fim; preferências não incluídas |

São 5 de 6 verificações confirmadas (**83%**, peso igual); a sexta foi encerrada com a
limitação indicada, conforme autorização. Preparação e classificação permanecem concluídas.
Os valores persistidos foram identificados; não houve ensaio visual de fade nem leitura
direta de campos em memória. Não foi forçado o retorno ao Home para observar o idle.

O ficheiro recolhido coincide com a candidata local R1 por hash. Esta coincidência
agora é evidência da TV, não uma suposição de instalação baseada no checkout.
A versão Android genérica não altera a designação histórica Reimagui v1.4.6, mas o
hash identifica o binário concreto sem certificar um commit ausente nos metadados VCS.

As consultas de recuperação identificaram o launcher e o FallbackHome do sistema como
candidatos; não demonstraram um segundo launcher stock operacional. FallbackHome não
é uma alternativa de recuperação comprovada. Não houve mudança de HOME nem teste de falha.
Os serviços de acessibilidade estavam desligados e nenhum helper do launcher constava
da lista consultada; não houve alteração desses settings.

Todos os outputs, endpoint, hashes individuais, configuração e manifesto de evidências
ficam numa pasta privada nova, ignorada pelo Git, com permissões 0700/0600. A cópia de
reversão contém só o JSON; não garante restaurar overrides apagados pela mudança de tema.
Antes de um teste futuro, explicitar essa limitação e acordar verificação/reposição
de colunas, linhas, animações, efeitos, escolha de wallpaper e opacidade do dock.

Nenhuma leitura adicional será usada para contornar a recusa de preferências. O tratamento
das seis escolhas e o teste posterior estão nas secções 6–8; não executar `theme.sh`,
reinstalação ou limpeza de dados como atalho. O teste está concluído; aguardar a próxima fase.

## 6. Candidato e procedimento de teste — tentativa e reversão na secção 7

### Artefactos preparados e prova da alteração única

- Origem: bytes exatos da configuração recolhida na R2, validados contra o hash remoto.
- Candidato e original guardados em pasta privada nova, fora dos ficheiros versionados;
  ambos têm 819 bytes. A modificação altera apenas um byte, de `3` para `1` no valor 30.
- JSON válido, mesmas chaves, ordem, espaços e quebras de linha; diferença semântica
  exclusivamente em `idle_art_mode_opacity`. Preservados 15s, rotação 10 min, dim zero
  e todas as outras configurações. Nenhum ficheiro da R1/R2 foi sobrescrito.
- Hashes do original/candidato, offset e verificação de origem guardados no manifesto
  privado; original e reversão são idênticos. Não repetir auditorias ou testes anteriores.

```diff
-  "idle_art_mode_opacity": 30
+  "idle_art_mode_opacity": 10
```

### Seis escolhas que a assinatura do tema pode repor

São exatamente as removidas por [syncThemeChoices](../launcher/app/src/main/java/com/gathin/tvhome/Config.java#L666).
As preferências internas continuam inacessíveis; os valores do JSON não provam os overrides atuais.

| Chave | Anotar antes de escrever | Como restaurar manualmente, só se mudar |
|---|---|---|
| `columns` | Número selecionado | Definições do launcher → Colunas → mesmo número |
| `rows_below_dock` | 0, 1, 2 ou 3 linhas | Linhas abaixo do dock → mesmo número |
| `animations` | On/Off | Animações → estado anotado; fazer antes dos efeitos |
| `advanced_effects` | On/Off efetivo | Efeitos avançados → estado anotado, depois de Animações |
| `wallpaper_choice` | Theme default, ficheiro ou None | Wallpaper → Theme default, ficheiro anotado ou None |
| `dock_opacity` | Botão selecionado ou seguir tema | Dock band opacity → mesmo botão; se seguia tema, não escolher aproximação |

Procedimento curto para a pessoa, antes de qualquer escrita: no Home, manter OK na
engrenagem e escolher Definições do launcher; anotar os seis valores numa linha ou
fotografia privada e sair com Back sem alterar opções. Após o teste ou reversão,
abrir o mesmo menu e repor apenas diferenças; voltar com Back.
O seletor de wallpaper tem uma opção própria Theme default, sem reset global.

**Dock:** a base JSON tem 60%, mas a UI oferece só 100/70/50/30
([opções](../launcher/app/src/main/java/com/gathin/tvhome/LauncherSettingsActivity.java#L28)).
Se seguia o tema, 60% é preservado no JSON; não selecionar 50/70 por aproximação.
**Não usar Follow theme/reset global:** também limpa Open on start, que não pertence
a estas seis chaves ([ação global](../launcher/app/src/main/java/com/gathin/tvhome/LauncherSettingsActivity.java#L131)).
O objetivo é repor escolhas efetivas; não se promete reconstruir o XML privado original.

### Aplicação dirigida proposta — requer aprovação específica

1. Guardar as seis escolhas visíveis, fechar menus e combinar o teste físico com a pessoa.
   Validar hashes locais do candidato e original. Antes da escrita, confirmar que o hash
   remoto de config.json ainda é o da R2. Se diferir, parar: não sobrescrever uma base nova.
2. Enviar os 819 bytes diretamente ao provider, sem pasta de tema ou ficheiro temporário na TV:

```sh
adb -s '<tv-endpoint>' shell -T 'content write --uri content://com.gathin.tvhome.theme/config.json' < '<private-candidate-file>'
```

3. Ler de volta config.json e calcular SHA-256. Só prosseguir se os bytes forem exatamente
   os do candidato e o JSON válido. Verificar presença dos 90 nomes/tamanhos e hash das
   legendas; não repetir o hash integral dos 90 JPEGs. Não escrever theme_revision nem imagens.
4. Recriar **apenas a atividade Home**, para reler o JSON. A escrita não recarrega por si
   a configuração em memória. Comando corrigido após a recusa da secção 7, executado
   uma vez na retomada autorizada da secção 8; sem force-stop, reboot ou mudança da HOME:

```sh
adb -s '<tv-endpoint>' shell am start -W -f 0x10008000 -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity
```

Esta ação substitui a tarefa/atividade do launcher; não limpa os dados da aplicação.
É uma operação de ciclo de vida; na retomada autorizada produziu uma nova Activity focada.
Confirmar atividade focada e UI pronta com prazo de 10s; a resposta de start isolada não basta.
Não recorrer automaticamente a force-stop ou a outro comando se o arranque for recusado.

5. Teste físico curto: deixar Home 15s sem interação, observar o fade da navegação para
   10%, pressionar uma direção no comando real e confirmar retorno imediato a 100% com
   navegação normal. Abrir uma aplicação habitual e regressar a Home. Comparar as seis
   escolhas e repor diferenças se necessário. Relógio e pintura não devem ser avaliados
   como navegação atenuada. Não repetir auditoria completa de vídeo/HDMI/Bluetooth sem sintoma.

### Reversão e critérios de paragem

- Reverter se a pessoa rejeitar legibilidade, se o foco/primeira tecla falhar, houver
  alterações inesperadas, Home não ficar pronto ou a leitura de retorno não coincidir.
- O provider escreve com truncagem, não atomicamente. Falha de transferência exige
  leitura de estado e, se necessário, uma tentativa de reposição dos bytes originais.
  Não recriar Home sobre um JSON incompleto e não fazer tentativas de escrita em ciclo.
- Reposição dirigida, sujeita à mesma autorização de teste/reversão:

```sh
adb -s '<tv-endpoint>' shell -T 'content write --uri content://com.gathin.tvhome.theme/config.json' < '<private-original-file>'
```

- Confirmar hash original, recriar a atividade pelo comando acima e verificar 30% após
  15s; repor manualmente só as escolhas divergentes. Se a escrita/recriação for recusada,
  parar e comunicar, sem permissões adicionais, reinstalação, limpeza de dados ou scripts de tema.
- Teste, recriação controlada de Home e reversão autorizados em 2026-10-25. Antes da escrita,
  o utilizador exige confirmação/registo das seis escolhas e TV disponível para observação.
  O utilizador confirmou as seis escolhas, TV e comando disponíveis antes da aplicação.
- Preflight de 20:55 UTC confirmou alvo/utilizador e hash da TV igual ao original R2.
  Candidato e original continuam com 819 bytes e uma única diferença de um byte/chave.
  O hash foi novamente confirmado imediatamente antes da escrita; resultado abaixo.

## 7. Tentativa autorizada, falha de sintaxe e reversão verificada

- Antes de escrever, o utilizador confirmou: 6 colunas, 1 linha, animações e efeitos
  ligados, wallpaper no padrão do tema e dock a seguir tema (60%, sem override).
- Hash remoto confirmado igual ao original R2 imediatamente antes de escrever. Foram
  escritos só os 819 bytes do candidato em config.json; leitura completa de retorno,
  hash, tamanho e JSON válidos, com diferença semântica exclusiva em opacidade 30→10.
- A pessoa foi avisada antes da recarga e orientada sobre 15s de inatividade e uma tecla
  direcional real para despertar; não foi usada injeção de teclas.
- A recarga proposta pelo agente falhou com saída 255:
  `java.lang.IllegalArgumentException: Unknown option: --activity-new-task`.
  A exceção ocorreu na análise dos argumentos, antes do arranque da atividade. Foi um
  erro na preparação do comando; não é evidência de falha do launcher ou de integridade.
- Foi executada uma única reversão dirigida. Leitura completa e SHA confirmaram os
  bytes originais (819), JSON válido e opacidade 30. Não houve nova tentativa de arranque.
- Após a reversão: 90 nomes/tamanhos esperados, zero divergências e hash das legendas
  igual à R1; sem escrita em imagens/APKs, force-stop, reboot ou alteração da HOME.
- Outra atividade estava à frente na consulta final; não foi forçado o retorno a Home.
  Depois, o utilizador confirmou Home normal e autorizou a única retomada da secção 8.
- **Conclusão da tentativa: 80% (4/5 marcos)** — candidato, preflight, escolhas e escrita/
  integridade cumpridos; recarga e teste físico de 10% não concluídos. Estado persistido: 30%.
  Não declarar o teste visual aprovado ou a configuração 10% mantida.

Alternativa corrigida: o comando de recarga da secção 6 usa `-f 0x10008000`.
A ajuda real de `am` aceita `-f <FLAG>` e não contém `--activity-new-task`.
As constantes foram confirmadas no android.jar do SDK 34 local:
NEW_TASK=`0x10000000`, CLEAR_TASK=`0x00008000`, combinação=`0x10008000`.
Essa alternativa só foi executada depois de apresentada e expressamente autorizada,
com revalidação imediata do hash original, na retomada da secção 8.
Evidências completas e configurações permanecem privadas; documentação local sem novo push.

## 8. Única retomada corrigida — aplicada e aprovada fisicamente

- Utilizador confirmou Home normal após a reversão e autorizou uma única tentativa
  com `-f 0x10008000`; mantidas as seis escolhas registadas e os limites de escrita.
- Às 21:29 UTC, alvo/utilizador e hash original confirmados; logo a seguir, escrita
  exclusiva do candidato. Retorno completo: 819 bytes, JSON válido, SHA-256 coincidente,
  mesmas chaves e diferença semântica apenas `idle_art_mode_opacity: 30→10`.
- A pessoa foi avisada cinco segundos antes da recarga. O comando corrigido foi executado
  uma vez: retorno 0, Status ok, LaunchState WARM, sem aviso de reutilização da Activity.
  Comparação dos ActivityRecords confirmou uma nova MainActivity focada em cerca de 1,1s.
- Pós-recarga às 21:32 UTC: hash do candidato mantido, 90 nomes/tamanhos esperados,
  hash das legendas igual à R1 e HOME predefinido inalterado. Sem escrita em imagens/APKs,
  force-stop, reboot, mudança de serviços ou nova tentativa automática de arranque.
- Estado persistido **10%**; não houve reversão nesta retomada. O original exato de 30%
  permanece preparado para qualquer anomalia reportada, conforme autorização.
- **100% (5/5 marcos):** verificações preparatórias, integridade/recarga e confirmação
  física concluídas. O utilizador confirmou expressamente o sucesso do teste: transparência
  esperada após 15s, recuperação da visibilidade pelo comando, navegação funcional e
  pinturas/aplicações acessíveis. Não foram reportadas anomalias nem pedida reversão.
- Confirmação física é evidência do utilizador; as leituras técnicas foram feitas antes,
  sem alegar medição instrumental de alpha ou testes adicionais de HDMI/Bluetooth.
- Conforme decisão de encerramento, manter **10% ativo**, com 15s, rotação 10 min e dim
  zero preservados. Candidato e original exato de 30% mantidos privadamente, sem os mover
  ou sobrescrever. A reversão não foi usada nesta retomada aprovada.
- Pendentes, sem execução nesta fase: ensaios prolongados de rotação/desempenho e nova
  validação de vídeo, Live TV, HDMI, Bluetooth e reinício. Não condicionam o teste dirigido aprovado.
- Não houve injeção de teclas nem repetição da suíte. Nenhum comando ADB executado após
  a confirmação física. Utilizador autorizou commit/push apenas dos três documentos de
  continuidade; verificar a correspondência remota e aguardar a definição da próxima fase.
