# LocGym — Fase 4 (Frequência) — Plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:executing-plans (ou superpowers:subagent-driven-development). Passos com checkbox (`- [ ]`).

**Goal:** Grade semanal de horários, aulas criadas no primeiro check-in, check-in por QR Code rotativo assinado lido na tela Recepção, presença manual, correção/exclusão lógica e histórico de frequência — com eventos `checkin.*` e as telas do painel admin e "Meu QR Code" do aluno.

**Architecture:** Dois módulos Spring Modulith novos: `agenda` (horário, aula) e `frequencia` (check-in, token do QR, recepção). Dependências: `frequencia → agenda → professor`; `frequencia → aluno, matricula, pagamento, academia`, sempre pelas APIs públicas no pacote base. Token HMAC-SHA256 emitido pelo backend; a escolha da aula segue a opção B (recepção escolhe quando há mais de uma aberta).

**Tech Stack:** o das Fases 1–3 + `@zxing/browser` (leitura do QR) e `qrcode` (desenho do QR) no frontend; `javax.crypto.Mac` (HmacSHA256) no backend.

**Spec:** `docs/planejamento/fase-4-design.md` (base: `design.md`, `fase-2-design.md`, `fase-3-design.md`).

## Global Constraints

- Reaproveitar: `EntidadeDeTenant` (`@TenantId`), `TenantContext`/`ContextoTenant`, `Auditoria`/`AcaoAuditada`, o publicador de eventos do outbox (padrão `EventosMatricula` + `TopicosMatricula`), `Pagina<T>`, `RegraVioladaException`, `NaoEncontradoException`, `ValorInvalidoException`, `Calendario`; nos testes `Cenarios`, `ClienteHttp`, `DadosDeTeste`, `CalendarioDeTeste`, `BancoSuperusuario`.
- Camadas `dominio` (sem Spring/JPA/Jackson) / `aplicacao` / `infraestrutura` / `web`; API pública no pacote base do módulo; `ApplicationModules.verify()` e `ArquiteturaTest` verdes.
- Data e hora de negócio vêm de `Calendario` (`hoje()`, `agora()`), fuso **`America/Sao_Paulo`**. Nunca `LocalDate.now()`/`LocalTime.now()`.
- Valores do design: janela abre **30 min** antes do início e fecha no fim (exclusivo); tolerância padrão **10** min (0–60); token válido por **60 s**, emitido no futuro além de **5 s** é inválido; portal renova a cada **30 s**; presença manual/correção/exclusão: data ≥ hoje − **30**; hora truncada para **minutos**; leitura repetida ignorada no cliente por **5 s**.
- Rotas sob `/api/v1`; recurso de outra academia → **404**; regra → **409** com `codigo`; validação → **400**; token → **400** (`QR_INVALIDO`, `QR_EXPIRADO`, `QR_DE_OUTRA_ACADEMIA`).
- Eventos `checkin.registrado|corrigido|excluido`, versão 1, `dados` só com ids, datas, horas e enums (nunca nome/CPF/e-mail).
- Commits Conventional Commits terminando com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Comandos: backend `cd backend && ./mvnw -B verify` (um IT: `-Dit.test=NomeIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`; um unitário: `./mvnw -B test -Dtest=Nome`); frontend `cd frontend && npx ng lint && npx ng test --watch=false && npx ng build`.

## Review Focus

1. **Câmera lê o mesmo QR várias vezes / duas recepções leem o mesmo aluno ao mesmo tempo** → um 201 e os demais 409 `CHECKIN_DUPLICADO`, nunca 500 nem dois registros. *(Task 7: `qrConcorrenteUmSucessoUm409`.)*
2. **Check-in entre 21h e 23h59 em São Paulo** (já é o dia seguinte em UTC) → data e dia da semana de SP; a aula certa é encontrada. *(Task 1: `agoraUsaFusoDeSaoPaulo`; Task 7: `checkinANoiteUsaDiaDeSaoPaulo`.)*
3. **Horário alterado ou inativado depois do primeiro check-in do dia** → a aula de hoje continua aberta com os dados copiados; alunos atrasados ainda entram nela. *(Task 4: `aulaCriadaContinuaAbertaAposAlterarHorario`.)*
4. **Token truncado, com lixo ou base64 inválido** (QR de outro sistema lido pela câmera) → 400 `QR_INVALIDO`, nunca 500. *(Task 5: `tokenMalformadoInvalido`.)*
5. **Aluno com vínculo em duas academias mostra o QR da academia A na recepção da B** → 400 `QR_DE_OUTRA_ACADEMIA`. *(Task 7: `qrDeOutraAcademia400`.)*

---

## Estrutura de arquivos (backend `src/main/java/com/locgym/`)

```
compartilhado/tempo/Calendario.java                 + agora()
compartilhado/erros/RegraVioladaException.java      + extras (propriedades no Problem Details)
compartilhado/auditoria/AcaoAuditada.java           + ações da Fase 4
academia/Academias.java                             + toleranciaPontualidade(id)
aluno/Alunos.java                                   + idDoUsuario(usuarioId)
professor/Professores.java (+ impl)                 API pública nova
matricula/Matriculas.java (+ impl)                  API pública nova
pagamento/Cobrancas.java                            + vencimentoAtrasadoMaisAntigoDoAluno(alunoId)
agenda/     Agenda.java, AulaAberta.java, AulaRegistrada.java          (API pública)
            dominio/ Horario, StatusHorario, JanelaDeAula, HorarioRepositorio, AulaRepositorio
            aplicacao/ ServicoHorarios, AgendaImpl   infraestrutura/ HorarioJpa…, AulaJdbc   web/ HorariosController
frequencia/ dominio/ Checkin, StatusCheckin, Origem, Pontualidade, TokenCheckin, CheckinRepositorio
            aplicacao/ ServicoCheckin, EscolhaDeAula, ServicoTokenQr, ServicoHistorico, EventosFrequencia
            infraestrutura/ CheckinJpa…, ChaveQrConfig, TopicosFrequencia
            web/ CheckinsController, RecepcaoController, QrCheckinController
resources/db/migration/ V7__tolerancia_pontualidade.sql, V8__agenda_e_frequencia.sql
```

Frontend (`frontend/src/app/`): `core/api/frequencia.ts` (AgendaApi, FrequenciaApi + modelos), `paginas/admin/agenda/` (`agenda-lista`, `horario-form`), `paginas/admin/recepcao/recepcao`, `paginas/admin/frequencia/frequencia-lista`, `componentes/leitor-qr/` (encapsula `@zxing/browser`), `componentes/dialogo-presenca/`, `componentes/dialogo-correcao/`, `paginas/aluno/meu-qr/meu-qr`; alterações em `app.routes.ts`, `layout/shell-admin`, `layout/shell-aluno`, `paginas/admin/academia`, `paginas/admin/alunos/aluno-form`, `paginas/admin/rotulos.ts`, `core/api/modelos.ts`.

---

### Task 1: Fundamentos — hora de negócio, tolerância, extras no erro e APIs públicas

**Files:**
- Create: `professor/Professores.java` (+ impl em `professor/aplicacao/`), `matricula/Matriculas.java` (+ impl em `matricula/aplicacao/`), `resources/db/migration/V7__tolerancia_pontualidade.sql`
- Modify: `compartilhado/tempo/Calendario.java`, `compartilhado/erros/RegraVioladaException.java`, `compartilhado/web/TratadorGlobalDeErros.java`, `compartilhado/auditoria/AcaoAuditada.java`, `academia/Academias.java` (+ `ConsultaAcademias`), `academia/dominio/Academia.java`, requisição/resposta/serviço da academia, `aluno/Alunos.java` (+ impl), `pagamento/Cobrancas.java` (+ `ServicoCobrancas`, `CobrancaRepositorio`)
- Test: `CalendarioTest` (estender), `test/.../apoio/CalendarioDeTeste.java`, `test/.../apoio/Cenarios.java` (`comoAluno`), `AcademiaIT` (estender), `test/.../compartilhado/web/RegraVioladaExtrasIT.java` ou teste no `ControllerDeContextoDeTeste` existente, `CobrancasIT` (estender)

**Interfaces:**
- Produces:
  - `Calendario.agora(): LocalDateTime` = `LocalDateTime.ofInstant(clock.instant(), FUSO)`.
  - `CalendarioDeTeste`: guarda um `LocalDateTime`; `definir(LocalDateTime)`; `definir(LocalDate)` mantém o comportamento atual e fixa a hora em 12:00; `avancarDias(int)`, `avancarMinutos(int)`; `hoje()` = `agora().toLocalDate()`.
  - `RegraVioladaException(String codigo, String mensagem, Map<String, Object> extras)`; `extras()` (vazio no construtor antigo). O tratador copia cada extra como propriedade do Problem Details.
  - `AcaoAuditada` += `HORARIO_CRIADO, HORARIO_ALTERADO, HORARIO_EXCLUIDO, PRESENCA_MANUAL_REGISTRADA, CHECKIN_CORRIGIDO, CHECKIN_EXCLUIDO, TOLERANCIA_PONTUALIDADE_ALTERADA`.
  - `Academias.toleranciaPontualidade(UUID academiaId): int`. `PUT /api/v1/academia` aceita `toleranciaPontualidade` (nulo mantém; 0–60); `GET` devolve.
  - `Alunos.idDoUsuario(UUID usuarioId): Optional<UUID>` (aluno do usuário na academia do contexto).
  - `professor.Professores { record SituacaoProfessor(UUID id, String nome, boolean ativo); Optional<SituacaoProfessor> situacao(UUID id); Map<UUID,String> nomes(Collection<UUID> ids); }`.
  - `matricula.Matriculas { record SituacaoMatricula(UUID id, String status, LocalDate dataInicio); Optional<SituacaoMatricula> vigenteDoAluno(UUID alunoId); }` (a não cancelada).
  - `Cobrancas.vencimentoAtrasadoMaisAntigoDoAluno(UUID alunoId): Optional<LocalDate>` (todas as matrículas, inclusive canceladas).
  - `Cenarios.comoAluno(ClienteHttp http, String email): ClienteHttp` (login + contexto ALUNO; mover o `entrarComoAluno` de `AlunosIT`).

- [ ] **Step 1: Testes que falham.**
  - `CalendarioTest.agoraUsaFusoDeSaoPaulo`: `Clock.fixed(Instant.parse("2026-11-24T01:30:00Z"), UTC)` → `agora() == 2026-11-23T22:30`.
  - `AcademiaIT.toleranciaPadraoEEditavel`: nova → `toleranciaPontualidade == 10`; PUT `0` → GET 0, auditoria `TOLERANCIA_PONTUALIDADE_ALTERADA`; PUT `61` → 400; PUT sem o campo mantém.
  - Extras: um endpoint de teste (no `ControllerDeContextoDeTeste` existente) lança `RegraVioladaException("X", "m", Map.of("aulas", List.of("a")))` → 409 com `codigo == "X"` e `aulas[0] == "a"`.
  - `CobrancasIT.vencimentoAtrasadoMaisAntigoDoAlunoConsideraCancelada`: atrasada de 2026-10-01 em matrícula CANCELADA + atrasada de 2026-11-01 na ATIVA → `2026-10-01`; aluno sem atraso → vazio.
- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B verify -Dit.test='AcademiaIT,CobrancasIT' -Dtest='CalendarioTest'` → FAIL.
- [ ] **Step 3: Implementar.** `V7`: `alter table academia add column tolerancia_pontualidade_min int not null default 10 check (tolerancia_pontualidade_min between 0 and 60);`. As APIs novas usam os repositórios existentes de cada módulo (`Matriculas` por `MatriculaRepositorio`, buscando a matrícula com `status <> 'CANCELADA'` do aluno).
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS (suíte inteira: a mudança no `CalendarioDeTeste` não pode quebrar as ITs da Fase 3).
- [ ] **Step 5: Commit** — `feat: hora de negócio, tolerância de pontualidade, extras em erros de regra e APIs públicas de professor/matrícula`

---

### Task 2: Schema de agenda e frequência com RLS

**Files:**
- Create: `resources/db/migration/V8__agenda_e_frequencia.sql`
- Modify: `test/.../compartilhado/tenancy/RlsIT.java`

**Interfaces:**
- Produces: tabelas `horario`, `aula`, `checkin` exatamente como no design §5, com RLS + `FORCE` e a policy `tenant_isolamento` (mesmo bloco `do $$` da V6), e:
  - `horario`: `dia_semana smallint check (dia_semana between 1 and 7)`, `tipo varchar(60) check (btrim(tipo) <> '')`, `status check in ('ATIVO','INATIVO')`, `check (fim > inicio)`, `version`; índice `(academia_id, dia_semana, status)`.
  - `aula`: `constraint ux_aula_horario_data unique (horario_id, data)`.
  - `checkin`: `hora time(0)`; `origem in ('QR','MANUAL')`; `pontualidade in ('PONTUAL','ATRASADO')` nula; `status in ('ATIVO','CORRIGIDO','EXCLUIDO')`; os três `check` do design; `create unique index ux_checkin_aula_aluno on checkin (aula_id, aluno_id) where status = 'ATIVO' and aula_id is not null;` e `create unique index ux_checkin_livre_dia on checkin (academia_id, aluno_id, data) where status = 'ATIVO' and aula_id is null;`; índices `(academia_id, data)` e `(academia_id, aluno_id, data)`.

- [ ] **Step 1: Teste que falha** — incluir `horario`, `aula`, `checkin` no teste parametrizado de isolamento do `RlsIT`.
- [ ] **Step 2: Rodar e ver falhar** — `-Dit.test=RlsIT` → FAIL (tabelas inexistentes).
- [ ] **Step 3: Escrever a V8.**
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS.
- [ ] **Step 5: Commit** — `feat(db): horários, aulas e check-ins com RLS`

---

### Task 3: Domínio da agenda

**Files:**
- Create: `agenda/dominio/Horario.java`, `StatusHorario.java`, `JanelaDeAula.java`, `HorarioRepositorio.java`, `AulaRepositorio.java`
- Test: `test/.../agenda/dominio/HorarioTest.java`, `JanelaDeAulaTest.java`

**Interfaces:**
- Produces:
  - `JanelaDeAula`: `static boolean contem(LocalTime inicio, LocalTime fim, LocalTime hora)` — `abre = inicio < 00:30 ? 00:00 : inicio − 30 min`; `abre ≤ hora < fim`.
  - `Horario.novo(UUID professorId, int diaSemana, LocalTime inicio, LocalTime fim, String tipo)`; `alterar(UUID professorId, int diaSemana, LocalTime inicio, LocalTime fim, String tipo, StatusHorario status)`; getters `id()`, `professorId()`, `diaSemana()`, `inicio()`, `fim()`, `tipo()`, `status()`, `ativo()`. Valida: `fim > inicio` (campo `fim`), `diaSemana` 1..7 (campo `diaSemana`), `tipo` aparado 1..60 (campo `tipo`) → `ValorInvalidoException`. Segundos/nanos de `inicio`/`fim` são zerados.
  - `HorarioRepositorio { Optional<Horario> porId(UUID); List<Horario> listar(Integer diaSemana, StatusHorario status); List<Horario> ativosDoDia(int diaSemana); void salvar(Horario); void excluir(UUID); }`.
  - `AulaRepositorio { record Aula(UUID id, UUID horarioId, LocalDate data, LocalTime inicio, LocalTime fim, UUID professorId, String tipo); Aula garantir(Horario h, LocalDate data); List<Aula> doDia(LocalDate data); boolean horarioTemAulas(UUID horarioId); Map<UUID, Aula> porIds(Collection<UUID>); }`.

- [ ] **Step 1: Testes que falham.**
  - `JanelaDeAulaTest`: início 19:00 fim 20:00 → 18:29 `false`, 18:30 `true`, 19:59 `true`, 20:00 `false`; início 00:15 fim 01:00 → 00:00 `true`.
  - `HorarioTest`: `fimAntesDoInicio400` (19:00–19:00 → campo `fim`); `diaSemanaForaDaFaixa400` (0 e 8); `tipoVazio400` e `tipoComEspacosEAparado` (`"  Gi "` → `"Gi"`); `segundosZerados` (19:00:30 → 19:00); `alterarInativa`.
- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest='HorarioTest,JanelaDeAulaTest'` → FAIL (compilação).
- [ ] **Step 3: Implementar** conforme Interfaces.
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B test -Dtest='HorarioTest,JanelaDeAulaTest,ArquiteturaTest'` → PASS.
- [ ] **Step 5: Commit** — `feat(agenda): domínio de horário e janela da aula`

---

### Task 4: Módulo `agenda` — persistência, endpoints de horário e API pública

**Files:**
- Create: `agenda/Agenda.java`, `AulaAberta.java`, `AulaRegistrada.java`, `agenda/aplicacao/ServicoHorarios.java`, `AgendaImpl.java`, `agenda/infraestrutura/HorarioJpa.java`, `HorarioJpaDados.java`, `HorarioRepositorioJpa.java`, `AulaJdbc.java`, `agenda/web/HorariosController.java` (+ DTOs)
- Test: `test/.../agenda/HorariosIT.java`, `test/.../agenda/AgendaIT.java`; `DadosDeTeste` (+ `professor(su, academia, status)`, `horario(su, academia, professor, diaSemana, inicio, fim, tipo)`)

**Interfaces:**
- Consumes: `Professores.situacao`, `Professores.nomes` (Task 1); domínio da Task 3; `Calendario`.
- Produces:
  - `record AulaAberta(UUID horarioId, UUID aulaId /* nulo se ainda não criada */, String tipo, UUID professorId, String professorNome, LocalTime inicio, LocalTime fim)`.
  - `record AulaRegistrada(UUID id, UUID horarioId, LocalDate data, LocalTime inicio, LocalTime fim, UUID professorId, String professorNome, String tipo)`.
  - `agenda.Agenda { List<AulaAberta> abertas(LocalDate data, LocalTime hora); AulaRegistrada garantirAula(UUID horarioId, LocalDate data); Map<UUID, AulaRegistrada> aulas(Collection<UUID> aulaIds); }`.
  - `abertas`: (a) aulas já criadas em `data` cuja janela (horários **copiados**) contém `hora`, qualquer status do horário; mais (b) horários **ATIVO** de `data.getDayOfWeek().getValue()` **sem** aula em `data` cuja janela contém `hora`. Ordenado por `inicio`, depois `tipo`.
  - `garantirAula`: horário inexistente → `NaoEncontradoException`; `insert … on conflict on constraint ux_aula_horario_data do nothing` + `select` (cópia de início, fim, professor, tipo).
  - Endpoints: `GET /api/v1/horarios?diaSemana=&status=` → lista com `professorNome`; `POST` (201 `{id}`); `GET/PUT/DELETE /api/v1/horarios/{id}`. Professor inexistente → 404; INATIVO ao criar ou ao trocar de professor → 409 `PROFESSOR_INATIVO`; `DELETE` com aulas → 409 `HORARIO_COM_AULAS`. Auditoria `HORARIO_CRIADO|ALTERADO|EXCLUIDO`.

- [ ] **Step 1: Testes que falham.**
  - `HorariosIT`: `criaListaEEdita` (lista ordenada por dia e início, com nome do professor); `professorInativo409`; `trocarParaProfessorInativo409` e `manterProfessorQueFicouInativoOk`; `excluirSemAulas204`; `excluirComAulas409`; `isolamento` (horário da B → 404 em GET/PUT/DELETE); `auditado`; `academiaInativaBloqueiaEscrita409`.
  - `AgendaIT` (chama `Agenda` dentro de transação com `TenantContext` do admin, `CalendarioDeTeste`): `abertasNaJanela` (2026-11-23 é segunda; horário seg 19:00–20:00 → aberto às 18:30, não às 18:29 nem às 20:00); `outroDiaDaSemanaNaoAbre`; `inativoNaoAbre`; `duasSobrepostasAmbasAbertas` (19:00–20:00 e 20:00–21:00 às 19:45 → 2); `garantirAulaIdempotente` (2 chamadas → mesmo id, 1 linha); `aulaCriadaContinuaAbertaAposAlterarHorario` (garantir às 19:05, alterar horário para 20:00–21:00 e inativar → às 19:15 a aula continua em `abertas` com início 19:00 e `aulaId` preenchido); `garantirAulaConcorrente` (2 threads → 1 linha).
- [ ] **Step 2: Rodar e ver falhar** — `-Dit.test='HorariosIT,AgendaIT'` → FAIL.
- [ ] **Step 3: Implementar** conforme Interfaces (JPA com `@Version` para `horario`; JDBC para `aula`).
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS.
- [ ] **Step 5: Commit** — `feat(agenda): horários, aulas sob demanda e API pública da agenda`

---

### Task 5: Token do QR e "Meu QR Code" no backend

**Files:**
- Create: `frequencia/dominio/TokenCheckin.java`, `frequencia/aplicacao/ServicoTokenQr.java`, `frequencia/infraestrutura/ChaveQrConfig.java`, `frequencia/web/QrCheckinController.java`
- Modify: `resources/application.yml` (`locgym.checkin.chave-qr: ${CHECKIN_CHAVE_QR:}`), `compose.yaml`/`.env.example` se a chave JWT estiver lá (mesmo padrão)
- Test: `test/.../frequencia/dominio/TokenCheckinTest.java`, `test/.../frequencia/QrCheckinIT.java`

**Interfaces:**
- Produces:
  - `TokenCheckin` (sem Spring): `TokenCheckin(byte[] chave)` (≥ 32 bytes, senão `IllegalArgumentException`); `String emitir(UUID alunoId, UUID academiaId, Instant agora, byte[] nonce8)`; `Conteudo validar(String token, Instant agora)` com `record Conteudo(UUID alunoId, UUID academiaId, Instant emitidoEm)`. Formato: `base64url(payload) + "." + base64url(HmacSHA256(payload))`, sem padding; `payload` = 16 bytes aluno + 16 bytes academia + 8 bytes epoch-segundos + 8 bytes nonce (48 bytes). Comparação com `MessageDigest.isEqual`. Erros: `TokenInvalidoException(String codigo)` com `QR_INVALIDO` ou `QR_EXPIRADO` (mapeada para 400 com `codigo` no tratador global).
  - `ServicoTokenQr.emitirParaUsuarioAtual(): TokenEmitido` com `record TokenEmitido(String token, Instant expiraEm)` (`expiraEm = emitidoEm + 60 s`); aluno via `Alunos.idDoUsuario` (sem aluno → 404). `ServicoTokenQr.validar(String token): Conteudo` exige `academiaId == academia do contexto`, senão `TokenInvalidoException("QR_DE_OUTRA_ACADEMIA")`.
  - `GET /api/v1/eu/qr-checkin` → `{token, expiraEm}` (perfil ALUNO, mesma regra de `/api/v1/eu/**`).
  - `ChaveQrConfig`: chave = Base64 de `locgym.checkin.chave-qr`; vazia → 32 bytes de `SecureRandom` + log WARN "chave do QR temporária (só desenvolvimento)".

- [ ] **Step 1: Testes que falham.**
  - `TokenCheckinTest`: `emiteEValida` (ids e emissão voltam iguais); `aos60sAindaValido` e `aos61sExpirado` (`QR_EXPIRADO`); `emitidoNoFuturoAlem5sInvalido` (agora − 6 s → `QR_INVALIDO`; − 5 s → válido); `assinaturaAdulteradaInvalida` (troca 1 caractere do payload); `outraChaveInvalida`; `tokenMalformadoInvalido` (`""`, `"abc"`, `"a.b.c"`, `"!!!.???"`, payload de 47 bytes → todos `QR_INVALIDO`, nenhuma outra exceção); `chaveCurtaRejeitada`; `tokenCabeEm110Caracteres`.
  - `QrCheckinIT`: `alunoRecebeToken` (`comoAluno` → 200, `expiraEm` ≈ agora + 60 s, token validável por `ServicoTokenQr` no contexto da academia); `adminSemPerfilAluno403` (mesmo status que `/eu/aluno` devolve hoje); `tokensSucessivosDiferentes` (nonce).
- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B verify -Dtest=TokenCheckinTest -Dit.test=QrCheckinIT` → FAIL.
- [ ] **Step 3: Implementar** conforme Interfaces.
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS.
- [ ] **Step 5: Commit** — `feat(frequencia): token HMAC do QR de check-in e endpoint do aluno`

---

### Task 6: Domínio do check-in e escolha da aula

**Files:**
- Create: `frequencia/dominio/Checkin.java`, `StatusCheckin.java`, `Origem.java`, `Pontualidade.java`, `CheckinRepositorio.java`, `frequencia/aplicacao/EscolhaDeAula.java`
- Test: `test/.../frequencia/dominio/CheckinTest.java`, `PontualidadeTest.java`, `test/.../frequencia/aplicacao/EscolhaDeAulaTest.java`

**Interfaces:**
- Consumes: `AulaAberta` (Task 4).
- Produces:
  - `enum Pontualidade { PONTUAL, ATRASADO; static Pontualidade de(LocalTime inicioAula, LocalTime hora, int toleranciaMin) }` — PONTUAL se `hora ≤ inicio + tolerância`.
  - `enum Origem { QR, MANUAL }`; `enum StatusCheckin { ATIVO, CORRIGIDO, EXCLUIDO }`.
  - `Checkin.registrar(UUID alunoId, UUID aulaId /* nulo = livre */, LocalTime inicioAula /* nulo se livre */, LocalDate data, LocalTime hora, Origem origem, int tolerancia, UUID registradoPor, Instant registradoEm)` — trunca `hora` para minutos; calcula `pontualidade` (nula se livre). `void excluir(String motivo)`; `Checkin corrigir(UUID novaAulaId, LocalTime novoInicioAula, LocalDate novaData, LocalTime novaHora, String motivo, int tolerancia, UUID usuario, Instant agora)` → devolve o novo (mesma origem, ATIVO) e marca este CORRIGIDO com `motivo` e `substituidoPor`. Motivo aparado 1..300 → senão `ValorInvalidoException` campo `motivo`. Só de ATIVO → senão `RegraVioladaException("CHECKIN_NAO_ATIVO", …)`.
  - `static void validarMomento(LocalDate data, LocalTime hora, LocalDateTime agora)` em `Checkin` — `data < agora.toLocalDate() − 30` → `ValorInvalidoException` campo `data`; `(data, hora) > agora` → campo `hora`.
  - `CheckinRepositorio { void inserir(Checkin) /* violação de ux_checkin_* → RegraVioladaException CHECKIN_DUPLICADO */; Optional<Checkin> porId(UUID); void salvar(Checkin); Pagina<Checkin> listar(FiltroCheckins, int pagina, int tamanho); }` com `record FiltroCheckins(UUID alunoId, UUID horarioId, LocalDate de, LocalDate ate, StatusCheckin status)`.
  - `EscolhaDeAula.escolher(List<AulaAberta> abertas, UUID horarioId): Optional<AulaAberta>` — vazio = livre; regras do design §6.3: `horarioId` fora das abertas → `RegraVioladaException("AULA_NAO_ABERTA", …)`; várias sem `horarioId` → `RegraVioladaException("AULA_AMBIGUA", "Escolha a aula", Map.of("aulas", <lista de {horarioId, tipo, professorNome, inicio, fim}>))`.

- [ ] **Step 1: Testes que falham.**
  - `PontualidadeTest`: início 19:00, tolerância 10 → 18:40 PONTUAL, 19:10 PONTUAL, 19:11 ATRASADO; tolerância 0 → 19:00 PONTUAL, 19:01 ATRASADO.
  - `CheckinTest`: `registrarTruncaSegundos` (19:10:59 → 19:10, PONTUAL); `livreSemPontualidade`; `excluirExigeMotivo` / `excluirSoDeAtivo` (`CHECKIN_NAO_ATIVO`); `corrigirCriaNovoEMarcaAntigo` (antigo CORRIGIDO com `substituidoPor == novo.id()`, novo ATIVO com a mesma origem e pontualidade recalculada); `validarMomento` (hoje − 30 ok, − 31 → `data`; agora + 1 min → `hora`).
  - `EscolhaDeAulaTest`: nenhuma → vazio; uma → ela; uma + `horarioId` dela → ela; várias sem id → `AULA_AMBIGUA` com `extras().get("aulas")` de tamanho 2; várias + id → a escolhida; id fora → `AULA_NAO_ABERTA`.
- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest='PontualidadeTest,CheckinTest,EscolhaDeAulaTest'` → FAIL.
- [ ] **Step 3: Implementar** conforme Interfaces.
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B test -Dtest='PontualidadeTest,CheckinTest,EscolhaDeAulaTest,ArquiteturaTest'` → PASS.
- [ ] **Step 5: Commit** — `feat(frequencia): domínio do check-in, pontualidade e escolha da aula`

---

### Task 7: Check-in por QR, presença manual e recepção

**Files:**
- Create: `frequencia/aplicacao/ServicoCheckin.java`, `EventosFrequencia.java`, `frequencia/infraestrutura/CheckinJpa.java`, `CheckinJpaDados.java`, `CheckinRepositorioJpa.java`, `TopicosFrequencia.java`, `frequencia/web/CheckinsController.java`, `RecepcaoController.java` (+ DTOs)
- Test: `test/.../frequencia/CheckinIT.java`, `test/.../frequencia/PresencaManualIT.java`; `DadosDeTeste` (+ `matricula(su, academia, aluno, plano, inicio, status)` se faltar)

**Interfaces:**
- Consumes: `Agenda` (Task 4), `ServicoTokenQr` (Task 5), domínio e `EscolhaDeAula` (Task 6), `Alunos.situacao`, `Matriculas.vigenteDoAluno`, `Cobrancas.alunoInadimplente`, `Cobrancas.vencimentoAtrasadoMaisAntigoDoAluno`, `Academias.toleranciaPontualidade`, `Calendario.agora()`.
- Produces:
  - `ServicoCheckin.porQr(String token, UUID horarioId): ResultadoCheckin`; `ServicoCheckin.manual(UUID alunoId, LocalDate data, LocalTime hora, UUID horarioId): ResultadoCheckin`. `record ResultadoCheckin(UUID checkinId, UUID alunoId, String alunoNome, AulaRegistrada aula /* nulo = livre */, Pontualidade pontualidade, Alerta alerta /* nulo */)`, `record Alerta(LocalDate vencimentoMaisAntigo, long diasDeAtraso)`.
  - Ordem (design §6.4): token (QR) → aluno existe (404) e ATIVO (`ALUNO_INATIVO`) → matrícula (`SEM_MATRICULA`; `MATRICULA_NAO_ATIVA` com extra `status`; `MATRICULA_NAO_INICIADA` com extra `dataInicio`) → escolha da aula → `garantirAula` → `Checkin.registrar` → `inserir` (duplicado → `CHECKIN_DUPLICADO`). QR usa `agora()` truncado; manual chama `Checkin.validarMomento` antes.
  - Evento `checkin.registrado` com `dados`: `checkinId, alunoId, aulaId, horarioId, data, hora, origem, pontualidade` (nulos quando livre). Manual auditado como `PRESENCA_MANUAL_REGISTRADA`.
  - `POST /api/v1/checkins/qr {token, horarioId?}` e `POST /api/v1/checkins {alunoId, data, hora, horarioId?}` → 201 `{checkinId, aluno:{id,nome}, aula:{horarioId,tipo,professorNome,inicio,fim}|null, livre, pontualidade, alerta:{vencimentoMaisAntigo,diasDeAtraso}|null}`.
  - `GET /api/v1/recepcao/aulas-abertas` → `abertas(agora)`: `[{horarioId, tipo, professorNome, inicio, fim}]`.
  - `GET /api/v1/recepcao/aulas-abertas?data=&hora=` → mesmas, para o momento informado (usado pelo diálogo de presença manual).

- [ ] **Step 1: Testes que falham** (`CalendarioDeTeste` em 2026-11-23, segunda; horário seg 19:00–20:00; aluno ATIVO com matrícula ATIVA desde 2026-11-01).
  - `CheckinIT`: `qrPontual` (19:05 → 201, PONTUAL, aula criada); `qrAtrasado` (19:11 → ATRASADO); `segundoAlunoReaproveitaAula`; `semAulaAbertaCheckinLivre` (15:00 → `livre == true`, sem pontualidade) e `livreDuplicadoNoDia409`; `duasAbertasSemEscolha409ComLista` (`codigo == AULA_AMBIGUA`, `aulas` com 2) e `comEscolha201`; `horarioNaoAberto409`; `qrDuplicadoNaMesmaAula409`; `qrConcorrenteUmSucessoUm409` (2 threads, mesmo token → um 201 e um 409, 1 linha); `alunoInativo409`; `semMatricula409`; `matriculaPendenteVencidaSuspensa409` (parametrizado, extra `status`); `matriculaNaoIniciada409` (início 2026-11-25, extra `dataInicio`); `inadimplenteNaCarenciaPassaComAlerta` (cobrança ATRASADO de 2026-11-20 → `alerta.diasDeAtraso == 3`); `qrDeOutraAcademia400`; `qrExpirado400`; `checkinANoiteUsaDiaDeSaoPaulo` (horário seg 22:00–23:00; agora 2026-11-23T22:10 → `data == 2026-11-23`, aula de segunda); `academiaInativa409`; `eventoNoOutbox` (`checkin.registrado` com `origem == QR` e sem nome no payload); `aulasAbertasDaRecepcao`; `isolamento` (horárioId da B → `AULA_NAO_ABERTA`; aluno da B no manual → 404).
  - `PresencaManualIT`: `manualRetroativo` (2026-11-20 19:05 → aula daquele dia criada, origem MANUAL, auditoria); `manualDataAntigaOuFutura400` (− 31 dias; hoje 23:00 com agora 19:00); `manualSegueRegrasDeElegibilidade` (matrícula VENCIDA → 409); `manualEscolheAulaQuandoAmbigua`.
- [ ] **Step 2: Rodar e ver falhar** — `-Dit.test='CheckinIT,PresencaManualIT'` → FAIL.
- [ ] **Step 3: Implementar** conforme Interfaces. `inserir` usa `saveAndFlush` dentro de `try` e traduz a violação dos índices `ux_checkin_*` (como `MatriculaRepositorio.inserir`).
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS.
- [ ] **Step 5: Commit** — `feat(frequencia): check-in por QR, presença manual e aulas abertas da recepção`

---

### Task 8: Histórico, correção e exclusão

**Files:**
- Create: `frequencia/aplicacao/ServicoHistorico.java`
- Modify: `ServicoCheckin` (corrigir, excluir), `CheckinsController`, `EventosFrequencia`, `CheckinRepositorioJpa` (listagem por JDBC, como `MatriculaRepositorioJpa`)
- Test: `test/.../frequencia/CorrecaoIT.java`, `test/.../frequencia/HistoricoIT.java`

**Interfaces:**
- Produces:
  - `POST /api/v1/checkins/{id}/excluir {motivo}` → 204; `POST /api/v1/checkins/{id}/corrigir {data, hora, horarioId?, motivo}` → 201 com o corpo do novo (mesmo formato da Task 7, sem `alerta`).
  - Original com `data < hoje − 30` → 409 `CHECKIN_FORA_DO_PRAZO`; não ATIVO → 409 `CHECKIN_NAO_ATIVO`; nova data/hora validadas por `Checkin.validarMomento`; aula pela escolha de §6.3 no momento novo; matrícula **não** reavaliada; duplicado → `CHECKIN_DUPLICADO`.
  - Eventos `checkin.excluido {checkinId, motivo}` e `checkin.corrigido {checkinAntigoId, checkinNovoId, aulaId, horarioId, data, hora, origem, pontualidade}`; auditoria `CHECKIN_EXCLUIDO`, `CHECKIN_CORRIGIDO`.
  - `GET /api/v1/checkins?alunoId=&horarioId=&de=&ate=&status=&page=&size=` → `Pagina` de `{id, alunoId, alunoNome, data, hora, origem, pontualidade, status, motivo, aula:{horarioId,tipo,professorNome,inicio}|null}`; padrões `de = hoje − 30`, `ate = hoje`, `status = ATIVO`; ordem `data desc, hora desc`; nomes via `Alunos.nomes` e `Agenda.aulas`.

- [ ] **Step 1: Testes que falham.**
  - `CorrecaoIT`: `excluirLiberaNovoCheckin` (excluir → QR de novo na mesma aula 201); `excluirSemMotivo400`; `excluirDuasVezes409`; `corrigirParaOutraAula` (19:05 na aula A → corrigir para 20:05 com `horarioId` B → antigo CORRIGIDO com `substituido_por`, novo na B, PONTUAL); `corrigirRecalculaPontualidade` (19:05 → 19:20 → ATRASADO); `corrigirNaoReavaliaMatricula` (matrícula VENCIDA depois do check-in → correção 201); `corrigirForaDoPrazo409` (registro de hoje − 31 inserido por `su`); `corrigirParaAulaOndeJaTem409`; `eventosEAuditoria`; `isolamento` (check-in da B → 404).
  - `HistoricoIT`: `padraoUltimos30DiasAtivos` (registro de − 31 dias e um EXCLUIDO fora da resposta padrão); `filtrosPorAlunoHorarioStatus`; `ordemMaisRecentePrimeiro`; `livreSemAula` (`aula == null`); `isolamento`.
- [ ] **Step 2: Rodar e ver falhar** — `-Dit.test='CorrecaoIT,HistoricoIT'` → FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS.
- [ ] **Step 5: Commit** — `feat(frequencia): histórico, correção e exclusão lógica de check-ins`

---

### Task 9: Frontend — serviço, Agenda, tolerância e "Meu QR Code"

**Files:**
- Create: `core/api/frequencia.ts` (+ `.spec.ts`), `paginas/admin/agenda/agenda-lista.ts`, `horario-form.ts` (+ specs), `paginas/aluno/meu-qr/meu-qr.ts` (+ spec)
- Modify: `package.json` (`qrcode`, `@types/qrcode`, `@zxing/browser`, `@zxing/library`), `app.routes.ts`, `layout/shell-admin`, `layout/shell-aluno`, `paginas/admin/academia` (+ spec), `core/api/modelos.ts`, `paginas/admin/rotulos.ts`

**Interfaces:**
- Produces:
  - `AgendaApi`: `listar(filtro?)`, `obter(id)`, `criar(dados)`, `alterar(id, dados)`, `excluir(id)`, `aulasAbertas(momento?: {data, hora})`.
  - `FrequenciaApi`: `checkinQr(token, horarioId?)`, `manual(dados)`, `listar(filtro, pagina, tamanho)`, `corrigir(id, dados)`, `excluir(id, motivo)`, `meuQr()`.
  - Modelos: `Horario`, `AulaAberta`, `ResultadoCheckin`, `CheckinListado`, `TokenQr`; `ROTULO_DIA_SEMANA` (1 → "Segunda" … 7 → "Domingo"), `ROTULO_ORIGEM`, `ROTULO_PONTUALIDADE`, `ROTULO_STATUS_CHECKIN` em `rotulos.ts`.
  - Rotas `/admin/agenda`, `/admin/agenda/novo`, `/admin/agenda/:id`, `/aluno/meu-qr`; menus "Agenda" (admin) e "Meu QR Code" (aluno).
- [ ] **Step 1: Testes que falham.**
  - `frequencia.spec.ts`: URLs e corpos de `checkinQr`, `aulasAbertas({data,hora})`, `listar` (params `de`, `ate`, `status`).
  - `agenda-lista.spec.ts`: agrupa por dia de segunda a domingo e mostra "19:00–20:00 · Gi · Ana"; `excluir` com 409 `HORARIO_COM_AULAS` mostra "Este horário já teve aulas; inative-o".
  - `horario-form.spec.ts`: fim ≤ início bloqueia o envio; autocomplete só lista professores ATIVOS.
  - `academia.spec.ts`: campo de tolerância enviado no PUT.
  - `meu-qr.spec.ts` (timers falsos do Vitest): pede token ao abrir e desenha; pede outro aos 30 s; contagem regressiva diminui; `visibilitychange` oculto para de pedir e visível pede na hora; falha na renovação mostra "Não foi possível atualizar o QR Code" e some o QR quando `expiraEm` passa.
- [ ] **Step 2: Rodar e ver falhar** — `npx ng test --watch=false` → FAIL.
- [ ] **Step 3: Implementar** (QR desenhado com `QRCode.toCanvas`; padrões visuais de `listas.scss`/`formularios.scss`).
- [ ] **Step 4: Rodar e ver passar** — `npx ng lint && npx ng test --watch=false && npx ng build` → tudo verde.
- [ ] **Step 5: Commit** — `feat(frontend): agenda de horários, tolerância de pontualidade e QR Code do aluno`

---

### Task 10: Frontend — Recepção e Frequência

**Files:**
- Create: `componentes/leitor-qr/leitor-qr.ts` (emite `(lido)` com o texto; usa `BrowserQRCodeReader` de `@zxing/browser`; emite `(semCamera)` quando a permissão falha), `paginas/admin/recepcao/recepcao.ts` (+ spec), `paginas/admin/frequencia/frequencia-lista.ts` (+ spec), `componentes/dialogo-presenca/` (+ spec), `componentes/dialogo-correcao/`
- Modify: `app.routes.ts` (`/admin/recepcao`, `/admin/frequencia`), `layout/shell-admin` (menus Recepção e Frequência), `paginas/admin/alunos/aluno-form` (link "Ver frequência" → `/admin/frequencia?alunoId=`)

**Interfaces:**
- Consumes: `AgendaApi`, `FrequenciaApi` (Task 9); `DialogoMotivo` existente.
- Produces: telas conforme design §10. Mensagens por `codigo`: `ALUNO_INATIVO` "Aluno inativo", `SEM_MATRICULA` "Aluno sem matrícula", `MATRICULA_NAO_ATIVA` "Matrícula {status}", `MATRICULA_NAO_INICIADA` "Matrícula começa em dd/MM/aaaa", `CHECKIN_DUPLICADO` "Check-in já registrado nesta aula", `AULA_AMBIGUA` "Escolha a aula", `AULA_NAO_ABERTA` "Esta aula não está aberta agora", `QR_EXPIRADO` "QR Code expirado — peça para atualizar", `QR_INVALIDO` "QR Code inválido", `QR_DE_OUTRA_ACADEMIA` "QR Code de outra academia", `ACADEMIA_INATIVA` "Academia inativa".
- [ ] **Step 1: Testes que falham** (`LeitorQr` substituído por um stub nos specs).
  - `recepcao.spec.ts`: uma aula aberta → selecionada automaticamente; duas → nenhuma selecionada e a leitura envia sem `horarioId` só se o operador não escolheu (e mostra "Escolha a aula" no 409); escolha enviada como `horarioId`; resultado verde (201 sem alerta), amarelo (com alerta "Cobrança atrasada há 3 dias"), vermelho (409 com a mensagem do `codigo`); mesmo token emitido 2× em menos de 5 s → 1 requisição; campo "colar token" envia; `semCamera` mostra aviso e mantém o campo; recarrega aulas abertas a cada 60 s.
  - `frequencia-lista.spec.ts`: lê `alunoId` da query string como filtro; "Excluir" abre `DialogoMotivo` e chama `excluir`; "Corrigir" abre o diálogo de correção.
  - `dialogo-presenca.spec.ts`: data padrão hoje (São Paulo) e hora padrão agora; ao mudar data/hora busca `aulasAbertas({data,hora})` e exige escolha quando há mais de uma.
- [ ] **Step 2: Rodar e ver falhar** — `npx ng test --watch=false` → FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar e ver passar** — `npx ng lint && npx ng test --watch=false && npx ng build` → verde.
- [ ] **Step 5: Commit** — `feat(frontend): recepção com leitura de QR e histórico de frequência`

---

### Task 11: Ponta a ponta e documentação

**Files:**
- Modify: `docs/02-requisitos-funcionais.md` (RF08/RF09), `04-modelo-de-dados.md`, `05-arquitetura.md`, `06-api.md`, `07-eventos.md`, `08-roadmap.md` (Fase 4 concluída), `09-decisoes-tecnicas.md` (ADRs: escolha da aula pela recepção; aula como cópia do horário e "aula criada manda"; token HMAC de 60 s), `README.md` (status), `docs/planejamento/fase-4-design.md` (correções feitas na implementação, se houver)

- [ ] **Step 1: Suítes completas** — `cd backend && ./mvnw -B clean verify` e `cd frontend && npx ng lint && npx ng test --watch=false && npx ng build` → tudo verde.
- [ ] **Step 2: Compose** — `docker compose up -d --build`; fluxo manual: admin cria professor, horário de hoje cobrindo a hora atual, aluno com matrícula paga → aluno entra em "Meu QR Code" → copiar o token (ou ler pela câmera em `localhost`) → Recepção registra PONTUAL; tópico `checkin.registrado` recebe a mensagem (`kafka-console-consumer`). Expected: todos os passos funcionam; nenhum erro no log do backend.
- [ ] **Step 3: Documentação** conforme Files.
- [ ] **Step 4: Commit** — `docs: documentação da Fase 4`
