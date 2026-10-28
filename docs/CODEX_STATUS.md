# CleanYourTV / Reimagui — continuidade

- Atualização: 2026-10-28. Responsável: Antigravity.
- R3 local: branch `feat/reimagui-r3-clock-spotify`, commit base `2044697c`.
- [Plano R3](REIMAGUI_R3_PLAN.md) e [Contrato Spotify](SPOTIFY_CONTRACT.md) ativos.
- R3.1a concluída e publicada em `2044697c`: galeria de pinturas no relógio funcional.
- R3.1b / R3.2 concluídos localmente com ciclo OAuth completo, segurança e testes simulados.
- Zero internet no launcher: `com.gathin.tvhome` sem `INTERNET`; metadados e capa via IPC local.
- UI do relógio: capa 160dp, faixa e artista com sombra, sem painel preto sólido, galeria/legendas ao fundo.
- Zero sobreposição: validado por teste de limites (360dp max text width, >520px de clearance vertical e >64dp horizontal).
- Zero Spotify na Home principal (`MainActivity`).
- Loopback RFC 8252: `http://127.0.0.1:8888/callback` acolhido por servidor local exclusivo em localhost.
- Keystore: tokens encriptados com AES-256-GCM via `AndroidKeyStore`, nunca em texto claro.
- Provider seguro: validação de chamador `com.gathin.tvhome`, permissão signature, openFile somente leitura e bloqueio de path traversal.
- CSRF e PKCE: validação estrita de `state`, consumo único de verifier e timeout de 5 min.
- Proteção Mock: `mockMode = false` por defeito em produção, evitando músicas fictícias acidentais.
- Certificados: launcher, spotify-bridge e APK instalado na TV partilham o mesmo SHA-256 (`cb4b94...ae5d`).
- Testes: 282 JVM no launcher, 24 JVM no bridge e 104 Python aprovados; zero erros de lint.
- APK release do launcher: 515.069 B (< 524.288 B limite, margem 9.219 B).
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
| Spotify Now Playing | R3.1b/R3.2 implementados e endurecidos localmente; testes JVM e lint OK; TV pendente |
| Componente bridge | `companion/spotify-bridge`: PKCE RFC 7636, Keystore AES-GCM, Loopback 127.0.0.1:8888 e Provider |
| Segurança de credenciais | Sem Client Secret no código/APK/Git; tokens encriptados no Keystore e nunca expostos |
| Certificados | SHA-256 idêntico (`cb4b94...ae5d`) verificado no launcher, bridge e APK instalado na TV |

## Executado e alterado

- Implementação funcional do cliente HTTPS (`SpotifyApiClient`): troca de authorization code, renovação de access token via refresh token e consultas a `/v1/me/player/currently-playing`.
- Ciclo OAuth completo testado de forma dirigida com respostas simuladas: sucesso, state inválido, callback duplicado, expiração de sessão, erro de rede, refresh token inválido, 204 No Content, 401 e 429 rate limit.
- Verificação de compatibilidade de certificados: apksigner confirmou que `launcher`, `companion/spotify-bridge` e `work/backup_apk/v143/tv_installed_base.apk` partilham a mesma assinatura SHA-256.
- Endurecida a segurança do `SpotifyNowPlayingProvider`: permissão `signature`, verificação de assinatura do chamador (`SignatureChecker`), rejeição de escrita em `openFile` e bloqueio de path traversal.
- Confirmado fallback natural do launcher: sem reprodução, sem login ou sem o bridge instalado, o relógio e a galeria funcionam normalmente sem erros.
- Corrigido redirect URI para loopback RFC 8252 (`http://127.0.0.1:8888/callback`) com `SpotifyLoopbackServer`.
- Encriptação de tokens via `AndroidKeyStore` (AES-256-GCM); sem Client Secret e sem tokens no Git.
- No launcher: largura de texto limitada a 360dp; `ClockLayoutBoundsTest` comprova matematicamente zero colisão em 720p, 1080p e 4K.
- 282 testes JVM no launcher, 24 testes JVM no bridge e 104 testes Python aprovados; zero erros de lint.
- APK release do launcher: 515.069 B (< 524.288 B limite, margem 9.219 B).

## Riscos e bloqueadores

- APK da TV permanece intacto na versão de 10% de opacidade; nenhuma alteração foi enviada para o dispositivo.
- A reprodução via Web API depende de conta Spotify ativa e autorização no bridge.
- Margem do APK do launcher: 9.219 bytes restantes até aos 512 KB.

## GO / NO-GO e próximo passo

- **GO local:** Implementação funcional da Web API, ciclo OAuth simulado, verificação de certificados e suite completa de testes aprovada.
- **Próximo passo:** Publicação em `feat/reimagui-r3-clock-spotify` para revisão independente do código antes de qualquer teste físico ou ativação na TV.
