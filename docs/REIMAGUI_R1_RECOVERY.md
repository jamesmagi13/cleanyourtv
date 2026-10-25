# Reimagui — R1: cópias e plano de recuperação

Atualizado em 2026-10-25. [Estado de continuidade](CODEX_STATUS.md). Plano documental;
nenhum passo nesta página foi executado na TV durante a fase R1.

Decisão posterior do utilizador, em 2026-10-25: a segunda cópia em disco físico separado
é **recomendada, não bloqueadora**. O risco residual de perda do volume do Mac foi
compreendido e aceite; as cópias locais verificadas e os originais permanecem protegidos.

## Inventário e validação

- O backup principal contém 90 JPEGs 1920×1080, `captions.txt`, `config.json`, um manifesto
  e duas listas de SHA-256. Os 92 conteúdos enumerados no manifesto coincidem em hash e tamanho;
  a lista histórica da TV coincide com 91 ficheiros locais. Isto não verifica a TV atual.
- A cópia privada R1 contém 118 ficheiros: backup principal (95), backup APK anterior (2),
  duas configurações de trabalho, configuração de staging (1), três backups legados Reimagui
  (16) e APK de build local com metadados (2). Total: 36 760 802 bytes de origem.
- O APK anterior não contém Idle Art; o APK de build contém-no. Na R1, nenhum tinha sido
  identificado como cópia comprovada da instalação. A recolha R2 confirmou entretanto que
  o SHA-256 do APK instalado coincide exatamente com esse artefacto de build preservado.
- A subpasta privada nova fora do checkout tem um manifesto próprio,
  ficheiros com permissões 0600 e diretório 0700. Os 118 ficheiros foram comparados com
  os originais por tamanho e SHA-256 após a cópia; zero divergências ou ficheiros em falta.
- Outra seleção local contém as mesmas 90 pinturas por hash. Todas as cópias atuais estão
  no mesmo volume físico do Mac; não resistem à sua falha.

## Segunda cópia independente — recomendada

- Exige outro **disco físico**, capacidade livre superior a 37 MB com margem, encriptação
  e local seguro. Uma pasta, partição ou imagem de disco no mesmo Mac não cumpre o critério.
- Copiar a partir da subpasta privada verificada, sem sobrescrever destino; conservar
  `R1_COPY_MANIFEST.json` e verificar de novo os 118 hashes no outro disco.
- Confirmar leitura de uma amostra de pinturas e da configuração, desligar/guardar o disco
  quando não estiver em uso e registar data e local apenas em notas privadas.
- Não usar cloud nem volumes externos sem autorização específica do utilizador.
- A ausência desta cópia não impede a recolha passiva nem, por si só, uma alteração futura.

## Recuperação numa sessão futura autorizada

1. **Conter:** parar novas instalações e alterações de temas. Determinar se o problema é
   visual, de configuração, perda de pinturas, falha do launcher ou perda de ADB. Preservar
   o estado da TV antes de qualquer escrita; não iniciar pacotes de fábrica/teste.
2. **ADB:** se estiver fechado, pedir à pessoa que use as definições físicas da TV para
   ativar a depuração e aceitar uma autorização nova. As chaves ADB não integram a cópia.
   Se a interface não permitir acesso, não presumir uma recuperação remota. No fim,
   fechar a depuração e verificar a porta como prevê o [PROMPT](../PROMPT.md).
3. **HOME:** se o launcher ainda abrir, verificar primeiro a atividade HOME e focada.
   Reinstalação exige APK identificado e compatível, assinatura verificada, confirmação
   de Home e um caminho de retorno. O APK anterior não é reversão automática da v1.4.6.
   Verificar o launcher de fábrica antes de qualquer alteração de pacotes; manter a
   salvaguarda HOME descrita no [AGENTS.md](../AGENTS.md).
4. **Arte e configuração:** confirmar inventário atual e diferenças antes de escrever.
   Restaurar apenas pinturas ausentes/alteradas a partir dos 90 hashes verificados,
   depois legendas e, por último, a configuração escolhida. Ler de volta cada ficheiro
   e comparar SHA-256. Não usar `tools/theme.sh` para restaurar só a configuração:
   [apaga primeiro a coleção](../tools/theme.sh#L87).
5. **Perfil correto:** o `config.json` original do backup não contém Idle Art e desliga
   esse modo com o APK atual. A R2 recolheu os bytes da configuração da TV: 15s/30%,
   rotação 10 min e dim zero, com hash coincidente no início e fim da recolha.
   Essa cópia privada é a base de reversão JSON. Preferências internas não puderam ser
   lidas; [a assinatura do tema pode limpá-las](../launcher/app/src/main/java/com/gathin/tvhome/Config.java#L653).
   Não prometer reposição exata dos overrides sem os ter recolhido.
6. **Validar e fechar:** após uma intervenção autorizada, confirmar Home e primeiro foco,
   regresso de aplicações, vídeo, Live TV, HDMI, Bluetooth, rotação e integridade das
   pinturas. Só então encerrar ADB. Guardar evidência privada sem identificadores públicos.

O script legado de recuperação v1.3 fica preservado como artefacto histórico. Tem
pressupostos próprios sobre biblioteca, estado e ligação; deve ser revisto antes de
qualquer uso e não foi executado nesta fase.

## GO / NO-GO para a mudança 30% → 10%

- **R1 local: GO:** inventário, hashes originais, cópia privada e hashes copiados completos.
- **Proteção independente: RECOMENDADA:** segunda cópia não realizada; risco aceite pelo utilizador.
- **Base e reversão JSON: CONFIRMADAS NA R2.** Preferências internas recusadas; definir
  o tratamento das opções afetadas antes de um teste. A escrita do provider trunca o ficheiro.
- **Leitura R2: AUTORIZADA. Execução 10%: NO-GO:** a autorização não permite alterar opacidade.
- Para implementar, confirmar a base e a reversão e obter autorização específica.
  Não exigir segunda cópia física nem identidade por commit do APK para uma mudança de configuração.
