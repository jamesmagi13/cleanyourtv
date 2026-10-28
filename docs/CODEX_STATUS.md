# CleanYourTV / Reimagui — continuidade

- Atualização: 2026-10-28. Responsável: Antigravity.
- R3 local: branch `feat/reimagui-r3-clock-spotify`, commit base `7ae32306`.
- [Plano R3](REIMAGUI_R3_PLAN.md) e [Contrato Spotify](SPOTIFY_CONTRACT.md) ativos.
- R3.1, R3.2, R3.3a e R3.3b concluídos e publicados em `7ae32306`.
- R3.3c concluída localmente: estabilização final do Spotify antes dos testes físicos.
- Zero internet no launcher: `com.gathin.tvhome` sem `INTERNET`; metadados e capa via IPC local.
- UI do relógio: capa 160dp, faixa, artista e atribuição Spotify com sombra, sem painel preto sólido, galeria/legendas ao fundo.
- Atribuição e link: `@string/spotify_attribution` e ligação à faixa (`spotify_uri`) via Intent ACTION_VIEW.
- Zero sobreposição: validado por teste de limites (360dp max text width, >520px de clearance vertical e >64dp horizontal).
- Zero Spotify na Home principal (`MainActivity`).
- Invalidação de sessão: `sessionVersion` atómica impede que callbacks ou refresh em trânsito ressuscitem credenciais após logout.
- Concorrência zero: sincronização atómica elimina a janela entre verificação de generation e publicação no provider (respostas canceladas nunca atualizam metadados nem capas).
- Preservação de backoff: `Retry-After` e `QUOTA_EXCEEDED` preservados entre stop/start do screensaver.
- Transição logout -> login: `onSessionRestored()` retoma polling automaticamente quando há subscrição ativa sem reabrir screensaver.
- Morte anormal do launcher: `IBinder.DeathRecipient` via `EXTRA_TOKEN` deteta término pelo LMK e desliga o poller sem gasto de RAM.
- Keystore: tokens encriptados com AES-256-GCM via `AndroidKeyStore`, nunca em texto claro.
- Provider seguro: validação de chamador `com.gathin.tvhome`, permissão signature, openFile somente leitura e bloqueio de path traversal.
- Certificados: launcher, spotify-bridge e APK instalado na TV partilham o mesmo SHA-256 (`cb4b94...ae5d`).
- Testes: 282 JVM no launcher, 51 JVM no bridge e 104 Python aprovados; zero erros de lint.
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
| Spotify Now Playing | R3.3c estabilizado localmente; 51 testes no bridge e 282 no launcher OK; TV pendente |
| Componente bridge | `companion/spotify-bridge`: PKCE RFC 7636, Keystore AES-GCM, Loopback 127.0.0.1:8888, Provider e Poller |
| Segurança de credenciais | Sem Client Secret no código/APK/Git; tokens encriptados no Keystore e nunca expostos |
| Certificados | SHA-256 idêntico (`cb4b94...ae5d`) verificado no launcher, bridge e APK instalado na TV |

## Executado e alterado (R3.3c)

- `TokenStorage` e `SpotifyTokenStore`: `sessionVersion` e `saveTokensIfVersion` garantem que respostas tardias de refresh ou OAuth não ressuscitem credenciais após logout.
- `SpotifyPoller`: eliminação da janela de concorrência com publicação atómica sob `synchronized (this)`; descarte garantido de metadados e capas em cancelamento.
- Backoff Preservado: `backoffUntilTimestampMs` retém prazos de `Retry-After` e `QUOTA_EXCEEDED` mesmo se o utilizador fechar e reabrir o screensaver.
- Transição Logout -> Login: `onSessionRestored()` e `onSessionTerminated()` gerem retoma segura do polling quando o relógio já está subscrito.
- Testes determinísticos: 5 novos testes dirigidos com latches de sincronização cobrindo concorrência, cancelamento, persistência e backoff.
- Zero Spotify na Home (`MainActivity`); zero permissões de rede no launcher.
- 282 testes JVM no launcher, 51 testes JVM no bridge e 104 testes Python aprovados; zero erros de lint.
- APK release do launcher: 515.797 B (< 524.288 B limite, margem 8.491 B).

## Riscos e bloqueadores

- APK da TV permanece intacto na versão de 10% de opacidade; nenhuma alteração foi enviada para o dispositivo.
- A reprodução via Web API depende de conta Spotify ativa e autorização no bridge.
- Margem do APK do launcher: 8.491 bytes restantes até aos 512 KB.

## GO / NO-GO e próximo passo

- **GO local:** Estabilização R3.3c concluída e comprovada com suíte rigorosa de 51 testes no bridge e 282 no launcher.
- **Próximo passo:** Apresentar relatório de estabilização, diff e decisão objetiva para testes físicos.
