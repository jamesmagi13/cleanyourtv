# CleanYourTV / Reimagui — continuidade

- Atualização: 2026-10-31. Responsável: Antigravity.
- R3 local: branch `feat/reimagui-r3-clock-spotify`, commit base `a70dcfd4`.
- [Plano R3](REIMAGUI_R3_PLAN.md) e [Contrato Spotify](SPOTIFY_CONTRACT.md) ativos.
- R3.1, R3.2, R3.3a, R3.3b e R3.3c concluídos; correção dirigida de auditoria aplicada.
- Zero internet no launcher: `com.gathin.tvhome` sem `INTERNET`; metadados e capa via IPC local.
- UI do relógio: capa 160dp, faixa, artista e atribuição Spotify com sombra, sem painel preto sólido, galeria/legendas ao fundo.
- Atribuição e link: `@string/spotify_attribution` e ligação à faixa (`spotify_uri`) via Intent ACTION_VIEW.
- Zero sobreposição: validado por teste de limites (360dp max text width, >520px de clearance vertical e >64dp horizontal).
- Zero Spotify na Home principal (`MainActivity`).
- Versionamento partilhado entre instâncias: `SpotifyTokenStore` com `PROCESS_LOCK` e `GLOBAL_SESSION_VERSION` atómico coordena leitura, invalidação e `saveTokensIfVersion` entre todas as instâncias do mesmo processo.
- Ausência de deadlock comprovada: hierarquia estrita (`SUBSCRIPTION_LOCK` -> `poller` -> `Provider.class`); `handleSubscription` e DeathRecipient reconciliam o poller de forma serializada com o `subscriberCount` atual.
- Protocolo de geração estendido a erros: `handleErrorLocked` só atua se `running && generation == currentGen && sessionVersion == expectedVersion`; respostas de erro tardias ou após `stop()` (incluindo 3º erro e `invalid_grant`) são descartadas a 0 sem alterar o Provider.
- Keystore: tokens encriptados com AES-256-GCM via `AndroidKeyStore`, nunca em texto claro.
- Provider seguro: validação de chamador `com.gathin.tvhome`, permissão signature, openFile somente leitura e bloqueio de path traversal.
- Certificados: launcher, spotify-bridge e APK instalado na TV partilham o mesmo SHA-256 (`cb4b94...ae5d`).
- Testes: 282 JVM no launcher, 55 JVM no bridge e zero erros de lint.
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
| Spotify Now Playing | R3.3c correção dirigida e auditoria de concorrência concluídas; 55 testes no bridge e 282 no launcher OK |
| Componente bridge | `companion/spotify-bridge`: PKCE RFC 7636, Keystore AES-GCM, Loopback 127.0.0.1:8888, Provider e Poller |
| Segurança de credenciais | Sem Client Secret no código/APK/Git; tokens encriptados no Keystore e nunca expostos |
| Certificados | SHA-256 idêntico (`cb4b94...ae5d`) verificado no launcher, bridge e APK instalado na TV |

## Executado e alterado (R3.3c Correção Dirigida)

- `SpotifyTokenStore`: `GLOBAL_SESSION_VERSION` e `PROCESS_LOCK` partilhados em processo garantem que `clear()` numa instância invalida atomicamente qualquer refresh ou OAuth em trânsito noutra instância.
- `SpotifyNowPlayingProvider`: desacoplamento de bloqueios com `SUBSCRIPTION_LOCK`; eliminação de decisões `shouldStart`/`shouldStop` executadas fora do lock; reconciliação serializada (`reconcilePollerState`) sob `SUBSCRIPTION_LOCK` em `handleSubscription` e `DeathRecipient`.
- `SpotifyPoller`: protocolo de descarte estendido a erros; erro após `stop()` (incluindo 3º erro consecutivo ou `invalid_grant` antigo) é descartado sem mutação de estado nem notificação.
- `SpotifyR33cDirectedAuditTest`: 4 testes determinísticos dirigidos cobrindo instâncias partilhadas, stress concorrente de subscrição/publicação, descarte de 3º erro após `stop()` e reconciliação com `CountDownLatch` forçando inversão de transições e Binder death.
- 282 testes JVM no launcher, 55 testes JVM no bridge (debug e release) aprovados; zero erros de lint.
- APK release do launcher: 515.797 B (< 524.288 B limite, margem 8.491 B).

## Riscos e bloqueadores

- APK da TV permanece intacto na versão de 10% de opacidade; nenhuma alteração foi enviada para o dispositivo.
- A reprodução via Web API depende de conta Spotify ativa e autorização no bridge.
- Margem do APK do launcher: 8.491 bytes restantes até aos 512 KB.

## GO / NO-GO e próximo passo

- **GO local:** Correção final dirigida R3.3c concluída com 4 testes determinísticos no bridge e validação completa sem regressões.
- **Próximo passo:** Apresentar relatório, diff e resultados objetivos para verificação independente.
