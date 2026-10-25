# CleanYourTV / Reimagui — continuidade

- Atualização: 2026-10-25 20:40 UTC. Responsável: Codex.
- Repositório: `jamesmagi13/cleanyourtv`; branch `feat/reimagui-idle-art-v144`.
- Commit publicado de preparação: `f4be4da2f7c2dbc737ca1d0ee34d586201c0f60d`;
  os três documentos foram confirmados no GitHub. Publicação destes resultados autorizada em 2026-10-25.
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
| Configuração recolhida | JSON da TV: 15s/30%, rotação 10 min, `wallpaper_dim=0`; cópia exata e hash remoto coincidentes |
| Preferências internas | `Permission denied`; pacote não debuggable; sem root, grants ou tentativa de contornar |
| Coleção na TV | 90 nomes e tamanhos coincidem com R1; legendas presentes e hash coincidente; JPEGs não re-hashados |
| Reversão preparada | Cópia privada dos bytes recolhidos; hash da configuração estável no início/fim; overrides não copiados |
| Opacidade 10% | PENDENTE; sem alteração da configuração nesta fase |
| Cinco listras | RESOLVIDO: características da pintura, conforme correção do utilizador |
| Spotify Now Playing | FUTURO; não implementado |

## Executado e alterado

- Preparação publicada com apenas os três documentos aprovados; ficheiros e commit verificados no remoto.
- Recolha R2 feita entre 20:21 e 20:25 UTC. Dados brutos, endpoint, configuração e hashes
  individuais mantidos numa pasta privada nova, ignorada pelo Git, com permissões 0700/0600.
- Preparado manifesto privado de evidências e cópia exata para reversão; nenhum candidato de 10% criado.
- HOME é o launcher esperado, mas outra aplicação estava à frente; não houve navegação nem ensaio visual.
- Nenhuma imagem extra identificada; verificação de presença/tamanho não equivale a hash dos 90 JPEGs.
- A leitura normal de launcher_settings.xml foi recusada; essa parte foi encerrada sem privilégios adicionais.
- Configuração persistida coincide com a candidata R1; o valor 10% continua por aplicar.
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
  Nenhuma instalação, build, escrita na TV, alteração de HOME/permissões, reboot ou restauração.
  Esta entrega documental foi autorizada; a preparação local do candidato será registada a seguir.
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
- Mudar `config.json` pode invalidar seis overrides. Há reversão exata do JSON,
  mas não das preferências inacessíveis; acordar verificação/reposição das opções antes do teste.
- Até `content read` pode criar diretórios pelo provider; excluído do roteiro estrito.
- O acesso direto ao tema funcionou; acesso às preferências internas foi recusado.
  Não instalar outro APK para obter acesso. Nenhum helper do launcher foi identificado como ativo.
- Ler ficheiros não prova por si só o estado em memória. Cruzar configuração recolhida,
  launcher e contexto observado; não exigir instrumentação ou reinício só para eliminar essa dúvida.
- Preservar HOME, acesso ADB, vídeo, Live TV, HDMI e Bluetooth antes de intervenções.
- As listras deixam de ser hipótese de defeito gráfico; não há investigação pendente.

## GO / NO-GO e próximo passo

- **GO técnico condicionado para planear o teste 10%:** launcher e JSON identificados,
  reversão JSON pronta; explicitar o risco dos overrides não recolhidos e acordar como tratar alterações.
- **NO-GO execução agora:** falta autorização específica para aplicar 10%; nada foi aplicado.
- Segunda cópia física e correspondência exata APK/commit não são requisitos bloqueadores.
- Próximo passo: preparar o diff de uma chave a partir da cópia R2, sem aplicar;
  apresentar aplicação dirigida e reversão para autorização específica de escrita na TV.
- Autorização pendente: uso de volume externo, alterações na TV, fase 30→10 e novo
  novos envios posteriores a esta entrega documental. Nenhuma alteração na TV autorizada.

## Rotina de sincronização

Ler a versão publicada e o commit antes de continuar; atualizar este relatório após
cada etapa relevante, preservando decisões essenciais e ligando anexos quando necessário.
Rever diff e privacidade; só fazer push após aprovação explícita para os ficheiros e destino.
Uma edição local não publicada não está disponível ao outro agente pelo GitHub.
