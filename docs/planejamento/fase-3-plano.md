# LocGym — Fase 3 (Matrículas e pagamentos) — Plano de implementação

**Goal:** Matrícula de alunos em planos com cobrança recorrente, registro manual de pagamentos, abono, suspensão, cancelamento, inadimplência derivada e job diário multi-tenant — com eventos Kafka e as telas do painel admin.

**Architecture:** Dois módulos Spring Modulith novos: `pagamento` (cobranças) e `matricula` (matrícula, períodos, job diário, inadimplência), com dependência única `matricula → pagamento`; a quitação volta para `matricula` por evento Spring **síncrono** na mesma transação. O job percorre as academias ativas e roda cada uma numa transação própria com `TenantContext` daquela academia (`Perfil.SISTEMA`), sob ShedLock — o RLS nunca é contornado.

**Tech Stack:** o das Fases 1–2 + ShedLock (provider JDBC), Micrometer (contador de falhas do job).

**Spec:** `docs/planejamento/fase-3-design.md` (base: `docs/planejamento/design.md`, `docs/planejamento/fase-2-design.md`).

## Global Constraints

- Reaproveitar Fases 1–2: `EntidadeDeTenant` (`@TenantId`), `TenantContext`/`ContextoTenant`, `Auditoria`, `PublicadorEventos`, `Pagina<T>`, `RegraVioladaException(codigo, mensagem)`, `NaoEncontradoException`, `ValorInvalidoException`, `Cenarios`/`ClienteHttp`/`RelogioDeTeste` nos testes.
- Camadas `dominio` (sem Spring/JPA/Jackson) / `aplicacao` / `infraestrutura` / `web`; API pública de cada módulo **no pacote base** do módulo (padrão `identidade.ContasDeUsuario`); `ApplicationModules.verify()` e `ArquiteturaTest` verdes.
- Toda data de negócio ("hoje") vem de `Calendario.hoje()` = data em **`America/Sao_Paulo`** do `Clock` existente. Nunca `LocalDate.now()`.
- Padrões aprovados: antecedência **7** dias (1–30), carência **10** dias (0–60), uma cobrança por período, meses por periodicidade MENSAL 1 / TRIMESTRAL 3 / SEMESTRAL 6 / ANUAL 12; `dataInicio` entre hoje − 30 e hoje + 30.
- Job: `cron = "0 5 0 * * *"`, `zone = "America/Sao_Paulo"`, ShedLock `lockAtMostFor = 30m`.
- Rotas sob `/api/v1`, tenant + `ROLE_ADMIN`; recurso de outra academia → **404**; regra → **409** com `codigo`; validação → **400**.
- Eventos: tópicos `matricula.criada|renovada|vencida|suspensa|reativada|cancelada`, `pagamento.gerado|registrado|atrasado|cancelado`, versão 1, `dados` só com ids, valores e datas (nunca CPF/e-mail/nome).
- Valores monetários `numeric(10,2)` / `BigDecimal` escala 2; frontend em `pt-BR` (R$, `dd/MM/yyyy`).
- Commits Conventional Commits terminando com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Comandos: backend `cd backend && ./mvnw -B verify` (um IT: `-Dit.test=NomeIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`); frontend `cd frontend && npx ng lint && npx ng test --watch=false && npx ng build`.

## Decisão de implementação (registrada aqui, não no design)

- O design pede um "`Clock` de negócio separado em `America/Sao_Paulo`". Implementado como o bean **`Calendario`**, que lê o `Clock` existente e converte para `America/Sao_Paulo`. Assim `RelogioDeTeste` controla as duas coisas (tempo técnico e data de negócio) com um único relógio, e o fuso fica garantido num único lugar.

## Review Focus

1. **Virada do dia em UTC antes de São Paulo** (21h–23h59 em SP) → "hoje" continua sendo a data de SP: nenhuma cobrança atrasa nem matrícula vence um dia antes. *(Task 1: `hojeUsaFusoDeSaoPaulo`.)*
2. **Duplo clique em "Nova matrícula" ou "Registrar pagamento"** → um sucesso e um 409, nunca 500 nem duplicata (é o defeito adiado da Fase 2). *(Task 6: `matriculaConcorrenteUmSucessoUm409`; Task 5: `pagamentoConcorrenteUmSucessoUm409`.)*
3. **Plano inativado ou preço alterado depois da matrícula** → matrícula continua cobrando o valor copiado na criação. *(Task 7: `planoInativadoNaoAfetaCobranca`.)*
4. **Aluno marcado INATIVO com matrícula ativa** → as cobranças continuam (status independentes). *(Task 7: `alunoInativoContinuaSendoCobrado`.)*
5. **Data de pagamento no futuro ou anterior à criação da matrícula** → 400 com mensagem clara; pagamento antecipado (antes do vencimento) é aceito. *(Task 5: `dataPagamentoFutura400`, `pagamentoAntecipadoAceito`.)*

---

## Estrutura de arquivos (backend `src/main/java/com/locgym/`)

```
compartilhado/tempo/Calendario.java                      bean: hoje() em America/Sao_Paulo
compartilhado/tenancy/Perfil.java                        + SISTEMA
compartilhado/tenancy/ContextoTenant.java                usuarioId nulo só para SISTEMA; fabrica sistema(academiaId)
compartilhado/auditoria/AcaoAuditada.java                + ações da Fase 3
academia/Academias.java (+ aplicacao/ impl)              API pública: ativas(), parametrosCobranca(id)
aluno/Alunos.java (+ impl)                               API pública: situacao(alunoId)
plano/Planos.java (+ impl)                               API pública: paraMatricula(planoId)
pagamento/  Cobrancas.java, CobrancaQuitada.java, CobrancaResumo.java   (API pública)
            dominio/ Cobranca, StatusCobranca, FormaPagamento, CobrancaRepositorio
            aplicacao/ ServicoCobrancas   infraestrutura/ CobrancaJpa…, TopicosPagamento   web/ CobrancasController
matricula/  dominio/ Matricula, StatusMatricula, CalendarioDePeriodos, Periodo, MatriculaRepositorio
            aplicacao/ ServicoMatriculas, QuitacaoListener, RotinaDiaria, ServicoInadimplencia
            infraestrutura/ MatriculaJpa…, InadimplenciaJdbc, AgendadorRotinaDiaria, ShedLockConfig, TopicosMatricula
            web/ MatriculasController, InadimplenciaController, RotinaDiariaDevController (@Profile("dev"))
resources/db/migration/ V5__parametros_cobranca_academia.sql, V6__matricula_e_pagamento.sql
```

Frontend (`frontend/src/app/`): `core/api/financeiro.ts` (serviço HTTP + modelos), `paginas/admin/matriculas/` (lista, nova, detalhe), `paginas/admin/cobrancas/`, `paginas/admin/inadimplencia/`, `componentes/dialogo-motivo/`, `componentes/dialogo-pagamento/`; alterações em `aluno-form`, `academia`, `app.routes.ts`, `shell-admin`, `rotulos.ts`.

---

### Task 1: Fundamentos — calendário, perfil SISTEMA, parâmetros de cobrança e APIs públicas

**Files:**
- Create: `compartilhado/tempo/Calendario.java`, `academia/Academias.java`, `aluno/Alunos.java`, `plano/Planos.java` (+ implementações nos `aplicacao/` de cada módulo), `resources/db/migration/V5__parametros_cobranca_academia.sql`
- Modify: `compartilhado/tenancy/Perfil.java`, `compartilhado/tenancy/ContextoTenant.java`, `compartilhado/auditoria/AcaoAuditada.java`, `compartilhado/auditoria/infraestrutura/AuditoriaJdbc.java`, `academia/dominio/Academia.java`, `academia/web/DadosAcademiaRequisicao.java`, `academia/web/AcademiaResposta.java`, `academia/aplicacao/ServicoAcademia.java`
- Test: `test/.../compartilhado/tempo/CalendarioTest.java`, `test/.../academia/AcademiaIT.java` (estender), `test/.../compartilhado/auditoria/AuditoriaIT.java` (estender)

**Interfaces:**
- Produces:
  - `@Component public class Calendario { Calendario(Clock clock); LocalDate hoje(); static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo"); }`
  - `Perfil.SISTEMA`; `ContextoTenant.sistema(UUID academiaId)` → `(academiaId, SISTEMA, true, null)`; o construtor só aceita `usuarioId == null` quando `perfil == SISTEMA`.
  - `AcaoAuditada` += `MATRICULA_CRIADA, MATRICULA_RENOVADA, MATRICULA_SUSPENSA, MATRICULA_REATIVADA, MATRICULA_CANCELADA, COBRANCAS_ABONADAS, PAGAMENTO_REGISTRADO, COBRANCA_ABONADA, PARAMETROS_COBRANCA_ALTERADOS`.
  - `academia.Academias { List<UUID> ativas(); ParametrosCobranca parametrosCobranca(UUID academiaId); }` com `record ParametrosCobranca(int diasAntecedencia, int diasCarencia)` no mesmo pacote. (`academia` é global, sem RLS — leitura direta.)
  - `aluno.Alunos { Optional<SituacaoAluno> situacao(UUID alunoId); }` com `record SituacaoAluno(UUID id, String nome, boolean ativo)` — no contexto de tenant atual.
  - `plano.Planos { Optional<PlanoParaMatricula> paraMatricula(UUID planoId); }` com `record PlanoParaMatricula(UUID id, String nome, BigDecimal valor, String periodicidade, boolean ativo)` (`periodicidade` = nome do enum).
  - `PUT /api/v1/academia` aceita e `GET` devolve `diasAntecedenciaCobranca`, `diasCarencia`.

- [ ] **Step 1: Testes que falham.**
  - `CalendarioTest.hojeUsaFusoDeSaoPaulo`: `Clock.fixed(Instant.parse("2026-11-24T01:30:00Z"), UTC)` → `hoje() == 2026-11-23`; `"2026-11-24T03:00:00Z"` → `2026-11-24`.
  - `AuditoriaIT.sistemaGravaUsuarioNulo`: com `TenantContext.definir(ContextoTenant.sistema(acad))`, `registrar(...)` grava linha com `usuario_id is null`.
  - `ContextoTenant` (unit): `new ContextoTenant(a, ADMIN, true, null)` → `NullPointerException`; `sistema(a).usuarioId() == null`.
  - `AcademiaIT.parametrosDeCobrancaPadraoEEditaveis`: academia nova → `diasAntecedenciaCobranca == 7`, `diasCarencia == 10`; PUT com `3` e `0` → GET devolve 3 e 0; auditoria `PARAMETROS_COBRANCA_ALTERADOS` gravada; PUT com `0` antecedência ou `61` carência → 400.

- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B verify -Dit.test='AcademiaIT,AuditoriaIT' -Dtest='CalendarioTest'` → FAIL (classes/colunas inexistentes).

- [ ] **Step 3: Implementar.** `V5`: `alter table academia add column dias_antecedencia_cobranca int not null default 7 check (dias_antecedencia_cobranca between 1 and 30), add column dias_carencia int not null default 10 check (dias_carencia between 0 and 60);`. Auditoria de parâmetros só quando algum dos dois mudar (`antes`/`depois` com os dois valores). Implementações das três APIs públicas usam os repositórios já existentes de cada módulo.

- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS (suíte inteira, inclusive `ArquiteturaTest`).

- [ ] **Step 5: Commit** — `feat: calendário em America/Sao_Paulo, perfil SISTEMA, parâmetros de cobrança e APIs públicas de academia/aluno/plano`

---

### Task 2: Schema de matrícula e pagamento com RLS

**Files:**
- Create: `resources/db/migration/V6__matricula_e_pagamento.sql`
- Modify: `test/.../compartilhado/tenancy/RlsIT.java`

**Interfaces:**
- Produces: tabelas `matricula`, `matricula_periodo`, `pagamento` (de tenant, RLS + `FORCE`, mesma policy `tenant_isolamento` da V3, grants para `${app_usuario}`) e `shedlock` (global; grants `select, insert, update` para `${app_usuario}`). Colunas exatamente como no design §5, com:
  - `matricula.version bigint not null default 0`, `pagamento.version bigint not null default 0`;
  - `create unique index ux_matricula_aluno_nao_cancelada on matricula (academia_id, aluno_id) where status <> 'CANCELADA';`
  - `unique (matricula_id, periodo_inicio)` em `pagamento`;
  - `check ((status = 'PAGO') = (data_pagamento is not null and forma is not null))`, `check ((status = 'CANCELADO') = (motivo_cancelamento is not null))`;
  - `check (periodo_fim > periodo_inicio)`, `check (fim > inicio)` em `matricula_periodo`;
  - índices `(academia_id, status, vencimento)` e `(academia_id, aluno_id)` em `pagamento`; `(academia_id, status)` em `matricula`.

- [ ] **Step 1: Teste que falha** — em `RlsIT`, incluir `matricula`, `matricula_periodo` e `pagamento` no teste parametrizado existente de isolamento (sem academia → 0 linhas; academia A → só A; INSERT com `academia_id` de B → erro), e `shedlockAcessivelAoPapelDaAplicacao` (insert/select em `shedlock` como `locgym_app` funciona).
- [ ] **Step 2: Rodar e ver falhar** — `-Dit.test=RlsIT` → FAIL (tabelas inexistentes).
- [ ] **Step 3: Escrever a V6** conforme Interfaces.
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS.
- [ ] **Step 5: Commit** — `feat(db): tabelas de matrícula, períodos, pagamentos e shedlock com RLS`

---

### Task 3: Domínio da cobrança

**Files:**
- Create: `pagamento/dominio/Cobranca.java`, `StatusCobranca.java`, `FormaPagamento.java`, `CobrancaRepositorio.java`
- Test: `test/.../pagamento/dominio/CobrancaTest.java`

**Interfaces:**
- Produces:
  - `enum StatusCobranca { PENDENTE, PAGO, ATRASADO, CANCELADO; boolean emAberto() }` (`PENDENTE|ATRASADO`).
  - `enum FormaPagamento { DINHEIRO, PIX, CARTAO_CREDITO, CARTAO_DEBITO, TRANSFERENCIA }`.
  - `Cobranca`: `static Cobranca nova(UUID matriculaId, UUID alunoId, BigDecimal valor, LocalDate periodoInicio, LocalDate periodoFim, LocalDate hoje)` — `vencimento = periodoInicio`; nasce `ATRASADO` se `vencimento < hoje`, senão `PENDENTE`. Métodos: `void registrarPagamento(LocalDate data, FormaPagamento forma, String observacoes, LocalDate hoje)`, `void abonar(String motivo)`, `void cancelarPorMatricula()` (motivo fixo `"Matrícula cancelada"`, só se PENDENTE; ATRASADO fica), `boolean marcarAtrasadaSeVencida(LocalDate hoje)`, `void deslocar(int dias)` (só PENDENTE: vencimento e período). Getters no estilo `id()`, `matriculaId()`, `status()`, …
  - `CobrancaRepositorio { Optional<Cobranca> porId(UUID); List<Cobranca> daMatricula(UUID matriculaId); Optional<Cobranca> maisAntigaEmAberto(UUID matriculaId); boolean existePeriodo(UUID matriculaId, LocalDate periodoInicio); List<Cobranca> pendentesVencidas(LocalDate hoje, Set<UUID> matriculasIgnoradas); void salvar(Cobranca); Pagina<Cobranca> listar(FiltroCobrancas, int pagina, int tamanho); }` com `record FiltroCobrancas(StatusCobranca status, LocalDate vencimentoDe, LocalDate vencimentoAte, UUID alunoId)`.

- [ ] **Step 1: Testes que falham** (`CobrancaTest`):
  - `novaComVencimentoFuturoNascePendente` / `novaComVencimentoPassadoNasceAtrasada` (`hoje = 2026-11-23`, início `2026-11-22` → ATRASADO).
  - `registrarPagamento`: PENDENTE → PAGO com data, forma e observações (`strip`, vazio → null).
  - `dataPagamentoFutura400`: `data = hoje + 1` → `ValorInvalidoException` campo `dataPagamento`.
  - `pagamentoAntecipadoAceito`: data antes do vencimento → PAGO.
  - `naoPagaCobrancaPagaOuCancelada` → `RegraVioladaException` código `COBRANCA_NAO_EM_ABERTO`.
  - `abonarExigeMotivo` (vazio ou > 300 caracteres → `ValorInvalidoException` campo `motivo`); `abonar` ATRASADO → CANCELADO com motivo.
  - `cancelarPorMatriculaPreservaAtrasada`: PENDENTE → CANCELADO; ATRASADO continua ATRASADO.
  - `marcarAtrasada`: no dia do vencimento → `false`; no dia seguinte → `true`, status ATRASADO; já ATRASADO → `false`.
  - `deslocarSoPendente`: PENDENTE desloca vencimento/período em +12 dias; ATRASADO → `IllegalStateException`.
- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest=CobrancaTest` → FAIL (compilação).
- [ ] **Step 3: Implementar** conforme Interfaces (sem Spring/JPA no `dominio`).
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B test -Dtest='CobrancaTest,ArquiteturaTest'` → PASS.
- [ ] **Step 5: Commit** — `feat(pagamento): domínio da cobrança`

---

### Task 4: Domínio da matrícula e calendário de períodos

**Files:**
- Create: `matricula/dominio/Matricula.java`, `StatusMatricula.java`, `CalendarioDePeriodos.java`, `Periodo.java`, `MatriculaRepositorio.java`
- Test: `test/.../matricula/dominio/CalendarioDePeriodosTest.java`, `MatriculaTest.java`

**Interfaces:**
- Produces:
  - `record Periodo(LocalDate inicio, LocalDate fim)` (fim exclusivo).
  - `final class CalendarioDePeriodos { static int meses(String periodicidade); static Periodo periodoQueComecaEm(LocalDate dataBase, int meses, LocalDate inicio); }` — encontra o `n` tal que `dataBase.plusMonths(n*meses) == inicio` e devolve `[inicio, dataBase.plusMonths((n+1)*meses))`; `IllegalArgumentException` se `inicio` não for um limite.
  - `enum StatusMatricula { PENDENTE, ATIVA, VENCIDA, SUSPENSA, CANCELADA }`.
  - `Matricula`: `static Matricula nova(UUID alunoId, UUID planoId, BigDecimal valor, String periodicidade, LocalDate dataInicio, LocalDate hoje)` (valida janela ±30 dias → `ValorInvalidoException` campo `dataInicio`; `dataBase = vigenteAte = dataInicio`; PENDENTE). Métodos:
    - `Periodo primeiroPeriodo()`; `Periodo proximoPeriodo()` (começa em `vigenteAte`).
    - `QuitacaoAplicada aplicarQuitacao(Periodo periodo, boolean restamAtrasadas, LocalDate hoje)` — exige `periodo.inicio == vigenteAte` (senão `IllegalStateException`); `vigenteAte = periodo.fim`; transição PENDENTE/VENCIDA → ATIVA só se `!restamAtrasadas && vigenteAte.isAfter(hoje)`; SUSPENSA continua SUSPENSA; CANCELADA → `IllegalStateException` (o listener nem chama). Retorna `record QuitacaoAplicada(boolean primeira, boolean ativou)`.
    - `List<Periodo> periodosAGerar(LocalDate inicioJaCobradoAte, int diasAntecedencia, LocalDate hoje)` — a partir de `inicioJaCobradoAte` (o início do primeiro período ainda sem cobrança), todos os períodos com `inicio.minusDays(diasAntecedencia) <= hoje`, em ordem.
    - `void suspender(String motivo, LocalDate hoje)` (só ATIVA → `RegraVioladaException MATRICULA_NAO_ATIVA`); `int reativar(LocalDate hoje)` (só SUSPENSA; retorna `dias`; desloca `dataBase` e `vigenteAte`); `void cancelar(String motivo)` (qualquer exceto CANCELADA → `MATRICULA_JA_CANCELADA`); `boolean vencerSe(boolean temAtrasadaAlemDaCarencia)` (só ATIVA).
    - Getters `id()`, `alunoId()`, `planoId()`, `valor()`, `periodicidade()`, `dataInicio()`, `dataBase()`, `vigenteAte()`, `status()`, `suspensaDesde()`, `motivo()`.
  - `MatriculaRepositorio { Optional<Matricula> porId(UUID); Optional<Matricula> naoCanceladaDoAluno(UUID alunoId); List<Matricula> porStatus(StatusMatricula); void salvar(Matricula); void registrarPeriodo(UUID matriculaId, Periodo, UUID cobrancaId); void deslocarPeriodosDesde(UUID matriculaId, LocalDate desde, int dias); List<Periodo> periodos(UUID matriculaId); Pagina<Matricula> listar(StatusMatricula statusOuNulo, String busca, int pagina, int tamanho); }`

- [ ] **Step 1: Testes que falham.**
  - `CalendarioDePeriodosTest`: `fimDeMesSemDeriva` (base 2026-01-31, mensal: períodos começam em 01-31, 02-28, 03-31, 04-30); `bissextoAnual` (base 2028-02-29, anual → próximo 2029-02-28, depois 2030-02-28); `trimestral` (base 2026-11-23 → 2027-02-23 → 2027-05-23); `inicioForaDoCalendario` → `IllegalArgumentException`; `meses("SEMESTRAL") == 6`.
  - `MatriculaTest`:
    - `novaNascePendente` (`dataBase == vigenteAte == dataInicio`); `dataInicioForaDaJanela400` (hoje − 31 e hoje + 31 → erro; −30 e +30 ok).
    - `primeiraQuitacaoAtiva`: quitação do primeiro período → ATIVA, `vigenteAte = inicio + 1 mês`, `primeira == true`.
    - `pagaAntesDoInicioFicaAtiva` (início hoje + 10).
    - `vencidaComVigenciaNoPassadoContinuaVencida`: VENCIDA, vigência 2026-08-10, quita período [08-10, 09-10) com hoje 2026-11-15 → continua VENCIDA, `ativou == false`.
    - `vencidaQuitadaAteHojeVoltaAtiva`.
    - `restandoAtrasadaNaoAtiva`.
    - `periodosAGerarRecuperaMesesParados`: vigência 2026-09-10, antecedência 7, hoje 2026-11-15, `inicioJaCobradoAte = 09-10` → períodos começando em 09-10, 10-10, 11-10 (e **não** 12-10, pois 12-03 > hoje).
    - `periodosAGerarUmaSoNormalmente`: vigência 2026-12-01, hoje 2026-11-24 → um período (12-01); hoje 2026-11-23 → nenhum.
    - `suspenderEReativarDeslocaDatas`: suspensa em 2026-11-01, reativada em 2026-11-13 → `dias == 12`, `dataBase` e `vigenteAte` + 12, ATIVA.
    - `suspenderSoAtiva` / `reativarSoSuspensa` / `cancelarDuasVezes409`.
    - `quitacaoEmSuspensaAvancaVigenciaMantemStatus`.
    - `vencerSoAtiva`: ATIVA + `true` → VENCIDA; PENDENTE + `true` → continua PENDENTE.
- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest='CalendarioDePeriodosTest,MatriculaTest'` → FAIL.
- [ ] **Step 3: Implementar** conforme Interfaces.
- [ ] **Step 4: Rodar e ver passar** — mesmo comando + `ArquiteturaTest` → PASS.
- [ ] **Step 5: Commit** — `feat(matricula): domínio da matrícula e calendário de períodos com âncora`

---

### Task 5: Módulo `pagamento` — persistência, API pública, quitação e endpoints

**Files:**
- Create: `pagamento/Cobrancas.java`, `pagamento/CobrancaQuitada.java`, `pagamento/CobrancaResumo.java`, `pagamento/aplicacao/ServicoCobrancas.java`, `pagamento/infraestrutura/CobrancaJpa.java`, `CobrancaJpaDados.java`, `CobrancaRepositorioJpa.java`, `TopicosPagamento.java`, `pagamento/web/CobrancasController.java` (+ DTOs), eventos `pagamento/aplicacao/eventos/*.java`
- Modify: `compartilhado/web/TratadorGlobalDeErros.java` (`ObjectOptimisticLockingFailureException` e `DataIntegrityViolationException` de índice único conhecido → 409)
- Test: `test/.../pagamento/CobrancasIT.java`

**Interfaces:**
- Consumes: `Cobranca`, `CobrancaRepositorio` (Task 3); `Calendario`, `Auditoria`, `PublicadorEventos`.
- Produces:
  - `pagamento.Cobrancas` (API usada por `matricula`):
    - `CobrancaResumo gerar(UUID matriculaId, UUID alunoId, BigDecimal valor, LocalDate periodoInicio, LocalDate periodoFim)` — idempotente: se o período já existe, devolve o existente sem evento; senão cria e publica `pagamento.gerado` (+ `pagamento.atrasado` se nascer ATRASADO).
    - `List<CobrancaResumo> daMatricula(UUID matriculaId)`; `boolean temAtrasada(UUID matriculaId)`; `Optional<LocalDate> vencimentoAtrasadoMaisAntigo(UUID matriculaId)`; `LocalDate inicioDoProximoPeriodoSemCobranca(UUID matriculaId, LocalDate aPartirDe)` — maior `periodo_fim` entre as cobranças não canceladas-por-matrícula da matrícula, ou `aPartirDe` se não houver nenhuma; `boolean temEmAberto(UUID matriculaId)`.
    - `int marcarAtrasadas(Set<UUID> matriculasSuspensas)` (publica `pagamento.atrasado` por cobrança); `void cancelarPendentesDaMatricula(UUID matriculaId)`; `void deslocarPendentes(UUID matriculaId, int dias)`.
    - `List<UUID> abonarAte(UUID matriculaId, UUID ateCobrancaId, String motivo)` — abona em ordem; publica `CobrancaQuitada` por cobrança; 404 se a cobrança não for da matrícula; 409 `COBRANCA_NAO_EM_ABERTO` se não estiver em aberto.
  - `record CobrancaResumo(UUID id, UUID matriculaId, UUID alunoId, BigDecimal valor, LocalDate vencimento, LocalDate periodoInicio, LocalDate periodoFim, String status, LocalDate dataPagamento, String forma, String observacoes, String motivoCancelamento)`.
  - `record CobrancaQuitada(UUID cobrancaId, UUID matriculaId, LocalDate periodoInicio, LocalDate periodoFim, boolean abono)` — publicada com `ApplicationEventPublisher` **antes** de retornar, dentro da transação.
  - `ServicoCobrancas.registrarPagamento(UUID cobrancaId, LocalDate data, FormaPagamento forma, String observacoes)` e `abonar(UUID cobrancaId, String motivo)` — ambos exigem que a cobrança seja a **mais antiga em aberto** da matrícula (senão 409 `QUITAR_MAIS_ANTIGA_PRIMEIRO`, mensagem "Quite primeiro a cobrança de dd/MM/yyyy"); publicam `pagamento.registrado` / `pagamento.cancelado`, auditam `PAGAMENTO_REGISTRADO` / `COBRANCA_ABONADA`.
  - Endpoints: `GET /api/v1/cobrancas?status=&vencimentoDe=&vencimentoAte=&alunoId=&pagina=&tamanho=` → `Pagina<CobrancaResposta>` (com `alunoNome` via `aluno.Alunos`); `POST /api/v1/cobrancas/{id}/pagamento {dataPagamento, forma, observacoes?}` → 200 `CobrancaResposta`; `POST /api/v1/cobrancas/{id}/cancelar {motivo}` → 200.
  - `TopicosPagamento`: beans `NewTopic` para os 4 tópicos `pagamento.*`.
  - Eventos (records `implements EventoDominio`, `versao() = 1`): `PagamentoGerado(academiaId, cobrancaId, matriculaId, alunoId, valor, vencimento)`, `PagamentoRegistrado(…, dataPagamento, forma)`, `PagamentoAtrasado(…, vencimento)`, `PagamentoCancelado(…, motivo)`.

- [ ] **Step 1: Testes que falham** (`CobrancasIT`; criar cobranças via `Cobrancas.gerar` dentro de `TenantContext` de teste — a matrícula ainda não existe como módulo, então inserir uma linha mínima em `matricula` via `jdbc` com o papel de teste):
  - `geraIdempotente`: `gerar` 2× mesmo período → 1 linha, 1 evento `pagamento.gerado` no outbox.
  - `registrarPagamentoPagaEPublica`: 200; status PAGO; outbox tem `pagamento.registrado`; auditoria `PAGAMENTO_REGISTRADO`; listener de teste (`@EventListener` num `@TestConfiguration`) recebeu `CobrancaQuitada(abono=false)` **dentro** da transação (`TransactionSynchronizationManager.isActualTransactionActive()`).
  - `quitarForaDeOrdem409`: duas cobranças em aberto → pagar a mais nova → 409 `QUITAR_MAIS_ANTIGA_PRIMEIRO`.
  - `dataPagamentoFutura400`, `pagamentoAntecipadoAceito`, `formaInvalida400`.
  - `abonarExigeMotivoEPublica`: sem motivo → 400; com motivo → CANCELADO, `pagamento.cancelado`, `CobrancaQuitada(abono=true)`.
  - `pagamentoConcorrenteUmSucessoUm409`: duas threads pagando a mesma cobrança → status 200 e 409 (`CONFLITO_DE_ATUALIZACAO` ou `COBRANCA_NAO_EM_ABERTO`), uma única linha PAGO, um único `pagamento.registrado`.
  - `listarComFiltros`: por status e por intervalo de vencimento; `alunoNome` preenchido.
  - `isolamento`: admin da academia B → GET/POST da cobrança de A → 404.
  - `eventosSemDadosPessoais`: payload de cada evento não contém CPF nem e-mail do aluno.
- [ ] **Step 2: Rodar e ver falhar** — `-Dit.test=CobrancasIT` → FAIL.
- [ ] **Step 3: Implementar.** `CobrancaJpa extends EntidadeDeTenant` com `@Version`. Conflito otimista → 409 código `CONFLITO_DE_ATUALIZACAO`, mensagem "Registro alterado por outra operação, recarregue". Violação de `ux_matricula_aluno_nao_cancelada` → 409 `MATRICULA_JA_EXISTE`; de `pagamento_matricula_id_periodo_inicio_key` → tratada dentro de `gerar` (idempotência).
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS.
- [ ] **Step 5: Commit** — `feat(pagamento): cobranças, registro de pagamento, abono e eventos`

---

### Task 6: Módulo `matricula` — casos de uso, quitação síncrona e endpoints

**Files:**
- Create: `matricula/aplicacao/ServicoMatriculas.java`, `matricula/aplicacao/QuitacaoListener.java`, `matricula/aplicacao/RecuperacaoDePeriodos.java`, `matricula/infraestrutura/MatriculaJpa.java`, `MatriculaJpaDados.java`, `MatriculaPeriodoJpa…`, `MatriculaRepositorioJpa.java`, `TopicosMatricula.java`, `matricula/web/MatriculasController.java` (+ DTOs), eventos `matricula/aplicacao/eventos/*.java`
- Test: `test/.../matricula/MatriculasIT.java`

**Interfaces:**
- Consumes: `Matricula`, `CalendarioDePeriodos`, `MatriculaRepositorio` (Task 4); `pagamento.Cobrancas`, `CobrancaQuitada` (Task 5); `aluno.Alunos`, `plano.Planos`, `academia.Academias` (Task 1).
- Produces:
  - `RecuperacaoDePeriodos.gerarFaltantes(Matricula m, LocalDate hoje)` — usado pelo listener **e** pelo job: `inicio = cobrancas.inicioDoProximoPeriodoSemCobranca(m.id(), m.vigenteAte())`; para cada `m.periodosAGerar(inicio, antecedencia, hoje)` chama `cobrancas.gerar(...)`. Só para matrícula ATIVA (no job) ou logo após quitação (no listener, se não CANCELADA nem SUSPENSA).
  - `QuitacaoListener.aoQuitar(CobrancaQuitada e)` — `@EventListener` síncrono: matrícula CANCELADA → não faz nada; senão `aplicarQuitacao`, `registrarPeriodo`, salva, publica `matricula.renovada` (se `!primeira`), depois `gerarFaltantes` e, se ainda restar atrasada, mantém status (o `aplicarQuitacao` já considerou).
  - `ServicoMatriculas`: `criar(UUID alunoId, UUID planoId, LocalDate dataInicio, BigDecimal valorOuNulo)`, `buscar(UUID)`, `listar(...)`, `daAluno(UUID alunoId)`, `renovar(UUID)`, `suspender(UUID, String motivo)`, `reativar(UUID)`, `cancelar(UUID, String motivo)`, `abonar(UUID matriculaId, UUID ateCobrancaId, String motivo)`.
  - Erros de criação: aluno inexistente → 404; aluno não ATIVO → 409 `ALUNO_NAO_ATIVO`; plano não ATIVO → 409 `PLANO_NAO_ATIVO`; já tem matrícula → 409 `MATRICULA_JA_EXISTE`. Renovar: não ATIVA → 409 `MATRICULA_NAO_ATIVA`; já há cobrança em aberto → 409 `COBRANCA_EM_ABERTO`.
  - Endpoints: `GET /api/v1/matriculas?status=&busca=&pagina=&tamanho=`; `POST /api/v1/matriculas {alunoId, planoId, dataInicio, valor?}` → 201 `MatriculaDetalhe`; `GET /api/v1/matriculas/{id}` → `MatriculaDetalhe {id, aluno{id,nome}, plano{id,nome}, valor, periodicidade, dataInicio, vigenteAte, status, suspensaDesde, motivo, inadimplente, periodos[], cobrancas[]}`; `POST .../{id}/renovar`, `.../suspender {motivo}`, `.../reativar`, `.../cancelar {motivo}`, `.../abonar {ateCobrancaId, motivo}` → 200 `MatriculaDetalhe`; `GET /api/v1/alunos/{id}/matricula` → `MatriculaDetalhe` ou 404.
  - `TopicosMatricula`: `NewTopic` para os 6 tópicos `matricula.*`. Eventos: `MatriculaCriada(academiaId, matriculaId, alunoId, planoId, valor, dataInicio)`, `MatriculaRenovada(…, vigenteAte)`, `MatriculaVencida(…, vigenteAte)`, `MatriculaSuspensa(…, motivo)`, `MatriculaReativada(…, dias, vigenteAte)`, `MatriculaCancelada(…, motivo)`.

- [ ] **Step 1: Testes que falham** (`MatriculasIT`, com `RelogioDeTeste`):
  - `criarGeraPrimeiraCobrancaEEventos`: 201, PENDENTE, 1 cobrança PENDENTE vencendo em `dataInicio`; outbox `matricula.criada` + `pagamento.gerado`; auditoria `MATRICULA_CRIADA`.
  - `valorPadraoDoPlanoOuInformado`: sem valor → valor do plano; com `99.90` → `99.90`.
  - `criarRegras`: aluno INATIVO → 409 `ALUNO_NAO_ATIVO`; plano INATIVO → 409 `PLANO_NAO_ATIVO`; segunda matrícula → 409 `MATRICULA_JA_EXISTE`; depois de cancelar a primeira → nova permitida.
  - `matriculaConcorrenteUmSucessoUm409`: duas threads criando para o mesmo aluno → 201 e 409, uma linha.
  - `pagarPrimeiraAtiva`: pagar a primeira → ATIVA, `vigenteAte = dataInicio + 1 mês`, 1 período registrado, sem `matricula.renovada`.
  - `cicloDeTresMeses`: pagar, avançar o relógio, renovar/pagar 2× → 3 períodos contíguos, 2 `matricula.renovada`.
  - `voltaDepoisDeTresMeses` (Review do design): matrícula VENCIDA com atrasada de 08-10, relógio em 11-15 → pagar a de 08-10 → nascem 09-10, 10-10, 11-10 (ATRASADO), continua VENCIDA → `abonar` até a de 10-10 → pagar a de 11-10 → ATIVA, `vigenteAte = 12-10`; auditoria `COBRANCAS_ABONADAS` única.
  - `rollbackDoListenerDesfazPagamento`: forçar exceção no listener (bean de teste) → POST pagamento 500 e cobrança continua em aberto.
  - `suspenderEReativar`: suspender (motivo obrigatório), avançar 12 dias, reativar → `vigenteAte` + 12, cobrança pendente deslocada + 12, períodos com início ≥ `suspensaDesde` deslocados; eventos `matricula.suspensa`/`reativada`.
  - `pagarDuranteSuspensao`: cobrança atrasada de matrícula suspensa pode ser paga; status continua SUSPENSA; nenhuma cobrança nova gerada.
  - `cancelarPreservaAtrasadas`: PENDENTE → CANCELADO, ATRASADO preservado; pagar a atrasada depois → matrícula intocada, só `pagamento.registrado`.
  - `renovarManual`: gera a do próximo período já; com cobrança em aberto → 409 `COBRANCA_EM_ABERTO`.
  - `matriculaDoAluno`: `GET /alunos/{id}/matricula` → detalhe; aluno sem matrícula → 404.
  - `isolamento`: admin de B → todos os endpoints com id de A → 404.
- [ ] **Step 2: Rodar e ver falhar** — `-Dit.test=MatriculasIT` → FAIL.
- [ ] **Step 3: Implementar.** `MatriculaJpa extends EntidadeDeTenant` com `@Version`. Auditar `MATRICULA_CRIADA`, `_RENOVADA` (só renovação manual), `_SUSPENSA`, `_REATIVADA`, `_CANCELADA`, `COBRANCAS_ABONADAS` (uma linha com a lista de ids).
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS (inclui `ApplicationModules.verify()` sem ciclo).
- [ ] **Step 5: Commit** — `feat(matricula): matrículas, quitação síncrona, recuperação de períodos e endpoints`

---

### Task 7: Rotina diária multi-tenant e inadimplência

**Files:**
- Create: `matricula/aplicacao/RotinaDiaria.java`, `matricula/infraestrutura/AgendadorRotinaDiaria.java`, `matricula/infraestrutura/ShedLockConfig.java`, `matricula/web/RotinaDiariaDevController.java`, `matricula/aplicacao/ServicoInadimplencia.java`, `matricula/infraestrutura/InadimplenciaJdbc.java`, `matricula/web/InadimplenciaController.java`
- Modify: `backend/pom.xml` (ShedLock `shedlock-spring` + `shedlock-provider-jdbc-template`)
- Test: `test/.../matricula/RotinaDiariaIT.java`, `test/.../matricula/InadimplenciaIT.java`

**Interfaces:**
- Consumes: `academia.Academias.ativas()/parametrosCobranca()`, `RecuperacaoDePeriodos`, `pagamento.Cobrancas`, `MatriculaRepositorio`, `Calendario`.
- Produces:
  - `RotinaDiaria.executar(): ResultadoRotina` — para cada `academiaId` de `ativas()`: `TenantContext.definir(ContextoTenant.sistema(id))`, `TransactionTemplate` → passos 1–3 do design §6.4 (gerar para ATIVAS via `gerarFaltantes`; `marcarAtrasadas(suspensas)`; vencer ATIVAS cujo `vencimentoAtrasadoMaisAntigo + carencia < hoje` → `matricula.vencida`); `finally TenantContext.limpar()`. Exceção numa academia → log `error` + `Counter locgym.rotina_diaria.falhas` com tag `academia` e continua. `record ResultadoRotina(int academias, int falhas, int cobrancasGeradas, int cobrancasAtrasadas, int matriculasVencidas)`.
  - `AgendadorRotinaDiaria`: `@Scheduled(cron = "0 5 0 * * *", zone = "America/Sao_Paulo") @SchedulerLock(name = "rotina-diaria", lockAtMostFor = "30m")`; desligável por `locgym.rotina-diaria.habilitada` (false nos testes).
  - `POST /api/v1/dev/rotina-diaria` (`@Profile("dev")`, ADMIN autenticado, **sem** tenant) → `ResultadoRotina`.
  - `GET /api/v1/inadimplencia?busca=&pagina=&tamanho=` → `Pagina<Inadimplente>` com `record Inadimplente(UUID alunoId, String nome, String cpf, BigDecimal totalEmAtraso, int cobrancasAtrasadas, LocalDate vencimentoMaisAntigo, int diasDeAtraso, UUID matriculaId, String statusMatricula)`, ordenado por `diasDeAtraso desc, nome`. Consulta SQL de leitura (`InadimplenciaJdbc`, com RLS ativo) juntando `pagamento`, `aluno` e `matricula` — modelo de leitura documentado no ADR.

- [ ] **Step 1: Testes que falham.**
  - `RotinaDiariaIT` (relógio controlado; duas academias A e B):
    - `geraSeteDiasAntes`: vigência 12-01; hoje 11-23 → nada; 11-24 → 1 cobrança.
    - `rodarDuasVezesNaoDuplica` e `execucoesSimultaneasNaoDuplicam` (2 threads chamando `executar()`).
    - `atrasaNoDiaSeguinte`: vencimento 12-01 → em 12-01 PENDENTE; em 12-02 ATRASADO + `pagamento.atrasado`.
    - `venceAposCarencia`: atrasada de 12-01, carência 10 → em 12-11 ATIVA; em 12-12 VENCIDA + `matricula.vencida`.
    - `respeitaParametrosDaAcademia`: A com antecedência 3 e carência 0, B com padrão → comportamentos distintos no mesmo dia.
    - `ignoraSuspensaEAcademiaInativa`.
    - `pendenteNaoVence`.
    - `planoInativadoNaoAfetaCobranca` e `alunoInativoContinuaSendoCobrado` (Review Focus 3 e 4): valor da nova cobrança = valor da matrícula; aluno INATIVO ainda recebe cobrança.
    - `academiaReativadaRecuperaPeriodos`: academia inativa por 3 meses, reativada → próxima execução gera os períodos faltantes (ATRASADO) e vence a matrícula.
    - `falhaNumaAcademiaNaoParaAsOutras`: forçar exceção para A (bean espião em `Cobrancas`) → B processada; `falhas == 1`; contador incrementado.
    - `cadaAcademiaSoVeAsProprias`: durante o processamento de A, `select count(*) from matricula` retorna só as de A.
  - `InadimplenciaIT`: `listaAtrasadosOrdenados` (2 alunos, dias de atraso 30 e 5 → ordem 30, 5; total e quantidade corretos); `incluiMatriculaCancelada`; `buscaPorNome`; `isolamento` (B não vê A).
- [ ] **Step 2: Rodar e ver falhar** — `-Dit.test='RotinaDiariaIT,InadimplenciaIT'` → FAIL.
- [ ] **Step 3: Implementar** conforme Interfaces; `ShedLockConfig` com `@EnableSchedulerLock(defaultLockAtMostFor = "30m")` e `JdbcTemplateLockProvider` (`usingDbTime()`).
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS.
- [ ] **Step 5: Commit** — `feat(matricula): rotina diária multi-tenant com ShedLock e consulta de inadimplência`

---

### Task 8: Frontend — serviço financeiro e telas de matrícula

**Files:**
- Create: `core/api/financeiro.ts` (+ `.spec.ts`), `paginas/admin/matriculas/matriculas-lista.ts` (+ spec), `nova-matricula.ts` (+ spec), `matricula-detalhe.ts` (+ spec), `componentes/dialogo-motivo/dialogo-motivo.ts`, `componentes/dialogo-pagamento/dialogo-pagamento.ts` (+ spec)
- Modify: `app.routes.ts`, `layout/shell-admin/shell-admin.ts` (menu: Matrículas, Cobranças, Inadimplência), `paginas/admin/rotulos.ts` (rótulos e cores de status)

**Interfaces:**
- Consumes: endpoints das Tasks 5–7.
- Produces:
  - `@Injectable FinanceiroApi`: `listarMatriculas(f)`, `criarMatricula(d)`, `matricula(id)`, `matriculaDoAluno(alunoId)`, `renovar(id)`, `suspender(id, motivo)`, `reativar(id)`, `cancelar(id, motivo)`, `abonar(id, ateCobrancaId, motivo)`, `listarCobrancas(f)`, `registrarPagamento(id, d)`, `abonarCobranca(id, motivo)`, `inadimplentes(f)` — todos `Observable<…>` tipados em `financeiro.ts`.
  - `DialogoMotivo` (`MatDialog`; dado `{titulo, confirmar}`; retorna `string | undefined`; motivo obrigatório, máx. 300) e `DialogoPagamento` (dado `{cobranca}`; retorna `{dataPagamento, forma, observacoes}`; data padrão = hoje, não aceita futura).
  - Rotas: `admin/matriculas`, `admin/matriculas/nova`, `admin/matriculas/:id`.

- [ ] **Step 1: Testes que falham.**
  - `financeiro.spec.ts`: `criarMatricula` faz POST em `/api/v1/matriculas` com o corpo; `registrarPagamento` POST em `/api/v1/cobrancas/{id}/pagamento`.
  - `nova-matricula.spec.ts`: `valorPreenchidoComValorDoPlano` (selecionar plano → campo valor = valor do plano); `dataInicioForaDaJanelaInvalida` (hoje + 31 → erro "Até 30 dias antes ou depois de hoje"); `conflito409MostraMensagem` (resposta 409 `MATRICULA_JA_EXISTE` → mensagem do backend visível).
  - `matricula-detalhe.spec.ts`: `acoesConformeStatus` (ATIVA → Renovar, Suspender, Cancelar; SUSPENSA → Reativar, Cancelar; CANCELADA → nenhuma); `pagarSoNaMaisAntiga` (botão "Registrar pagamento" habilitado só na cobrança em aberto mais antiga); `abonarAteAqui` (ação de abono em lote disponível nas cobranças em aberto quando há mais de uma).
  - `dialogo-pagamento.spec.ts`: `dataFuturaInvalida`; `padraoHoje`.
  - `matriculas-lista.spec.ts`: `filtraPorStatus` (chama a API com `status=VENCIDA`).
- [ ] **Step 2: Rodar e ver falhar** — `cd frontend && npx ng test --watch=false` → FAIL.
- [ ] **Step 3: Implementar.** Nova matrícula: autocomplete de alunos ATIVOS (API de alunos existente com `status=ATIVO`), select de planos ATIVOS, data (padrão hoje), valor. Detalhe: cartão com status (chip colorido), vigente até ("Começa em dd/MM" se `dataInicio > hoje`), tabela de períodos e de cobranças com ações; confirmação antes de cancelar.
- [ ] **Step 4: Rodar e ver passar** — `npx ng lint && npx ng test --watch=false && npx ng build` → verdes.
- [ ] **Step 5: Commit** — `feat(frontend): telas de matrícula com ações, pagamento e abono`

---

### Task 9: Frontend — cobranças, inadimplência, ficha do aluno e parâmetros da academia

**Files:**
- Create: `paginas/admin/cobrancas/cobrancas-lista.ts` (+ spec), `paginas/admin/inadimplencia/inadimplencia.ts` (+ spec)
- Modify: `paginas/admin/alunos/aluno-form.ts` (+ spec), `paginas/admin/academia/academia.ts`, `core/api/cadastros.ts`/`modelos.ts` (campos de parâmetros), `app.routes.ts`

**Interfaces:**
- Consumes: `FinanceiroApi`, `DialogoPagamento`, `DialogoMotivo` (Task 8).
- Produces: rotas `admin/cobrancas`, `admin/inadimplencia`.

- [ ] **Step 1: Testes que falham.**
  - `cobrancas-lista.spec.ts`: `filtrosEnviadosAApi` (status + intervalo de vencimento); `registrarPagamentoRecarrega` (após o diálogo confirmar, chama a API e recarrega a lista); `erro409QuitarMaisAntiga` mostra a mensagem do backend.
  - `inadimplencia.spec.ts`: `mostraTotalEDias` (R$ formatado em pt-BR, "30 dias"); `linkParaMatricula`.
  - `aluno-form.spec.ts`: `cartaoMatriculaAtual` (com matrícula → status, vigente até e "Inadimplente" quando aplicável); `semMatriculaOfereceMatricular` (404 → botão "Matricular" levando a `admin/matriculas/nova?alunoId=`).
- [ ] **Step 2: Rodar e ver falhar** — `npx ng test --watch=false` → FAIL.
- [ ] **Step 3: Implementar**; na tela da Academia, campos "Gerar cobrança com antecedência de (dias)" (1–30) e "Carência até vencer a matrícula (dias)" (0–60).
- [ ] **Step 4: Rodar e ver passar** — `npx ng lint && npx ng test --watch=false && npx ng build` → verdes.
- [ ] **Step 5: Commit** — `feat(frontend): cobranças, inadimplência, matrícula na ficha do aluno e parâmetros de cobrança`

---

### Task 10: Ponta a ponta e documentação

**Files:**
- Modify: `docs/02-requisitos-funcionais.md`, `docs/04-modelo-de-dados.md`, `docs/05-arquitetura.md`, `docs/06-api.md`, `docs/07-eventos.md`, `docs/08-roadmap.md`, `docs/09-decisoes-tecnicas.md`, `README.md` (se citar funcionalidades), `.env.example`/`docker-compose.yml` só se surgir variável nova (`LOCGYM_ROTINA_DIARIA_HABILITADA`, padrão `true`)

- [ ] **Step 1: Verificação ponta a ponta** — `docker compose up -d --build`; no navegador (`http://localhost:4200`), como admin: criar plano mensal, aluno, matrícula → registrar pagamento → matrícula ATIVA; `curl -X POST localhost:8080/api/v1/dev/rotina-diaria` (com token) → `ResultadoRotina` sem falhas; `docker compose --profile tools up -d kafka-ui` → tópicos `matricula.criada`, `pagamento.gerado`, `pagamento.registrado` com mensagens. Registrar o resultado no ledger.
- [ ] **Step 2: Documentação** — 02 (regras da Fase 3 com exemplos de datas), 04 (ER Mermaid com `matricula`, `matricula_periodo`, `pagamento`, `shedlock`), 05 (módulos `matricula → pagamento`, quitação síncrona, job por academia), 06 (todos os endpoints novos e códigos de erro), 07 (produtores reais e payloads), 08 (Fase 3 concluída; ideias candidatas marcadas como **não adotadas**), 09 (ADRs: rotina diária multi-tenant sem contornar RLS; quitação síncrona na mesma transação; calendário com âncora; abono estende vigência; recuperação de períodos parados; inadimplência como consulta de leitura; `Calendario` em America/Sao_Paulo).
- [ ] **Step 3: Verificar** — `./mvnw -B verify` e `npx ng lint && npx ng test --watch=false && npx ng build` verdes; links relativos de `docs/` válidos.
- [ ] **Step 4: Commit** — `docs: documentação da Fase 3`

## Critério de pronto da Fase 3

- Backend e frontend verdes localmente e no CI.
- Fluxo da Task 10 funcionando via `docker compose up`.
- Nenhum código de check-in, relatórios ou portal do aluno.
