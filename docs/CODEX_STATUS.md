# CleanYourTV / Reimagui — continuidade

- Atualização: 2026-10-25 21:41 UTC. Responsável: Codex.
- Repositório: `jamesmagi13/cleanyourtv`; branch `feat/reimagui-idle-art-v144`.
- Commit publicado dos resultados R2: `27dade59593166f7231728d2d2ebc9284ff37749`;
  os três documentos foram confirmados no GitHub. Esta atualização documental é apenas local.
- **Preparação do teste 10%: 100% (5/5)** — origem R2, mudança única, hashes/diff,
  reversão byte a byte e procedimento de aplicação/restauração manual definidos.
- **Fase de teste 10% CONCLUÍDA: 100% (5/5 marcos)** — candidato, preflight, escolhas,
  aplicação/integridade/recarga e validação física concluídos. Utilizador confirmou sucesso:
  atenuação após 15s, recuperação pelo comando, navegação e pinturas/aplicações acessíveis.
- **Preparação R2: 100% (4/4). Recolha encerrada com limitação: 83% (5/6 verificações)**.
  Confirmados sistema/HOME, APK, configuração, coleção e reversão JSON; preferências internas recusadas.
- A percentagem dá peso igual a estas seis verificações; não é progresso global nem prova visual do idle.
- R1 local concluída (7/7); segunda cópia física recomendada, não bloqueadora.
- Decisão do utilizador em 2026-10-25: risco de perda do disco compreendido e aceite.

## Estado e evidências

| Item | Estado / evidência |
|---|---|
| Cópia privada R1 | Evidência histórica de 19:50 UTC: 118 ficheiros, 36 760 802 bytes; fora do checkout, permissões 0700/0600 |
| Integridade R1 | 92/92 conteúdos originais, 91/91 hashes históricos e 118/118 cópias confirmados; sem repetição desta auditoria em R2 |
| Pinturas | 90 JPEGs preservados; seleção local contém as mesmas 90 por hash; 90 legendas correspondem |
| Classificação R2 | 15 casos já classificados; nenhuma suíte repetida nesta recolha |
| Leitura R2 | Ligação existente reutilizada; apenas consultas, sem comando mutativo na TV |
| Segunda cópia física | RECOMENDADA, não bloqueadora; risco residual aceite pelo utilizador |
| Sistema / HOME | Alvo esperado, Android 14/API 34, utilizador 0; HOME resolve para com.gathin.tvhome/.MainActivity |
| Launcher instalado | Versão 2.7, código 8; SHA-256 coincide exatamente com o APK de build preservado na R1 |
| Base recolhida R2 | JSON original: 15s/30%, rotação 10 min, `wallpaper_dim=0`; preservado para reversão |
| Preferências internas | `Permission denied`; pacote não debuggable; sem root, grants ou tentativa de contornar |
| Coleção na TV | 90 nomes e tamanhos coincidem com R1; legendas presentes e hash coincidente; JPEGs não re-hashados |
| Reversão preparada | Cópia privada dos bytes recolhidos; hash da configuração estável no início/fim; overrides não copiados |
| Configuração ativa | 15s/10%, rotação 10 min e dim zero; integridade técnica e resultado físico aprovados |
| Reversão disponível | Original exato R2 de 30% preservado privadamente; não executado na retomada aprovada |
| Seis escolhas | Utilizador confirmou 6 colunas, 1 linha, animações/efeitos ligados, wallpaper/dock a seguir tema (60%) |
| Cinco listras | RESOLVIDO: características da pintura, conforme correção do utilizador |
| Spotify Now Playing | FUTURO; não implementado |

## Executado e alterado

- Preparação publicada com apenas os três documentos aprovados; ficheiros e commit verificados no remoto.
- Recolha R2 feita entre 20:21 e 20:25 UTC. Dados brutos, endpoint, configuração e hashes
  individuais mantidos numa pasta privada nova, ignorada pelo Git, com permissões 0700/0600.
- Preparado manifesto privado de evidências R2 e cópia exata para reversão.
- Na recolha R2 outra aplicação estava à frente; na retomada atual Home foi recriado conforme autorização.
- Nenhuma imagem extra identificada; verificação de presença/tamanho não equivale a hash dos 90 JPEGs.
- A leitura normal de launcher_settings.xml foi recusada; essa parte foi encerrada sem privilégios adicionais.
- Base original R2 coincide com a candidata R1; a configuração atual difere só em opacidade 10%.
- Candidato criado a partir dos bytes exatos R2, mantendo formato, tamanho e todas as outras
  chaves. Apenas idle_art_mode_opacity mudou de 30 para 10; original preservado e hashes verificados.
- Acrescentados diff de uma linha e ficha privada para anotar as seis escolhas visíveis antes do teste.
- Hash remoto revalidado imediatamente antes da escrita: igual ao original R2.
  Candidato relido completo: 819 bytes, SHA correto, JSON válido e só uma opção diferente.
- Recarga recusada: `Unknown option: --activity-new-task`, opção inválida proposta pelo agente.
  Primeira tentativa revertida; o utilizador confirmou Home normal e autorizou uma única retomada.
- Retomada de 21:29–21:32 UTC: hash original novamente confirmado, candidato relido completo;
  recriação com `-f 0x10008000` executada uma vez, retorno 0/WARM, nova Activity e foco em 1,1s.
- Pós-recarga: hash do candidato, 90 nomes/tamanhos, legendas e HOME predefinido confirmados.
  Sem reversão nesta retomada. Utilizador confirmou o teste físico concluído com sucesso.
- Encerramento: manter o candidato de 10% como configuração ativa e o original de 30%
  como opção de reversão. Nenhuma nova operação ADB ou alteração na TV após a confirmação.
- Histórico dos testes preservado, sem reexecução: 874 testes em 90,820s;
  3 falhas, 12 erros e 12 ignorados.
  Os 15 tracebacks finais estão completos, apesar da truncagem noutra parte da saída.
- F1–F3: a pasta privada ignorada pelo Git é enumerada pelos validadores de add-ons.
  As asserções falham legitimamente nessa árvore; não demonstram regressão do launcher.
- E1–E12: `PermissionError` em `socket.bind` na preparação, antes da funcionalidade avaliada.
  Limitação do ambiente confirmada; a cobertura desses caminhos continua por validar.
- Nenhuma correção de produção, novo teste ou skip justificado; eventual repetição futura
  limitada aos casos afetados, em condições adequadas e com autorização própria.
- Atualizados este relatório, o [anexo R2](REIMAGUI_R2_PREPARATION.md) e o plano R1 com resultados.
  Primeira tentativa: candidato e reversão; retomada autorizada: uma escrita dirigida do candidato.
  Sem instalação, build, alteração de HOME/permissões, force-stop, reboot ou escrita nas imagens.
  Publicação do encerramento autorizada para apenas estes três documentos; verificar o remoto após push.
- Resultados documentais anteriores: 5 testes de links e 12 de AGENTS.md aprovados em R1.
- [Plano R1](REIMAGUI_R1_RECOVERY.md) atualizado com a decisão e os resultados de reversão JSON.
  Backups, 90 pinturas e legendas preservados; nenhuma cópia repetida ou ficheiro privado publicado.
- Auditoria anterior e limitações de proveniência: [anexo de 2026-10-25](CODEX_AUDIT_2026-10-25.md).

## Riscos e bloqueadores

- A cópia privada está no **mesmo disco**: protege contra alterações locais acidentais,
  mas não contra perda do volume. Risco aceite; segundo disco permanece recomendação.
- APK instalado identificado por hash como o build R1; versão Android genérica 2.7/código 8.
  A designação Reimagui v1.4.6 é histórica; hash não fornece um commit ausente nos metadados VCS.
- O backup original de `config.json` não contém Idle Art; a candidata 15s/30% é distinta.
- Não usar o aplicador de tema completo para alterar opacidade: limpa as pinturas.
- Mudar `config.json` pode invalidar seis overrides. Escolhas registadas antes do teste;
  nenhum problema reportado no resultado físico. Reversão exata do JSON disponível,
  sem promessa de reconstruir preferências internas que não puderam ser lidas.
- Até `content read` pode criar diretórios pelo provider; excluído do roteiro estrito.
- O acesso direto ao tema funcionou; acesso às preferências internas foi recusado.
  Não instalar outro APK para obter acesso. Nenhum helper do launcher foi identificado como ativo.
- Ler ficheiros não prova por si só o estado em memória. Cruzar configuração recolhida,
  launcher e contexto observado; não exigir instrumentação ou reinício só para eliminar essa dúvida.
- Preservar HOME, acesso ADB, vídeo, Live TV, HDMI e Bluetooth antes de intervenções.
- As listras deixam de ser hipótese de defeito gráfico; não há investigação pendente.
- Pendentes nesta fase: ensaios prolongados de rotação/desempenho e nova validação de
  vídeo/Live TV/HDMI/Bluetooth/reinício; não executados nem necessários para aprovar o teste dirigido.

## GO / NO-GO e próximo passo

- **GO / fase concluída:** JSON 10%, nova Home focada, coleção preservada e sucesso físico
  confirmado pelo utilizador. Manter 10%; original 30% disponível se for necessário reverter.
- Segunda cópia física e correspondência exata APK/commit não são requisitos bloqueadores.
- Próximo passo autorizado: publicar apenas estes três documentos e verificar correspondência no GitHub.
  Depois, aguardar definição da próxima fase. Volume externo e outras operações exigem autorização própria.

## Rotina de sincronização

Ler a versão publicada e o commit antes de continuar; atualizar este relatório após
cada etapa relevante, preservando decisões essenciais e ligando anexos quando necessário.
Rever diff e privacidade; só fazer push após aprovação explícita para os ficheiros e destino.
Uma edição local não publicada não está disponível ao outro agente pelo GitHub.
