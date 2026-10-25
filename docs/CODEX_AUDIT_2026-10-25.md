# Reimagui — evidências de continuidade, 2026-10-25

Resumo público das auditorias locais. Base: `2755c6a4`; [estado atual](CODEX_STATUS.md).
Nenhum contacto com a TV, alteração de produção, build ou execução de testes nas auditorias.

## Proveniência

- A árvore `launcher/app/src/main` é idêntica em `8afc5020`, `06f2b77b` e `2755c6a4`;
  os commits posteriores a `8afc5020` alteram testes, não a implementação principal.
- O APK local de build contém IdleArtController e defaults 0s/30%; o backup APK anterior não.
- Ambos declaram Home, com.gathin.tvhome, versão 2.7, código 8, minSdk 26 e targetSdk 34.
- Os metadados VCS de ambos dizem `NO_SUPPORTED_VCS_FOUND`; não certificam a origem por commit.
- A equivalência binária com a instalação reportada como v1.4.6 permanece DESCONHECIDA.
- Resultados JVM históricos: 26 testes do controlador e 10 do modelo de dispatch, sem falhas;
  o [contrato de dispatch](../launcher/app/src/test/java/com/gathin/tvhome/KeyDispatchContractTest.java#L13)
  declara a limitação do modelo sintético. Não houve nova execução.

## Arte e recuperação

- Backup lido: 90 JPEGs únicos, cabeçalhos 1920×1080, 90 legendas sem referências órfãs.
- 92/92 hashes e tamanhos coincidem com o manifesto; 91/91 coincidem com a lista histórica da TV.
  Isto verifica ficheiros locais, não o conteúdo atual da TV.
- Há outra cópia de cada pintura com conteúdo idêntico, no mesmo volume e checkout.
- [.gitignore](../.gitignore#L12) e exclusões Git locais evitam commits normais dessas pastas;
  não constituem backup independente nem proteção contra limpeza de ignorados ou colisões de checkout.
- O backup original de configuração não inclui as chaves Idle Art; a candidata inclui 15s/30%.
- [theme.sh](../tools/theme.sh#L87) limpa a coleção mesmo numa aplicação que só forneça config.json.
- O restore legado Reimagui usa nomes e pressupostos de uma coleção anterior; não certifica esta recuperação.

## Opacidade e decisões

- **Listras: RESOLVIDO.** Em 2026-10-25, o utilizador esclareceu que fazem parte da pintura.
  As hipóteses gráficas anteriores não são diagnósticos; não prosseguir essa investigação.
- [IdleArtController](../launcher/app/src/main/java/com/gathin/tvhome/IdleArtController.java#L124)
  suporta 10% sem mudança de código: fade de 400ms; reduced_motion torna-o imediato.
- [MainActivity](../launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java#L1142)
  reduz grid e foco separadamente, preservando a pintura; a interação pede alpha 1 imediatamente.
- Menor opacidade reduz legibilidade em idle; não há validação física de 10% nesta auditoria.
- [Config.syncThemeChoices](../launcher/app/src/main/java/com/gathin/tvhome/Config.java#L653)
  pode limpar overrides ao mudar a assinatura do ficheiro; uma chave pode ter efeitos indiretos.
- [ThemeProvider.openFile](../launcher/app/src/main/java/com/gathin/tvhome/ThemeProvider.java#L35)
  escreve com truncagem, sem substituição atómica: requer backup e leitura de retorno.
- Futuro plano, sujeito a autorização: preservar cópia independente, confirmar configuração e preferências
  ativas, aplicar só 30→10, verificar foco/retorno/coleção e reverter configuração e overrides se necessário.
- Não usar o aplicador de tema completo para essa alteração; não associar true color ou novo APK.
