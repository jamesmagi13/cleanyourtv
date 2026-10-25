# CleanYourTV / Reimagui — continuidade

- Atualização: 2026-10-25 19:54 UTC. Responsável: Codex.
- Repositório: `jamesmagi13/cleanyourtv`; branch `feat/reimagui-idle-art-v144`.
- Commit publicado de referência: `0b0b239048bf76548ea8ad4890772ea70ee4e551`.
- Publicação exclusiva deste relatório e do plano R1 autorizada em 2026-10-25;
  a revisão entregue é identificada pelo commit desta página no GitHub.
- Etapa: **R1 local concluída**, confirmada por nova verificação. Tarefas R1: **100% (7/7)** — inventário,
  hashes originais, cópia privada, hashes copiados, requisitos da segunda cópia, plano de
  recuperação e bloqueadores. A proteção em disco físico separado permanece pendente.
- A percentagem mede as sete entregas pedidas em R1; não mede a conclusão global do Reimagui.

## Estado verificado

| Item | Estado / evidência |
|---|---|
| Cópia privada R1 | 118 ficheiros, 36 760 802 bytes, subpasta privada fora do checkout; permissões 0700/0600 |
| Integridade | 92/92 conteúdos do manifesto original e 118/118 cópias confirmados por SHA-256 e tamanho; zero divergências |
| Pinturas | 90 JPEGs preservados; seleção local contém as mesmas 90 por hash; 90 legendas correspondem |
| Segunda cópia física | PENDENTE; as cópias atuais estão no mesmo volume do Mac |
| HOME e v1.4.6 na TV | REPORTADO anteriormente; não revalidado em R1 |
| Configuração ativa da TV | DESCONHECIDA; candidata local: 15s/30%, rotação 10 min, `wallpaper_dim=0` |
| Opacidade 10% | PENDENTE; sem alteração da configuração nesta fase |
| Cinco listras | RESOLVIDO: características da pintura, conforme correção do utilizador |
| Spotify Now Playing | FUTURO; não implementado |

## Executado e alterado

- Inventariados backup de arte, dois APKs, configurações, legendas e backups legados.
- Confirmados hashes originais; criada cópia privada exclusiva, com manifesto próprio.
- Verificação separada após a cópia: 118 fontes e destinos idênticos; nenhum ficheiro
  em falta ou extra. O manifesto privado guarda hashes individuais e caminhos relativos.
- Revalidação em 2026-10-25 às 19:50 UTC: 118 fontes e 118 cópias inalteradas, manifesto
  privado intacto, 92/92 hashes originais e 91/91 históricos; 90 pinturas e 90 legendas.
- Adicionado [plano de recuperação R1](REIMAGUI_R1_RECOVERY.md) com critérios GO/NO-GO.
- Ficheiros autorizados para publicação: este relatório e o plano R1; nenhum artefacto privado.
  Funções de produção afetadas: nenhuma.
- Resultados anteriores de R1, não repetidos nesta verificação: 5 testes de links Markdown
  e 12 de AGENTS.md passaram. Suíte completa: 874 testes, 3 falhas, 12 erros e 12 ignorados.
- Observações preliminares: falhas associadas à pasta Reimagui local e erros de sockets
  bloqueados pelo sandbox. Na próxima auditoria, classificar individualmente os 15 casos,
  separando limitações do ambiente de possíveis regressões reais; classificação PENDENTE.
- Sem contacto com a TV, ADB real, instalação, build, scripts de restauração, debloat,
  nem início da fase seguinte; autorização de commit/push limitada aos dois documentos.
- Auditoria anterior e limitações de proveniência: [anexo de 2026-10-25](CODEX_AUDIT_2026-10-25.md).

## Riscos e bloqueadores

- A cópia privada está no **mesmo disco**: protege contra alterações locais acidentais,
  mas não contra perda do volume. Segundo disco encriptado e verificado requer autorização.
- O APK de build local contém Idle Art; não há prova de identidade com o instalado na TV.
  O APK anterior não contém Idle Art e não é uma reversão certificada.
- O backup original de `config.json` não contém Idle Art; a candidata 15s/30% é distinta.
- Não usar o aplicador de tema completo para alterar opacidade: limpa as pinturas.
- Mudar `config.json` pode invalidar preferências da TV; a escrita pelo provider trunca
  o ficheiro. Requer cópia ativa exata, plano de reversão e leitura de retorno.
- Preservar HOME, acesso ADB, vídeo, Live TV, HDMI e Bluetooth antes de intervenções.
- As listras deixam de ser hipótese de defeito gráfico; não há investigação pendente.

## GO / NO-GO e próximo passo

- **GO R1 local:** inventário e verificação das cópias concluídos.
- **NO-GO para 10%:** falta segunda cópia em disco físico, identidade e configuração
  efetivas da TV, preferências e autorização específica para a fase seguinte.
- Próximo passo recomendado: aprovar um destino físico independente e verificar aí a
  segunda cópia; depois estabelecer a base ativa da TV numa sessão autorizada.
- Autorização pendente: uso de volume externo; contacto/alteração da TV; nova fase 30→10;
  novos envios além desta entrega documental. Nenhuma destas ações foi iniciada.

## Rotina de sincronização

Ler a versão publicada e o commit antes de continuar; atualizar este relatório após
cada etapa relevante, preservando decisões essenciais e ligando anexos quando necessário.
Rever diff e privacidade; só fazer push após aprovação explícita para os ficheiros e destino.
Uma edição local não publicada não está disponível ao outro agente pelo GitHub.
