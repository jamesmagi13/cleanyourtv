# CleanYourTV / Reimagui — continuidade

- Atualização: 2026-10-25 20:20 UTC. Responsável: Codex.
- Repositório: `jamesmagi13/cleanyourtv`; branch `feat/reimagui-idle-art-v144`.
- Commit publicado de referência: `7687efed0569582119aa5f97f9860b1c41a3f06f`.
- Publicação destes três documentos e recolha passiva R2 autorizadas em 2026-10-25.
  Resultados da recolha terão nova revisão documental e aprovação antes de outro push.
- Etapa: **R2, preparação e classificação: 100% (4/4 entregas)** — classificação histórica
  dos 15 casos, plano de leitura prioritário, reversão e critérios/documentação revistos.
- A percentagem mede a preparação pedida; nenhuma validação na TV ou implementação foi iniciada.
- R1 local concluída (7/7); segunda cópia física recomendada, não bloqueadora.
- Decisão do utilizador em 2026-10-25: risco de perda do disco compreendido e aceite.

## Estado e evidências

| Item | Estado / evidência |
|---|---|
| Cópia privada R1 | Evidência histórica de 19:50 UTC: 118 ficheiros, 36 760 802 bytes; fora do checkout, permissões 0700/0600 |
| Integridade R1 | 92/92 conteúdos originais, 91/91 hashes históricos e 118/118 cópias confirmados; sem repetição desta auditoria em R2 |
| Pinturas | 90 JPEGs preservados; seleção local contém as mesmas 90 por hash; 90 legendas correspondem |
| Classificação R2 | 3 falhas de contrato da árvore local; 12 erros de preparação por sockets bloqueados; 15 tracebacks históricos consultados |
| Leitura passiva da TV | Procedimento proposto com comandos, riscos e limites; nenhum comando ADB executado |
| Segunda cópia física | RECOMENDADA, não bloqueadora; risco residual aceite pelo utilizador |
| HOME e v1.4.6 na TV | REPORTADO anteriormente; não revalidado em R1 |
| Configuração ativa da TV | DESCONHECIDA; candidata local: 15s/30%, rotação 10 min, `wallpaper_dim=0` |
| Opacidade 10% | PENDENTE; sem alteração da configuração nesta fase |
| Cinco listras | RESOLVIDO: características da pintura, conforme correção do utilizador |
| Spotify Now Playing | FUTURO; não implementado |

## Executado e alterado

- Lidos os documentos de continuidade, os logs históricos e os pontos relevantes do código.
- Confirmado no log R1: 874 testes em 90,820s; 3 falhas, 12 erros e 12 ignorados.
  Os 15 tracebacks finais estão completos, apesar da truncagem noutra parte da saída.
- F1–F3: a pasta privada ignorada pelo Git é enumerada pelos validadores de add-ons.
  As asserções falham legitimamente nessa árvore; não demonstram regressão do launcher.
- E1–E12: `PermissionError` em `socket.bind` na preparação, antes da funcionalidade avaliada.
  Limitação do ambiente confirmada; a cobertura desses caminhos continua por validar.
- Nenhuma correção de produção, novo teste ou skip justificado; eventual repetição futura
  limitada aos casos afetados, em condições adequadas e com autorização própria.
- Atualizados este relatório e o [anexo R2](REIMAGUI_R2_PREPARATION.md), com os 15 casos
  individuais, ordem de recolha e reversão 30→10. Comandos ADB apenas propostos.
  Validados links, diff e privacidade; classificação concluída preservada sem repetir testes.
- Nesta revisão de preparação: nenhuma suíte repetida ou comando ADB executado;
  autorização de publicação limitada aos três documentos, sem artefactos privados.
- Resultados documentais anteriores: 5 testes de links e 12 de AGENTS.md aprovados em R1.
- [Plano R1](REIMAGUI_R1_RECOVERY.md) atualizado só nos critérios, conforme a decisão do utilizador.
  Backups, 90 pinturas e legendas preservados; nenhuma cópia repetida ou ficheiro privado publicado.
- Auditoria anterior e limitações de proveniência: [anexo de 2026-10-25](CODEX_AUDIT_2026-10-25.md).

## Riscos e bloqueadores

- A cópia privada está no **mesmo disco**: protege contra alterações locais acidentais,
  mas não contra perda do volume. Risco aceite; segundo disco permanece recomendação.
- O APK de build local contém Idle Art; não há prova de identidade com o instalado na TV.
  O APK anterior não contém Idle Art e não é uma reversão certificada.
- O backup original de `config.json` não contém Idle Art; a candidata 15s/30% é distinta.
- Não usar o aplicador de tema completo para alterar opacidade: limpa as pinturas.
- Mudar `config.json` pode invalidar preferências da TV; a escrita pelo provider trunca
  o ficheiro. Requer cópia ativa exata, plano de reversão e leitura de retorno.
- Até `content read` pode criar diretórios pelo provider; excluído do roteiro estrito.
- Acesso direto pode ser recusado no Android 14 e `run-as` pode estar indisponível em release.
  Registar limites e propor alternativa dirigida; não instalar outro APK para obter acesso.
- Ler ficheiros não prova por si só o estado em memória. Cruzar configuração recolhida,
  launcher e contexto observado; não exigir instrumentação ou reinício só para eliminar essa dúvida.
- Preservar HOME, acesso ADB, vídeo, Live TV, HDMI e Bluetooth antes de intervenções.
- As listras deixam de ser hipótese de defeito gráfico; não há investigação pendente.

## GO / NO-GO e próximo passo

- **GO R2 documental:** classificação 15/15 e plano passivo preparados; nenhuma validação na TV.
- **GO para leitura R2:** ligação e consultas autorizadas; recolha ainda não iniciada nesta revisão.
- **NO-GO para 10% por agora:** falta recolher a configuração ativa recuperável, confirmar
  o launcher instalado, tratar eventuais preferências afetadas e obter autorização de implementação.
- Segunda cópia física e correspondência exata APK/commit não são requisitos bloqueadores.
- Próximo passo: executar a recolha de leitura prioritária já autorizada no anexo R2;
  depois preparar a reversão com os bytes efetivamente recolhidos, sem alterar a TV nesta etapa.
- Autorização pendente: uso de volume externo, alterações na TV, fase 30→10 e novo
  push dos resultados após esta publicação de preparação. Nenhuma alteração na TV autorizada.

## Rotina de sincronização

Ler a versão publicada e o commit antes de continuar; atualizar este relatório após
cada etapa relevante, preservando decisões essenciais e ligando anexos quando necessário.
Rever diff e privacidade; só fazer push após aprovação explícita para os ficheiros e destino.
Uma edição local não publicada não está disponível ao outro agente pelo GitHub.
