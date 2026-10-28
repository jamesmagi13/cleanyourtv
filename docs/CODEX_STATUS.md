# CleanYourTV / Reimagui — continuidade

- Atualização: 2026-10-28. Responsável: Antigravity.
- R3 local: branch `feat/reimagui-r3-clock-spotify`, commit base `2ce06a27`.
- [Plano R3](REIMAGUI_R3_PLAN.md) e [Contrato Spotify](SPOTIFY_CONTRACT.md) ativos.
- R3.1, R3.2 e R3.3a concluídos e publicados em `2ce06a27`.
- R3.3b concluída localmente: 7 correções dirigidas pós-auditoria validadas com 8 novos testes obrigatórios.
- Zero internet no launcher: `com.gathin.tvhome` sem `INTERNET`; metadados e capa via IPC local.
- UI do relógio: capa 160dp, faixa, artista e atribuição Spotify com sombra, sem painel preto sólido, galeria/legendas ao fundo.
- Atribuição e link: `@string/spotify_attribution` e ligação à faixa (`spotify_uri`) via Intent ACTION_VIEW.
- Zero sobreposição: validado por teste de limites (360dp max text width, >520px de clearance vertical e >64dp horizontal).
- Zero Spotify na Home principal (`MainActivity`).
- Loopback RFC 8252: `http://127.0.0.1:8888/callback` cancelado no logout; limites anti-slowloris (2KB linha, 8KB cabeçalhos, 3s timeout).
- Poller resiliente: `SpotifyPoller` suporta múltiplos ciclos start/stop/start, descarta respostas em trânsito, expira metadados em falhas prolongadas (3 erros), aplica backoff seguro (30s) em falha de refresh pós-401 e 1h em QUOTA_EXCEEDED.
- Desconexão limpa: `disconnect()` cancela sessão OAuth, para loopback, limpa tokens e dispara `notifyChange()` para ocultar Spotify imediatamente.
- Morte anormal do launcher: `IBinder.DeathRecipient` via `EXTRA_TOKEN` deteta término inesperado pelo LMK e desliga o poller sem gasto de RAM.
- Keystore: tokens encriptados com AES-256-GCM via `AndroidKeyStore`, nunca em texto claro.
- Provider seguro: validação de chamador `com.gathin.tvhome`, permissão signature, openFile somente leitura e bloqueio de path traversal.
- Certificados: launcher, spotify-bridge e APK instalado na TV partilham o mesmo SHA-256 (`cb4b94...ae5d`).
- Testes: 282 JVM no launcher, 46 JVM no bridge e 104 Python aprovados; zero erros de lint.
- APK release do launcher: 515.797 B (< 524.288 B limite, margem 8.491 B).
- Nenhuma operação na TV, sem alteração de config ativa, sem commit e sem push.

## Estado e evidências

| Item | Estado / evidência |
|---|---|
| Cópia privada R1 | Evidência histórica: 118 ficheiros, 36 760 802 bytes; permissões 0700/0600 |
| Integridade R1 | 92/92 conteúdos, 91/91 hashes e 118 cópias confirmados |
| Pinturas | 90 JPEGs preservados; seleção local contém as mesmas 90 por hash; 90 legendas correspondem |
| Sistema / HOME | Alvo esperado, Android 14/API 34, utilizador 0; HOME resolve para com.gathin.tvhome/.MainActivity |
| Launcher instalado | Versão 2.7, código 8; SHA-256 coincide com o build R1 |
| Configuração ativa TV | 15s/10%, rotação 10 min e dim zero; integridade técnica e resultado físico aprovados |
| Reversão TV | Original exato R2 de 30% preservado privadamente em work/ |
| Galeria no relógio | R3.1a aprovada e publicada em `2044697c`; `clock_gallery=false` por defeito |
| Spotify Now Playing | R3.3b implementado e validado localmente; testes JVM e lint OK; TV pendente |
| Componente bridge | `companion/spotify-bridge`: PKCE RFC 7636, Keystore AES-GCM, Loopback 127.0.0.1:8888, Provider e Poller |
| Segurança de credenciais | Sem Client Secret no código/APK/Git; tokens encriptados no Keystore e nunca expostos |
| Certificados | SHA-256 idêntico (`cb4b94...ae5d`) verificado no launcher, bridge e APK instalado na TV |

## Executado e alterado (R3.3b)

- `SpotifyPoller`: múltiplos ciclos start/stop/start sem esgotar executor (thread timeout 10s e recriação sob demanda); descarte de respostas em trânsito com contador de geração; expiração de metadados após 3 falhas consecutivas; backoff de 30s em falha de refresh pós-401 sem loop de 1s; `pollImmediately` inerte sem relógio.
- `SpotifyAuthActivity`: `disconnect()` cancela sessão ativa, para loopback, descarta callbacks pendentes e dispara `notifyChange()` para ocultação imediata no launcher.
- Resiliência IPC: `IBinder.DeathRecipient` via `EXTRA_TOKEN` limpa subscritores e desativa poller quando o launcher é terminado inesperadamente pelo LMK da TV.
- Zero Spotify na Home (`MainActivity`); zero permissões de rede no launcher.
- 282 testes JVM no launcher, 46 testes JVM no bridge e 104 testes Python aprovados; zero erros de lint.
- APK release do launcher: 515.797 B (< 524.288 B limite, margem 8.491 B).

## Riscos e bloqueadores

- APK da TV permanece intacto na versão de 10% de opacidade; nenhuma alteração foi enviada para o dispositivo.
- A reprodução via Web API depende de conta Spotify ativa e autorização no bridge.
- Margem do APK do launcher: 8.491 bytes restantes até aos 512 KB.

## GO / NO-GO e próximo passo

- **GO local:** Todas as 7 correções dirigidas R3.3b e 8 testes obrigatórios implementados e aprovados.
- **Próximo passo:** Apresentar relatório de correções, resultados dos testes e diff completo ao utilizador para revisão antes de commit/push.
