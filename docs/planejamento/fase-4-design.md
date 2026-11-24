# LocGym — Fase 4 (Frequência) — Design

Data: 2026-11-23
Status: aprovado pelo usuário em 2026-11-24
Base: [design geral aprovado](design.md) (§5.5 agenda, check-in, presença, correção), [design da Fase 2](fase-2-design.md) (tenancy, auditoria, padrões de API), [design da Fase 3](fase-3-design.md) (matrícula, `Calendario`, inadimplência) e [especificação](especificacao.md) (§9, §11, §20).

## 1. Intenção

Dar ao LocGym o **controle de presença**: a academia cadastra a grade semanal de aulas, o aluno mostra um QR Code que muda a cada 30 s e a recepção lê esse QR com a câmera. O sistema valida se o aluno pode treinar, associa a entrada à aula certa e registra a pontualidade. Quando o QR falha, o admin registra a presença à mão e pode corrigir ou excluir registros, sempre com motivo.

Para o portfólio, o destaque é **segurança prática** (token assinado de curta duração, sem segredo no navegador), **regras de elegibilidade reaproveitando os módulos anteriores** pelas APIs públicas do Spring Modulith e uma **tela de recepção** que funciona com câmera de verdade.

## 2. Objetivos, não-objetivos, anti-objetivos

**Objetivos**
- Cadastro de horários (grade semanal); aulas como ocorrências de um horário numa data, criadas no primeiro check-in.
- QR Code rotativo assinado na área do aluno ("Meu QR Code").
- Tela "Recepção" no painel admin: leitura por câmera, escolha da aula quando há mais de uma aberta, resultado com alerta de inadimplência.
- Presença manual, correção e exclusão lógica, com motivo e auditoria.
- Histórico de frequência no painel admin, com filtros.
- Tolerância de pontualidade configurável por academia.
- Eventos `checkin.*` via outbox.

**Não-objetivos (ficam para depois)**
- Histórico de frequência e grade de horários **no portal do aluno** (Fase 5). Nesta fase a área do aluno ganha só "Meu QR Code".
- Relatórios de frequência por período, aluno, horário e professor (Fase 5). Esta fase grava os dados de que eles precisarão.
- Confirmação de check-in por notificação (Fase 6).
- Aulas avulsas fora da grade (feriados, aulão, seminário), cancelamento de uma aula específica, limite de vagas, reserva de aula.
- Check-in feito pelo próprio aluno (ler um QR da academia) ou por geolocalização.

**Anti-objetivos**
- Print ou foto do QR servir para check-in depois de expirar.
- Segredo de assinatura no navegador, ou token aceito de outra academia.
- Check-in contado na aula errada sem que a recepção tenha como evitar (motivo da escolha B, §3).
- Dois registros ativos do mesmo aluno na mesma aula (leitura repetida da câmera, clique duplo, duas recepções).
- Hora calculada em UTC em vez de `America/Sao_Paulo` (aluno "atrasado" por 3 horas).
- Mudança num horário alterar aulas passadas e, com isso, os relatórios futuros.

## 3. Abordagens consideradas

**Aulas com janelas sobrepostas** (ex.: 19h–20h e 20h–21h às 19h45; kids e adulto no mesmo horário)

| Abordagem | Trade-off | Decisão |
|---|---|---|
| A) Automático: escolhe a aula de início mais próximo; admin corrige depois | Zero clique; erra às vezes e suja os relatórios por professor/horário | Descartada |
| **B) A recepção escolhe a aula quando há mais de uma aberta; com uma só, automático** | Nunca erra; um passo a mais só quando há sobreposição | **Escolhida pelo usuário** |
| C) Proibir horários sobrepostos no cadastro | Código mais simples; não serve para academias reais | Descartada |

**Construção:** um único caminho faz sentido — dois módulos novos (`agenda`, `frequencia`) no mesmo padrão das Fases 2 e 3 (Clean Architecture por módulo, `academia_id` + RLS, outbox, regras no domínio). Descartados: gerar o QR no navegador (exigiria expor o segredo) e WebSocket para a recepção (ela só lê e envia; não precisa de push).

## 4. Módulos e dependências

- **`agenda`** (dono de `horario` e `aula`). API pública `Agenda`:
  - `List<AulaAberta> abertas(LocalDate data, LocalTime hora)` — horários ativos daquele dia da semana cuja janela contém a hora (§6.2).
  - `AulaRegistrada garantirAula(UUID horarioId, LocalDate data)` — devolve a aula daquele horário na data, criando-a se não existir (cópia do horário, §6.1). 404 se o horário não existe na academia.
  - `Optional<AulaRegistrada> aula(UUID aulaId)` e `Map<UUID, AulaResumo> aulas(Collection<UUID>)` para o histórico.
- **`frequencia`** (dono de `checkin`). Depende, pelas APIs públicas, de:
  - `agenda` (aulas abertas, garantir aula);
  - `aluno` (`Alunos.situacao`, `Alunos.nomes` — já existem);
  - `matricula` — **nova API pública `Matriculas`**: `Optional<SituacaoMatricula> vigenteDoAluno(UUID alunoId)` com `(id, status, dataInicio)` da matrícula não cancelada;
  - `pagamento` para o alerta: `Cobrancas.alunoInadimplente` (já existe) e **novo** `Optional<LocalDate> vencimentoAtrasadoMaisAntigoDoAluno(UUID alunoId)` — considera todas as matrículas do aluno, inclusive canceladas, coerente com a regra de inadimplência da Fase 3;
  - `academia` (`Academias` ganha `int toleranciaPontualidade(UUID)`).
- `agenda` depende de `professor` — **nova API pública `Professores`**: `Optional<SituacaoProfessor> situacao(UUID)` com `(id, nome, ativo)` e `Map<UUID,String> nomes(Collection<UUID>)`.
- Sem ciclos: `frequencia → agenda → professor`; `frequencia → aluno, matricula, pagamento, academia`. Verificado por `ApplicationModules.verify()`.
- `Calendario` ganha `LocalDateTime agora()` (fuso `America/Sao_Paulo`); `CalendarioDeTeste` passa a ajustar também a hora.

## 5. Modelo de dados (migration V7)

- `academia` + `tolerancia_pontualidade_min int not null default 10 check (0..60)`.
- `horario`: `id, academia_id, professor_id → professor, dia_semana smallint (1=segunda … 7=domingo, ISO), inicio time, fim time, tipo varchar(60) (texto livre: "Gi", "No-Gi", "Kids"…), status ATIVO|INATIVO, version, criado_em, atualizado_em`; `check (fim > inicio)` (não cruza a meia-noite). Índice `(academia_id, dia_semana, status)`.
- `aula`: `id, academia_id, horario_id → horario, data date, inicio time, fim time, professor_id, tipo, criado_em`; `unique (horario_id, data)`. Os campos `inicio, fim, professor_id, tipo` são **cópia do horário** no momento da criação.
- `checkin`: `id, academia_id, aluno_id → aluno, aula_id → aula (nulo = check-in livre), data date, hora time (datas de negócio em São Paulo), registrado_em timestamptz, origem QR|MANUAL, pontualidade PONTUAL|ATRASADO (nula no check-in livre), status ATIVO|CORRIGIDO|EXCLUIDO, motivo varchar(300), substituido_por → checkin (na correção), registrado_por uuid (usuário; nulo não ocorre nesta fase), version, criado_em, atualizado_em`.
  - `check ((aula_id is null) = (pontualidade is null))`; `check (status = 'ATIVO' or motivo is not null)`; `check ((status = 'CORRIGIDO') = (substituido_por is not null))`.
  - Índices únicos parciais: `(aula_id, aluno_id) where status = 'ATIVO' and aula_id is not null`; `(academia_id, aluno_id, data) where status = 'ATIVO' and aula_id is null`. Violação → 409 `CHECKIN_DUPLICADO`.
  - Índice `(academia_id, data)` e `(academia_id, aluno_id, data)` para o histórico.
- RLS forçado nas três tabelas novas, mesma policy das fases anteriores.

## 6. Regras de domínio

### 6.1 Horários
- CRUD pelo admin. Validações: `fim > inicio`; `dia_semana` 1..7; `tipo` obrigatório (1..60); professor existe na academia e está **ATIVO** ao criar ou ao trocar de professor (409 `PROFESSOR_INATIVO`). Inativar um professor depois não mexe nos horários dele.
- Horários sobrepostos são permitidos (motivo da escolha B).
- **Excluir** só se o horário nunca teve aula (409 `HORARIO_COM_AULAS` — inative em vez disso). Inativar/reativar é pelo `status` no `PUT`.
- Alterar um horário não altera aulas já criadas (elas guardam a cópia). Uma aula de hoje já criada continua com os dados antigos até o fim do dia. *(Decisão minha.)*

### 6.2 Aula aberta
- Um horário **ATIVO** do dia da semana de `data` está **aberto** às `hora` quando `inicio − 30 min ≤ hora < fim` (janela recortada em 00:00 se `inicio` < 00:30).
- **Aula já criada manda:** se já existe a aula de um horário naquela data, a janela usa os horários copiados nela (e ela continua aberta mesmo que o horário tenha sido alterado ou inativado depois). Para horários sem aula naquela data, vale o horário atual (ATIVO). Vale igualmente para a recepção, a presença manual retroativa e a correção. *(Decisão minha — resolve o caso "horário mudou de 19h para 20h depois do primeiro check-in".)*
- A aula (linha em `aula`) é criada no primeiro check-in daquele horário naquela data, com `INSERT … ON CONFLICT (horario_id, data) DO NOTHING` seguido de leitura (duas recepções simultâneas não criam duas aulas).

### 6.3 Escolha da aula (opção B, aprovada)
Dado `data`, `hora` e um `horarioId` opcional:
- `horarioId` informado → precisa estar aberto naquele momento; senão 409 `AULA_NAO_ABERTA`.
- Não informado e **uma** aula aberta → ela.
- Não informado e **várias** abertas → 409 `AULA_AMBIGUA`, com a lista das abertas no corpo (a tela pede a escolha).
- Nenhuma aberta → **check-in livre** (sem aula, sem pontualidade), um por aluno por dia.

### 6.4 Elegibilidade (QR e manual)
Na ordem, cada falha com o seu código (409, exceto token):
1. Academia ATIVA (regra da Fase 2: escrita em academia inativa → 409 `ACADEMIA_INATIVA`).
2. Aluno existe na academia (404) e está **ATIVO** (`ALUNO_INATIVO`).
3. Matrícula não cancelada existe (`SEM_MATRICULA`), está **ATIVA** (`MATRICULA_NAO_ATIVA`, com o status no corpo) e `data_inicio ≤ data` do check-in (`MATRICULA_NAO_INICIADA`, com a data).
4. Sem registro ATIVO do aluno na mesma aula (ou no mesmo dia, se livre) → `CHECKIN_DUPLICADO`.
- **Alerta, não bloqueio:** se o aluno é inadimplente (cobrança ATRASADO, ainda dentro da carência — senão a matrícula já estaria VENCIDA), a resposta traz `alerta: {vencimentoMaisAntigo, diasDeAtraso}`.
- Presença manual retroativa usa o **status atual** da matrícula (não o histórico) mais `data_inicio ≤ data`. *(Decisão minha: o histórico de status não é guardado; a regra atual é a mais simples de explicar.)*

### 6.5 Pontualidade
`PONTUAL` se `hora ≤ aula.inicio + tolerância` (inclusive); senão `ATRASADO`. Tolerância lida da academia no momento do check-in (padrão 10 min, 0..60). Chegar antes do início é PONTUAL. A comparação é **em minutos**: `hora` é truncada para HH:mm ao gravar (o instante exato fica em `registrado_em`); com início 19:00 e tolerância 10, 19:10 é PONTUAL e 19:11 é ATRASADO. *(Decisão minha.)*

### 6.6 Token do QR
- `GET /api/v1/eu/qr-checkin` (perfil ALUNO, academia ativa do aluno) → `{token, expiraEm}`. Emitido mesmo se a matrícula não estiver ativa; a recepção é quem decide.
- Conteúdo: aluno, academia, emitido em (segundos), nonce aleatório; assinado com **HMAC-SHA256** com chave do servidor (`locgym.checkin.chave-qr`, env `CHECKIN_CHAVE_QR`; vazia gera uma temporária a cada subida, só para desenvolvimento — mesmo padrão da chave JWT). Formato: `base64url(payload).base64url(assinatura)`, compacto para caber num QR legível de longe. *(Decisão minha.)*
- **Validade aceita: 60 s** desde a emissão (o portal renova a cada 30 s; a folga cobre relógio e rede). Emissão no futuro além de 5 s → inválido.
- Na leitura: assinatura inválida ou formato errado → 400 `QR_INVALIDO`; expirado → 400 `QR_EXPIRADO`; academia do token ≠ academia da recepção → 400 `QR_DE_OUTRA_ACADEMIA`.
- Reuso do mesmo token dentro dos 60 s não gera segundo registro: cai na regra de duplicado. Nonce não é guardado. *(Risco aceito: alguém com o celular do aluno em mãos nos 60 s.)*

### 6.7 Presença manual
- Admin informa `alunoId, data, hora, horarioId?`. Mesmas regras de §6.3 e §6.4, avaliadas para aquela data e hora (`horarioId` precisa estar aberto naquele momento daquele dia). Origem `MANUAL`.
- `data` entre hoje − 30 e hoje; data e hora não podem estar no futuro. *(Decisão minha.)*

### 6.8 Correção e exclusão
- **Excluir** `POST /checkins/{id}/excluir {motivo}`: ATIVO → EXCLUIDO. Libera um novo check-in para a mesma aula.
- **Corrigir** `POST /checkins/{id}/corrigir {data, hora, horarioId?, motivo}`: só de ATIVO. O registro antigo vira CORRIGIDO (com `motivo` e `substituido_por`) e nasce um novo ATIVO com a mesma origem, aula e pontualidade recalculadas pelas regras de §6.3/§6.5 para os novos dados. Elegibilidade de matrícula **não** é reavaliada na correção (o aluno estava presente; corrige-se o lançamento). Mesmo limite de 30 dias e duplicidade valem.
- **Janela de 30 dias também para o registro original:** só se corrige ou exclui check-in cuja `data` ≥ hoje − 30 (409 `CHECKIN_FORA_DO_PRAZO`). Registros mais antigos ficam congelados, protegendo os relatórios da Fase 5. *(Decisão minha.)* *(Decisão minha.)*
- Registros CORRIGIDO e EXCLUIDO são finais.

## 7. API (tenant, ADMIN salvo indicação; Problem Details; 404 para recurso de outra academia; 409 para regra violada)

- `GET /api/v1/horarios?diaSemana=&status=` (sem paginação; grade é pequena) · `POST /api/v1/horarios` · `GET/PUT/DELETE /api/v1/horarios/{id}`.
- `GET /api/v1/recepcao/aulas-abertas` → aulas abertas agora: `horarioId, tipo, professor, inicio, fim`.
- `POST /api/v1/checkins/qr {token, horarioId?}` → `201 {checkinId, aluno: {id, nome}, aula?: {tipo, professor, inicio}, pontualidade?, livre, alerta?}`.
- `POST /api/v1/checkins {alunoId, data, hora, horarioId?}` (manual) → mesmo corpo.
- `GET /api/v1/checkins?alunoId=&horarioId=&de=&ate=&status=&page=&size=` → padrão: últimos 30 dias, status ATIVO, mais recentes primeiro; itens com nome do aluno, aula (tipo, professor, início), origem, pontualidade, status, motivo.
- `POST /api/v1/checkins/{id}/corrigir` · `POST /api/v1/checkins/{id}/excluir`.
- `GET /api/v1/eu/qr-checkin` (ALUNO).
- `PUT /api/v1/academia` aceita `toleranciaPontualidade` opcional (nulo mantém).
- Concorrência: `version` em `horario` e `checkin` → 409 `CONFLITO_DE_ATUALIZACAO` (já mapeado).

## 8. Eventos Kafka (via outbox, envelope da Fase 1)
- `checkin.registrado` (QR e manual; o novo registro de uma correção emite `checkin.corrigido`, não `registrado`): `checkinId, alunoId, aulaId?, horarioId?, data, hora, origem, pontualidade?`.
- `checkin.corrigido`: `checkinAntigoId, checkinNovoId` + os dados do novo. *(Decisão minha — sem ele, consumidores de relatório contariam o registro antigo.)*
- `checkin.excluido`: `checkinId, motivo`. *(Decisão minha, mesmo motivo.)*
- Sem dados pessoais além de ids. Tópicos declarados pelo módulo `frequencia`. Nenhum consumidor nesta fase.

## 9. Auditoria
Presença manual, correção, exclusão, criação/alteração/exclusão de horário e alteração da tolerância. Check-in por QR **não** é auditado (gera evento; volume alto). *(Decisão minha.)*

## 10. Frontend

**Painel `/admin`** — menu ganha Agenda, Recepção e Frequência.
- **Agenda**: grade por dia da semana (lista agrupada segunda → domingo), com início–fim, tipo, professor e status; criar/editar em formulário (professor só ATIVO no autocomplete); inativar; excluir (mostra o 409 quando há aulas).
- **Recepção**:
  ```
  ┌ Recepção ───────────────────────────────────────────┐
  │ Aulas abertas: (•) 19:00 Gi — Prof. Ana             │
  │                ( ) 19:00 Kids — Prof. Bruno          │
  │ ┌──────── câmera ────────┐  ✔ Carla Souza            │
  │ │                        │    19:00 Gi — PONTUAL     │
  │ │                        │    ⚠ Cobrança atrasada    │
  │ └────────────────────────┘      há 4 dias            │
  │ [colar token ……………………] [Registrar]                  │
  └──────────────────────────────────────────────────────┘
  ```
  - Lista de aulas abertas atualizada a cada minuto; com uma só, fica selecionada; com várias, o operador escolhe uma (a escolha persiste enquanto ela estiver aberta). Sem nenhuma: aviso "check-in livre".
  - Leitura com `@zxing/browser`; o mesmo token lido de novo em menos de 5 s é ignorado no cliente (a câmera lê continuamente).
  - Resultado grande e colorido: verde (registrado), amarelo (registrado com alerta), vermelho (recusado, com o motivo em português). Fica visível até a próxima leitura.
  - Campo para colar o token (testes e dispositivo sem câmera). Sem permissão de câmera → mensagem e só o campo.
- **Frequência**: histórico com filtros (aluno, horário, período, status), chip de origem/pontualidade/status; ações "Corrigir" e "Excluir" (diálogo com motivo); botão "Registrar presença" (aluno ATIVO por autocomplete, data — padrão hoje —, hora — padrão agora —, aula: lista das abertas naquele momento quando houver mais de uma).
- **Academia**: campo de tolerância de pontualidade.
- **Ficha do aluno**: link "Ver frequência" (histórico filtrado pelo aluno).

**Área do aluno `/aluno`** — menu ganha **Meu QR Code**: QR grande (biblioteca `qrcode`), contagem regressiva, renovação a cada 30 s (pede novo token alguns segundos antes de expirar); pausa quando a aba fica oculta e renova ao voltar; mensagem clara se a renovação falhar (QR antigo some quando expira).

## 11. Testes
- **Domínio (unitários)**: janela da aula (30 min antes, fim exclusivo, recorte na meia-noite); escolha da aula (0, 1, várias, `horarioId` fechado); pontualidade (limite exato da tolerância, tolerância 0); token (válido, assinatura adulterada, expirado aos 61 s, emitido no futuro, outra academia); máquina de estados do check-in (corrigir/excluir só de ATIVO).
- **IT (app como `locgym_app`, `CalendarioDeTeste` com data e hora)**: check-in por QR pontual e atrasado; aula criada no primeiro check-in e reaproveitada; duas aulas abertas sem escolha → 409 `AULA_AMBIGUA` e com escolha → 201; check-in livre e duplicado no dia; cada bloqueio de elegibilidade (aluno inativo, sem matrícula, PENDENTE, VENCIDA, SUSPENSA, início futuro); inadimplente dentro da carência passa com alerta; QR duplicado → 409; duas leituras simultâneas do mesmo aluno → um 201 e um 409; presença manual retroativa e limites de data; correção recalcula pontualidade e libera a aula antiga; exclusão libera novo check-in; horário alterado não muda aula existente; excluir horário com aulas → 409; academia inativa bloqueia check-in; eventos no outbox; auditoria.
- **Isolamento**: academia A não lê nem altera horários, aulas e check-ins da B (404); token da B rejeitado na A; RLS nas tabelas novas.
- **Arquitetura**: `ApplicationModules.verify()` com as novas dependências e sem ciclo.
- **Frontend**: Recepção (seleção de aula, resultado verde/amarelo/vermelho, ignora leitura repetida, campo de token), Meu QR Code (renovação e contagem com timers falsos), formulário de horário, diálogo de presença manual.

## 12. Documentação
Atualizar 02 (RF08/RF09 com as regras finais), 04 (ER com horário, aula, check-in), 05 (módulos `agenda` e `frequencia`), 06 (endpoints), 07 (eventos `checkin.*`), 08 (Fase 4 concluída), 09 (ADRs: escolha da aula pela recepção; aula como cópia do horário; token HMAC com validade de 60 s), README (status).

## 13. Deixado para o construtor
- Estrutura interna de pacotes/DTOs, seguindo `dominio/aplicacao/infraestrutura/web`.
- Versões das bibliotecas `@zxing/browser` e `qrcode`; layout visual fino.
- Tamanho de página padrão do histórico.
- Como `CalendarioDeTeste` passa a controlar a hora (desde que a data de negócio continue vindo do mesmo relógio).

## 13.1 Ajustes feitos na implementação
- `substituido_por` com FK `deferrable initially deferred`: a correção atualiza o registro antigo (liberando o índice único da aula) antes de inserir o novo para o qual ele aponta.
- Erros do token (400 com `codigo`) usam a exceção compartilhada `RequisicaoInvalidaException`.
- Horas na API em `HH:mm`; paginação do histórico com `pagina`/`tamanho` (padrão da API).
- Presença manual e correção usam o mesmo diálogo no frontend; professores são escolhidos numa lista de seleção.

## 14. Decisões minhas (revisáveis)
(Aprovadas pelo usuário: **opção B** para aulas sobrepostas; as decisões listadas na conversa — token de 60 s, check-in livre, aula como cópia do horário, presença manual com as mesmas regras, correção/exclusão, tolerância na tela da academia, telas e biblioteca de leitura.)
- Dia da semana ISO (1 = segunda); horário não cruza meia-noite; tipo de aula em texto livre.
- Alterar horário não muda a aula de hoje já criada.
- Presença retroativa usa o status atual da matrícula.
- Correção não reavalia matrícula.
- Eventos `checkin.corrigido` e `checkin.excluido`.
- Check-in por QR não é auditado.
- Formato do token e chave por variável de ambiente.
- Aula já criada define a janela daquela data; pontualidade em minutos; correção/exclusão só nos últimos 30 dias; alerta considera dívidas de todas as matrículas do aluno.
