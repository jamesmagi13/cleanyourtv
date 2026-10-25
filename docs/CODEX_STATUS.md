# CleanYourTV / Reimagui — continuidade

- Atualização: 2026-10-25 18:41 UTC. Responsável: Codex.
- Repositório: `jamesmagi13/cleanyourtv` (confirmado no origin local e no GitHub).
- Branch: `feat/reimagui-idle-art-v144`.
- Commit de referência: `2755c6a42f51e9cfd1c6258e0dd25471ad10be3b`.
- Esse commit é a base auditada; a versão deste relatório é identificada pelo histórico Git.
- Etapa: entrega documental inicial, autorizada pelo utilizador para commit e push em 2026-10-25.
- Preparação: 100% — 5 de 5 marcos: identidade, relatório, regra, validação e aprovação.
  A entrega remota é verificável pelo commit desta página no GitHub; o progresso global não é estimado.

## Estado e evidências

| Item | Estado | Evidência / limite |
|---|---|---|
| HOME e Reimagui v1.4.6 instalados; testes físicos A–D aprovados | REPORTADO pelo utilizador | Não revalidado nestas auditorias; sem contacto com a TV |
| 15s, opacidade 30%, wallpaper_dim=0 | CONFIRMADO na configuração candidata local | Também reportado anteriormente na TV; estado atual não consultado |
| Opacidade 10% | PENDENTE | Suportada pelo código; não aplicada nesta sessão |
| Rotação de 10 minutos | CONFIRMADO na configuração local | Verificação periódica enquanto Home está ativo; instalação não inferida |
| Cinco listras | RESOLVIDO | Utilizador esclareceu em 2026-10-25 que pertenciam à pintura; investigação encerrada |
| Coleção de 90 pinturas | CONFIRMADO no backup local | 92 ficheiros com hashes válidos, incluindo configuração e legendas |
| Correspondência código ↔ APK instalado | DESCONHECIDO | APK local com Idle Art; falta prova por hash da instalação reportada |
| Spotify Now Playing | FUTURO | Não implementado |

## Executado e alterado

- Auditorias anteriores: leitura de código, Git, metadados APK e backups; cálculo de hashes.
- Nesta etapa: criado este relatório e o [anexo de auditoria](CODEX_AUDIT_2026-10-25.md).
- Acrescentada ao [AGENTS.md](../AGENTS.md#reimagui-continuity-this-fork) uma regra de continuidade.
- Funções de produção alteradas: nenhuma. Configurações, APKs, debloat e backups não alterados.
- Nenhuma operação na TV, instalação ou build; o commit/push autorizado abrange só estes três documentos.

## Validação

- Antes do commit documental, identidade e branch confirmadas localmente e no GitHub em `2755c6a4`.
  Árvore inicialmente limpa; nenhuma alteração de produção incluída nesta entrega.
- Documentação: revisão do diff, links locais, limite de linhas e conteúdo público.
- Testes e builds não executados nesta etapa.
- Relatórios históricos locais: 26 testes IdleArtController e 10 KeyDispatchContract sem falhas.
  São evidência histórica JVM; não comprovam integração Android nem testes físicos atuais.

## Riscos e bloqueadores

- Rever a branch remota antes de cada publicação; novos envios exigem aprovação explícita.
- Os APKs locais não identificam o commit e partilham versão Android 2.7 / código 8.
- Cópias das pinturas estão no mesmo volume e checkout; ignorar no Git não protege contra perda.
- O backup original de configuração não contém Idle Art; não equivale ao estado 15s/30%.
- Aplicar um tema completo pode apagar pinturas; mudar config.json pode invalidar preferências.
- Prioridades permanentes: HOME, recuperação, ADB, vídeo, Live TV, HDMI e Bluetooth.

## Decisões e próximo passo

- GitHub será o registo de continuidade; instalação na TV exige evidência própria.
- As listras não são tratadas como defeito do launcher, conforme correção do utilizador.
- Não combinar a futura alteração 30→10 com true color, renderização ou substituição de APK.
- Próximo passo: o outro agente ler a versão publicada e indicar o commit consultado.
- Depois: confirmar a base ativa e proteção independente dos backups antes de alterar opacidade.

## Autorizações

- AUTORIZADO: commit/push inicial de AGENTS.md, CODEX_STATUS.md e CODEX_AUDIT_2026-10-25.md.
- PENDENTE: contacto/alteração da TV, instalação, alteração de configuração ou operação em backups.
- A aprovação documental não autoriza intervenções na TV nem publicação de artefactos privados.

## Procedimento de sincronização

1. No início, ler este relatório e o AGENTS.md na branch indicada; citar a versão/commit consultado.
2. Após cada etapa relevante autorizada, atualizar data, estado, evidências, riscos e próximo passo.
   Preservar decisões essenciais; detalhes extensos vão para anexos Markdown ligados aqui.
3. Rever privacidade e diff; obter aprovação explícita para os ficheiros e destino de cada push.
4. Antes de publicar, verificar a branch remota e os commits a enviar; não sobrescrever trabalho alheio.
5. Publicar apenas o âmbito aprovado e verificar no remoto o commit e conteúdo; comunicar o link.
6. O outro agente deve ler a versão publicada antes de continuar e confirmar o commit consultado.
   Uma cópia local não publicada não é visível pelo GitHub; este fluxo não cria acesso automático.
