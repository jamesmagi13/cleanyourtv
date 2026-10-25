# Reimagui — R2: classificação histórica e verificação passiva proposta

Atualização: 2026-10-25 20:27 UTC. Preparação publicada: `f4be4da2f7c2dbc737ca1d0ee34d586201c0f60d`.
[Continuidade](CODEX_STATUS.md) · [Recuperação R1](REIMAGUI_R1_RECOVERY.md).
Nenhum teste repetido em R2; recolha ADB autorizada e concluída apenas com leituras.
Backups e código de produção preservados. Resultados na secção 5, ainda sem publicação;
o utilizador exige nova aprovação antes do push desta revisão.
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

Na recolha R2, foi criada no Mac uma cópia exata para reversão, não um candidato de 10%.
Nenhuma configuração da TV foi alterada nem reversão executada. A eventual aplicação será dirigida apenas à configuração,
nunca por `theme.sh`, reset do tema ou substituição de APK.

## 4. Critérios para avançar

- **GO R2 documental:** 15/15 casos classificados a partir de logs, roteiro e riscos preparados.
- **Reexecução seletiva:** apenas se for preciso fechar cobertura e após resolver as
  limitações de preparação; nenhum novo teste ou alteração de código necessário agora.
- **Leitura da TV:** autorizada para R2, incluindo ligação ao alvo confirmado. Não autoriza escrita.
- **Segunda cópia física:** recomendada; ausência não bloqueia, conforme decisão do utilizador.
- **Base confirmada:** pacote/versão/hash do launcher e configuração persistida recolhidos.
- **GO técnico condicionado para planear o teste:** há reversão JSON; falta acordar o
  tratamento das escolhas internas não recolhidas, caso o teste provoque alterações nessas opções.
- **NO-GO execução 10% agora:** nenhuma autorização de escrita/aplicação foi dada.
- Hash do APK, correspondência por commit e certeza absoluta sobre memória não são
  bloqueadores autónomos; resolver apenas divergências concretas relevantes à mudança.
- Preservar os backups e o [plano R1](REIMAGUI_R1_RECOVERY.md); nenhuma implementação iniciada.

## 5. Resultados da recolha passiva de 2026-10-25

Preparação publicada no commit acima com apenas os três documentos aprovados; ficheiros
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

Nenhuma leitura adicional será usada para contornar a recusa de preferências. O próximo
passo é rever estes resultados e autorizar separadamente o eventual teste de uma chave;
não executar `theme.sh`, reinstalação ou limpeza de dados como atalho.
