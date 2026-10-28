# CleanYourTV / Reimagui — continuidade

- Atualização: 2026-10-28. Responsável: Antigravity.
- R3 local: branch `feat/reimagui-r3-clock-spotify`, commit base `afa0be4a`.
- [Plano R3](REIMAGUI_R3_PLAN.md) e [Contrato Spotify](SPOTIFY_CONTRACT.md) ativos.
- R3.1 e R3.2 concluídos e publicados em `afa0be4a`.
- R3.3a concluída localmente: bloqueadores de integração corrigidos com testes de orquestração.
- Zero internet no launcher: `com.gathin.tvhome` sem `INTERNET`; metadados e capa via IPC local.
- UI do relógio: capa 160dp, faixa, artista e atribuição Spotify com sombra, sem painel preto sólido, galeria/legendas ao fundo.
- Atribuição e link: `@string/spotify_attribution` e ligação à faixa (`spotify_uri`) via Intent ACTION_VIEW.
- Zero sobreposição: validado por teste de limites (360dp max text width, >520px de clearance vertical e >64dp horizontal).
- Zero Spotify na Home principal (`MainActivity`).
- Loopback RFC 8252: `http://127.0.0.1:8888/callback` responde com sucesso apenas após confirmação e troca de tokens; limites anti-slowloris (2KB linha, 8KB cabeçalhos, 3s timeout).
- Poller em segundo plano: `SpotifyPoller` moderado (15s tocando / 30s inativo; backoff de 1 hora em QUOTA_EXCEEDED julho/2026), ativo apenas com screensaver subscrito, renovação automática, 204/401/403/429 e `notifyChange()`.
- Capas de álbuns: download atómico via `.tmp`, validação estrita de HTTPS, proteção de path traversal e limpeza em 204.
- Idempotência: subscrições no `ClockSpotify` tornadas estritamente idempotentes (sem duplo unsubscribe no close).
- Entrada TV pelo comando: `SpotifyAuthActivity` com `LEANBACK_LAUNCHER`, banner 16:9 e campo de Client ID na TV, sem requerer ADB.
- Keystore: tokens encriptados com AES-256-GCM via `AndroidKeyStore`, nunca em texto claro.
- Provider seguro: validação de chamador `com.gathin.tvhome`, permissão signature, openFile somente leitura e bloqueio de path traversal.
- Certificados: launcher, spotify-bridge e APK instalado na TV partilham o mesmo SHA-256 (`cb4b94...ae5d`).
- Testes: 282 JVM no launcher, 35 JVM no bridge e 104 Python aprovados; zero erros de lint.
- APK release do launcher: 515.641 B (< 524.288 B limite, margem 8.647 B).
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
| Spotify Now Playing | R3.3a implementado e orquestrado localmente; testes JVM e lint OK; TV pendente |
| Componente bridge | `companion/spotify-bridge`: PKCE RFC 7636, Keystore AES-GCM, Loopback 127.0.0.1:8888, Provider e Poller |
| Segurança de credenciais | Sem Client Secret no código/APK/Git; tokens encriptados no Keystore e nunca expostos |
| Certificados | SHA-256 idêntico (`cb4b94...ae5d`) verificado no launcher, bridge e APK instalado na TV |

## Executado e alterado (R3.3a)

- `SpotifyAuthActivity`: interface de login Leanback (categoria `LEANBACK_LAUNCHER` e banner 16:9 na gaveta de apps da TV) com edição do Client ID via comando remoto/Gboard, conectada a `SpotifyAuthSession`, `SpotifyLoopbackServer`, `SpotifyApiClient` e `SpotifyTokenStore` com redirect `http://127.0.0.1:8888/callback`.
- `SpotifyPoller`: motor de atualização periódica moderada (15s ativa / 30s inativa; backoff de 3600s para QUOTA_EXCEEDED de julho/2026), acionado sob subscrição do screensaver, com renovação automática de tokens, tratamento de 204/401/403/429 e `notifyChange()`.
- Gestão de Capas: download atómico via `.tmp`, validação estrita de URL HTTPS, barreira contra path traversal e eliminação de cache antiga em 204.
- `SpotifyLoopbackServer`: resposta visual de sucesso emitida estritamente após confirmação de state e autenticação; introduzido timeout de 3s e teto de leitura (2KB linha, 8KB cabeçalhos).
- Idempotência IPC: `ClockSpotify` e `ClockScene` garantem subscrições idempotentes (eliminação do duplo unsubscribe).
- Atribuição e Ligação: visual com texto de atribuição Spotify (`@string/spotify_attribution`) e disparo de `Intent.ACTION_VIEW` com `spotify_uri` ao clicar no cartão.
- Zero Spotify na Home (`MainActivity`); zero permissões de rede no launcher.
- 282 testes JVM no launcher, 35 testes JVM no bridge e 104 testes Python aprovados; zero erros de lint.
- APK release do launcher: 515.641 B (< 524.288 B limite, margem 8.647 B).

## Riscos e bloqueadores

- APK da TV permanece intacto na versão de 10% de opacidade; nenhuma alteração foi enviada para o dispositivo.
- A reprodução via Web API depende de conta Spotify ativa e autorização no bridge.
- Margem do APK do launcher: 8.647 bytes restantes até aos 512 KB.

## GO / NO-GO e próximo passo

- **GO local:** Bloqueadores R3.3a corrigidos e orquestração comprovada com suíte completa de testes.
- **Próximo passo:** Apresentar relatório de correções, ficheiros alterados e resultados ao utilizador para revisão antes de testes físicos ou publicação.
