# LocGym — Fase 1 (Fundação) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o monorepo do LocGym rodando com `docker compose up` — backend Spring Boot com health checks, migrations Flyway e publicação de eventos no Kafka via outbox; frontend Angular com o esqueleto das áreas admin/aluno; CI no GitHub Actions; documentação técnica inicial — sem nenhum módulo de negócio.

**Architecture:** Monolito modular Spring Boot (Spring Modulith verifica fronteiras; ArchUnit garante camadas). Nesta fase existe só o módulo técnico `compartilhado`, com a infraestrutura de eventos (transactional outbox → Kafka). Frontend é uma SPA Angular servida por nginx, que faz proxy de `/api` e `/actuator/health` para o backend.

**Tech Stack:** Java 21, Spring Boot (última 4.x estável), Maven Wrapper, Flyway, PostgreSQL 17, Spring Data JPA, Spring Kafka, Spring Modulith, ArchUnit, Testcontainers, springdoc-openapi; Angular (última estável, standalone + signals), Angular Material, angular-eslint; Docker Compose; Apache Kafka 3.9+/4.x em modo KRaft (imagem `apache/kafka`); GitHub Actions.

**Spec:** `docs/planejamento/design.md` (requisitos originais em `docs/planejamento/especificacao.md`).

## Global Constraints

- Java **21**; Spring Boot 4.x; build com `./mvnw` (o wrapper é versionado — a máquina não precisa de Maven instalado).
- Pacote base `com.locgym`; groupId `com.locgym`, artifactId `locgym-backend`.
- Domínio, tabelas, colunas, endpoints e eventos em **português**, sem acentos em identificadores (`outbox_evento`, `ocorrido_em`); termos técnicos em inglês quando forem nomes de framework.
- API REST sob `/api/v1`; erros no formato RFC 7807 (Problem Details).
- Fuso de negócio `America/Sao_Paulo`; o banco grava `timestamptz`; a JVM roda em UTC.
- Envelope de evento: `evento_id`, `tipo`, `academia_id`, `ocorrido_em`, `versao`, `dados`. Nome do tópico = `tipo` (ex.: `checkin.registrado`). Chave da mensagem = `academia_id` (ou `evento_id` se nulo). Entrega at-least-once.
- Nenhum segredo commitado: credenciais vêm de variáveis de ambiente; `.env.example` documenta os valores de desenvolvimento.
- **Fora desta fase:** autenticação/JWT, multi-tenancy/RLS, auditoria, ShedLock e todos os módulos de negócio (academia, aluno, professor, plano, matrícula, pagamento, agenda, frequência, relatório, notificação). A Fase 2 começa por identidade + tenancy.
- Commits no estilo Conventional Commits, terminando com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Critérios aprovados pelo usuário (obrigatórios)

- **Liveness** (`/actuator/health/liveness`) verifica apenas se o processo Spring Boot está respondendo — nenhum indicador de dependência externa no grupo.
- **Readiness** (`/actuator/health/readiness`) verifica o banco de dados (`db`) e dependências críticas locais, **mas não o Kafka**.
- **Outbox com retry e ordem preservada:** evento que falha permanece pendente e é retentado nos ciclos seguintes do relay (com `tentativas`/`ultimo_erro` registrados); a publicação segue a ordem de gravação (`sequencia`) e é interrompida na primeira falha, de modo que nenhum evento posterior é publicado antes de um anterior. Apenas um relay publica por vez (advisory lock no Postgres), e a chave Kafka = `academia_id` mantém os eventos de uma academia — e portanto de cada agregado dela — na mesma partição. Isso garante a ordem por agregado (de forma mais forte: ordem global).
- **Frontend não depende do Kafka** para iniciar nem para exibir a página inicial: o container do frontend depende só do backend, e o backend fica pronto sem Kafka.

## Review Focus

1. **Kafka fora do ar ao publicar** → a transação de negócio não pode falhar; o evento fica pendente no outbox e é publicado quando o Kafka voltar. *(Teste em Task 3: `naoPerdeEventoQuandoKafkaFalha`.)*
2. **Transação revertida** → nenhum evento pode chegar ao Kafka. *(Task 3: `rollbackNaoPublica`.)*
3. **Ordem de subida do compose** → backend não pode iniciar antes do Postgres estar saudável nem ficar em crash-loop; o Kafka precisa apenas ter sido iniciado (revisado na revisão final: exigir Kafka saudável fazia o frontend depender do Kafka, contrariando o critério aprovado). *(Task 6: `depends_on` com `service_healthy` para o Postgres e `service_started` para o Kafka + verificação manual com Kafka unhealthy.)*
4. **Backend fora do ar visto pelo frontend** → a tela mostra "Backend indisponível", não uma página quebrada. *(Task 5: teste `exibeIndisponivelQuandoHealthFalha`.)*
5. **Dois relays concorrentes (duas instâncias)** → nenhum evento publicado duas vezes pelo mesmo lote. *(Task 3: advisory lock do PostgreSQL — `pg_try_advisory_xact_lock` — garante um único relay ativo por vez; o código não usa `FOR UPDATE SKIP LOCKED`. Teste `relaysConcorrentesNaoDuplicam`.)*

---

## Estrutura de arquivos

```
locgym/ (raiz do repositório)
├── .editorconfig  .gitignore  .env.example  README.md  docker-compose.yml
├── .github/workflows/ci.yml
├── backend/
│   ├── pom.xml  mvnw  .mvn/  Dockerfile  .dockerignore
│   └── src/
│       ├── main/java/com/locgym/
│       │   ├── LocGymApplication.java
│       │   └── compartilhado/
│       │       ├── package-info.java                 (@ApplicationModule, tipo OPEN)
│       │       ├── eventos/                          (API pública: EventoDominio, PublicadorEventos)
│       │       ├── eventos/infraestrutura/           (OutboxEvento, OutboxRepositorio, PublicadorOutbox, OutboxRelay)
│       │       └── web/                              (ProblemDetail handler global)
│       ├── main/resources/
│       │   ├── application.yml  application-dev.yml
│       │   └── db/migration/V1__outbox_evento.sql
│       └── test/java/com/locgym/
│           ├── TestcontainersConfig.java
│           ├── LocGymApplicationIT.java
│           ├── arquitetura/ArquiteturaTest.java
│           └── compartilhado/eventos/OutboxIT.java
├── frontend/
│   ├── package.json  angular.json  eslint.config.js  Dockerfile  nginx.conf  .dockerignore
│   └── src/app/
│       ├── app.config.ts  app.routes.ts  app.ts
│       ├── core/saude/saude.service.ts (+ .spec.ts)
│       ├── layout/shell-admin/  layout/shell-aluno/
│       └── paginas/inicio/inicio.ts (+ .spec.ts)
└── docs/01-visao-geral.md … 09-decisoes-tecnicas.md
```

---

### Task 1: Esqueleto do backend com health check e Flyway

**Files:**
- Create: `.gitignore`, `.editorconfig`, `backend/pom.xml`, `backend/mvnw`, `backend/.mvn/`, `backend/src/main/java/com/locgym/LocGymApplication.java`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/application-dev.yml`, `backend/src/main/resources/db/migration/V1__outbox_evento.sql`
- Test: `backend/src/test/java/com/locgym/TestcontainersConfig.java`, `backend/src/test/java/com/locgym/LocGymApplicationIT.java`

**Interfaces:**
- Produces: `TestcontainersConfig` (`@TestConfiguration(proxyBeanMethods=false)`) com beans `@ServiceConnection` `PostgreSQLContainer` (`postgres:17-alpine`) e `KafkaContainer` (`apache/kafka:<mesma versão do compose>`). Todos os ITs usam `@SpringBootTest @Import(TestcontainersConfig.class)`.
- Produces: tabela `outbox_evento` (usada pela Task 3).

- [ ] **Step 1: Gerar o projeto** via start.spring.io (`curl https://start.spring.io/starter.tgz -d type=maven-project -d javaVersion=21 -d groupId=com.locgym -d artifactId=locgym-backend -d packageName=com.locgym -d dependencies=web,actuator,validation,data-jpa,postgresql,flyway,kafka,testcontainers,modulith | tar -xzf - -C backend`). Adicionar ao pom: `springdoc-openapi-starter-webmvc-ui`, `archunit-junit5` (test), módulos Testcontainers de PostgreSQL e Kafka, e `maven-failsafe-plugin` (ITs com sufixo `IT` rodam em `verify`). Renomear a classe principal para `LocGymApplication`. Raiz: `.gitignore` (Java, Node, IDE, `.env`) e `.editorconfig` (UTF-8, LF, 4 espaços Java / 2 espaços TS/YAML).

- [ ] **Step 2: Escrever o teste que falha** — `LocGymApplicationIT`:

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfig.class)
class LocGymApplicationIT {
    @Autowired TestRestTemplate http;   // ou RestTestClient no Boot 4
    @Autowired JdbcTemplate jdbc;

    @Test void healthEstaUp() {
        var r = http.getForEntity("/actuator/health", String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).contains("\"status\":\"UP\"");
    }
    @Test void livenessEReadinessExpostos() {
        assertThat(http.getForEntity("/actuator/health/liveness", String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(http.getForEntity("/actuator/health/readiness", String.class).getStatusCode().value()).isEqualTo(200);
    }
    @Test void readinessIncluiDbMasNaoKafka() {
        // expor detalhes só para este teste: @SpringBootTest(properties = "management.endpoint.health.group.readiness.show-components=always")
        var corpo = http.getForEntity("/actuator/health/readiness", String.class).getBody();
        assertThat(corpo).contains("\"db\"").doesNotContain("kafka");
    }
    @Test void livenessNaoIncluiDependencias() {
        // idem, show-components=always para o grupo liveness
        var corpo = http.getForEntity("/actuator/health/liveness", String.class).getBody();
        assertThat(corpo).doesNotContain("\"db\"").doesNotContain("kafka");
    }
    @Test void migrationsAplicadas() {
        assertThat(jdbc.queryForObject(
            "select count(*) from flyway_schema_history where success", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select to_regclass('public.outbox_evento') is not null", Boolean.class)).isTrue();
    }
    @Test void openApiDisponivel() {
        assertThat(http.getForEntity("/v3/api-docs", String.class).getStatusCode().value()).isEqualTo(200);
    }
}
```

- [ ] **Step 3: Rodar e ver falhar** — `cd backend && ./mvnw -B verify -Dit.test=LocGymApplicationIT` → FAIL (`outbox_evento` não existe / probes não expostos).

- [ ] **Step 4: Configurar e criar a migration.**
  - `application.yml`: `spring.application.name=locgym`; datasource via `${DB_URL:jdbc:postgresql://localhost:5432/locgym}`, `${DB_USER:locgym}`, `${DB_PASSWORD:locgym}`; `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`; `spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`, producer `acks=all`, `enable.idempotence=true`; `management.endpoints.web.exposure.include=health,info,prometheus`; `management.endpoint.health.probes.enabled=true`; `management.endpoint.health.group.liveness.include=livenessState`; `management.endpoint.health.group.readiness.include=readinessState,db`; `management.endpoint.health.show-details=never`; `logging.structured.format.console=ecs` (desligado em `application-dev.yml` para logs legíveis); `server.shutdown=graceful`; `spring.mvc.problemdetails.enabled=true`; `spring.jackson.time-zone=UTC`.
  - Kafka **não** entra no health agregado nesta fase (indisponibilidade do Kafka não deve derrubar o readiness — o outbox absorve). Registrar isso em `09-decisoes-tecnicas.md` (Task 7).
  - `V1__outbox_evento.sql`:

```sql
create table outbox_evento (
    id           uuid primary key,
    sequencia    bigint generated always as identity unique,
    tipo         varchar(100) not null,
    academia_id  uuid,
    versao       int          not null,
    payload      jsonb        not null,   -- envelope completo
    ocorrido_em  timestamptz  not null,
    publicado_em timestamptz,
    tentativas   int          not null default 0,
    ultimo_erro  text
);
create index ix_outbox_evento_pendente on outbox_evento (sequencia) where publicado_em is null;
```

- [ ] **Step 5: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS, 6 testes do IT passando.

- [ ] **Step 6: Commit** — `git add .gitignore .editorconfig backend && git commit -m "feat(backend): esqueleto Spring Boot com health checks, Flyway e Testcontainers"`

---

### Task 2: Fronteiras de módulo, regras de arquitetura e Problem Details

**Files:**
- Create: `backend/src/main/java/com/locgym/compartilhado/package-info.java`, `backend/src/main/java/com/locgym/compartilhado/web/TratadorGlobalDeErros.java`
- Test: `backend/src/test/java/com/locgym/arquitetura/ArquiteturaTest.java`, `backend/src/test/java/com/locgym/compartilhado/web/TratadorGlobalDeErrosTest.java`

**Interfaces:**
- Produces: convenção de camadas que todo módulo futuro segue — subpacotes `dominio`, `aplicacao`, `infraestrutura`, `web` dentro de `com.locgym.<modulo>`.
- Produces: `TratadorGlobalDeErros` (`@RestControllerAdvice extends ResponseEntityExceptionHandler`) que transforma `MethodArgumentNotValidException` em ProblemDetail 400 com propriedade `erros: [{campo, mensagem}]`, e exceções não tratadas em 500 sem stack trace no corpo.

- [ ] **Step 1: Escrever os testes que falham** — `ArquiteturaTest`:

```java
class ArquiteturaTest {
    static final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.locgym");

    @Test void modulosRespeitamFronteiras() {
        ApplicationModules.of(LocGymApplication.class).verify();
    }
    @Test void dominioNaoDependeDeFramework() {
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.fasterxml..")
            .allowEmptyShould(true).check(classes);
    }
    @Test void dominioNaoDependeDeOutrasCamadas() {
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat().resideInAnyPackage("..aplicacao..", "..infraestrutura..", "..web..")
            .allowEmptyShould(true).check(classes);
    }
    @Test void aplicacaoNaoDependeDeInfraOuWeb() {
        noClasses().that().resideInAPackage("..aplicacao..")
            .should().dependOnClassesThat().resideInAnyPackage("..infraestrutura..", "..web..")
            .allowEmptyShould(true).check(classes);
    }
}
```

`TratadorGlobalDeErrosTest` (`@WebMvcTest` com um controller de teste interno que tem um `@PostMapping` com `@Valid` num record com `@NotBlank String nome`, e um `@GetMapping` que lança `RuntimeException("segredo")`):
  - `validacaoRetornaProblemDetail400`: POST `{}` → status 400, `content-type: application/problem+json`, `$.erros[0].campo == "nome"`.
  - `erroInesperadoNaoVazaDetalhe`: GET → 500, corpo **não** contém `"segredo"` nem `"stackTrace"`.

- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest='ArquiteturaTest,TratadorGlobalDeErrosTest'` → `TratadorGlobalDeErrosTest` FAIL (`erros` ausente / detalhe vazando).

- [ ] **Step 3: Implementar** `package-info.java` (`@org.springframework.modulith.ApplicationModule(type = Type.OPEN)` — `compartilhado` é usável por todos os módulos) e `TratadorGlobalDeErros` conforme Interfaces; erro 500 loga a exceção com stack trace e devolve `detail = "Erro interno inesperado"`.

- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS.

- [ ] **Step 5: Commit** — `git commit -am "feat(backend): regras de arquitetura (Modulith/ArchUnit) e Problem Details"` (adicionando os arquivos novos).

---

### Task 3: Publicação de eventos via transactional outbox → Kafka

**Files:**
- Create em `backend/src/main/java/com/locgym/compartilhado/eventos/`: `EventoDominio.java`, `PublicadorEventos.java`, `infraestrutura/OutboxEvento.java`, `infraestrutura/OutboxRepositorio.java`, `infraestrutura/PublicadorOutbox.java`, `infraestrutura/OutboxRelay.java`, `infraestrutura/OutboxConfig.java`
- Modify: `backend/src/main/resources/application.yml` (propriedades `locgym.outbox.*`)
- Test: `backend/src/test/java/com/locgym/compartilhado/eventos/OutboxIT.java`, `backend/src/test/java/com/locgym/compartilhado/eventos/EventoTeste.java`

**Interfaces:**
- Produces (API pública usada por todos os módulos das fases seguintes):

```java
public interface EventoDominio {
    String tipo();          // ex.: "checkin.registrado" — também é o nome do tópico
    int versao();           // começa em 1
    UUID academiaId();      // pode ser null para eventos fora de tenant
}

public interface PublicadorEventos {
    /** Grava o evento no outbox. Exige transação ativa (Propagation.MANDATORY). */
    void publicar(EventoDominio evento);
}
```

- Produces: envelope JSON gravado em `outbox_evento.payload` e enviado como valor da mensagem:
  `{"evento_id": "<uuid>", "tipo": "...", "academia_id": "<uuid|null>", "ocorrido_em": "<ISO-8601 UTC>", "versao": 1, "dados": { ...campos do record do evento... }}`. Header Kafka `evento_id` também presente (para idempotência de consumidores).
- Produces: `OutboxRelay.publicarPendentes(): int` (retorna quantos publicou) — agendado com `@Scheduled(fixedDelayString = "${locgym.outbox.intervalo-ms:1000}")`; `locgym.outbox.lote=100`; `locgym.outbox.timeout-envio-ms=5000`. `@EnableScheduling` em `OutboxConfig`, desligável com `locgym.outbox.relay-habilitado=false` (os testes chamam `publicarPendentes()` diretamente com o agendamento desligado).
- Consumes: `outbox_evento` (Task 1), `TestcontainersConfig` (Task 1).

- [ ] **Step 1: Escrever os testes que falham** — `EventoTeste` é `record EventoTeste(UUID academiaId, String mensagem) implements EventoDominio` com `tipo() = "teste.ocorrido"`, `versao() = 1`. `OutboxIT` (`@SpringBootTest`, `locgym.outbox.relay-habilitado=false`, consumidor Kafka de teste inscrito em `teste.ocorrido`, `TransactionTemplate` para abrir transações):

  - `publicaEnvelopeCompleto`: em transação, `publicador.publicar(new EventoTeste(acad, "oi"))`; `relay.publicarPendentes() == 1`; mensagem consumida tem chave `acad.toString()`, JSON com `tipo == "teste.ocorrido"`, `versao == 1`, `academia_id == acad`, `dados.mensagem == "oi"`, `evento_id` UUID válido, `ocorrido_em` parseável como `Instant`; linha no outbox com `publicado_em` não nulo.
  - `rollbackNaoPublica`: publica dentro de transação marcada `setRollbackOnly()`; `select count(*) from outbox_evento == 0`; `relay.publicarPendentes() == 0`; nenhuma mensagem consumida em 2s.
  - `exigeTransacao`: chamar `publicar` fora de transação lança `IllegalTransactionStateException`.
  - `naoPerdeEventoQuandoKafkaFalha`: com `KafkaTemplate` substituído (`@MockitoSpyBean`) para lançar `KafkaException` no envio, `publicarPendentes() == 0`, linha continua com `publicado_em` nulo, `tentativas == 1`, `ultimo_erro` não vazio; removido o stub, nova chamada publica (`== 1`) e a mensagem chega.
  - `relaysConcorrentesNaoDuplicam`: grava 50 eventos; chama `publicarPendentes()` em 2 threads simultâneas (e de novo até retornar 0); soma dos retornos `== 50`; consumidor recebe exatamente 50 `evento_id` distintos.
  - `preservaOrdemAposFalha`: grava eventos E1, E2, E3 da mesma academia; stub do `KafkaTemplate` falha só no envio de E2 → `publicarPendentes() == 1` (só E1), E3 continua pendente; removido o stub → `publicarPendentes() == 2`; mensagens consumidas na partição, em ordem: E1, E2, E3.
  - `chaveUsaEventoIdQuandoSemAcademia`: evento com `academiaId == null` → chave da mensagem == `evento_id` do envelope.

- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B verify -Dit.test=OutboxIT` → FAIL de compilação (tipos inexistentes).

- [ ] **Step 3: Implementar.**
  - `PublicadorOutbox` (`@Transactional(propagation = MANDATORY)`): gera `evento_id` (UUID aleatório), `ocorrido_em = Instant.now(clock)` (injetar `Clock`, bean em `OutboxConfig` com `Clock.systemUTC()`), serializa o envelope com o `ObjectMapper`/`JsonMapper` do Spring (o record do evento vira `dados`, sem duplicar `tipo/versao/academiaId` se o Jackson os incluir — usar `@JsonIgnore` não é possível na interface de domínio; montar `dados` via `valueToTree` e remover essas três chaves).
  - `OutboxRelay` abre a transação com `select pg_try_advisory_xact_lock(<constante LOCK_OUTBOX>)`; se `false`, outro relay está ativo → retorna 0 sem publicar. `OutboxRepositorio.buscarPendentesParaPublicar(int limite)`: query nativa `select * from outbox_evento where publicado_em is null order by sequencia limit :limite`.
  - `OutboxRelay.publicarPendentes()` numa transação: busca o lote; para cada evento, `kafkaTemplate.send(tipo, chave, payload)` com header `evento_id`, aguarda `get(timeout)`; sucesso → `publicado_em = now`; falha → `tentativas++`, `ultimo_erro = mensagem`, **interrompe o lote** (preserva ordem) e retorna o total já publicado. Log `warn` na falha.
  - `OutboxEvento` é entidade JPA mapeando a tabela (`payload` como `String` com `@JdbcTypeCode(SqlTypes.JSON)`).

- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS, 7 testes do `OutboxIT` passando, `ArquiteturaTest` ainda verde.

- [ ] **Step 5: Commit** — `git add backend && git commit -m "feat(backend): publicação de eventos via transactional outbox para Kafka"`

---

### Task 4: Imagem Docker do backend

**Files:**
- Create: `backend/Dockerfile`, `backend/.dockerignore`

**Interfaces:**
- Produces: imagem que escuta na porta `8080`, configurada só por env (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `SPRING_PROFILES_ACTIVE`), roda como usuário não-root.

- [ ] **Step 1: Escrever o Dockerfile multi-stage** — estágio `build` com `eclipse-temurin:21-jdk` (cache de `~/.m2` via `--mount=type=cache`, `./mvnw -B -DskipTests package`), extração de layers (`java -Djarmode=tools -jar app.jar extract --layers --launcher`); estágio final `eclipse-temurin:21-jre-alpine`, usuário `locgym` (uid 10001), `ENV TZ=UTC`, `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"`, `HEALTHCHECK` via `wget -qO- http://localhost:8080/actuator/health/liveness`. `.dockerignore` exclui `target/`, `.idea/`, `*.iml`.

- [ ] **Step 2: Verificar** — `docker build -t locgym-backend:dev backend` → sucesso; `docker run --rm locgym-backend:dev id -u` → `10001`.

- [ ] **Step 3: Commit** — `git add backend/Dockerfile backend/.dockerignore && git commit -m "build(backend): Dockerfile multi-stage não-root"`

---

### Task 5: Esqueleto do frontend Angular

**Files:**
- Create: `frontend/` via Angular CLI; `frontend/src/app/core/saude/saude.service.ts` (+ `.spec.ts`), `frontend/src/app/paginas/inicio/inicio.ts` (+ `.spec.ts`), `frontend/src/app/layout/shell-admin/shell-admin.ts`, `frontend/src/app/layout/shell-aluno/shell-aluno.ts`, `frontend/proxy.conf.json`, `frontend/Dockerfile`, `frontend/nginx.conf`, `frontend/.dockerignore`
- Modify: `frontend/src/app/app.routes.ts`, `frontend/src/app/app.config.ts`

**Interfaces:**
- Produces: `SaudeService.verificar(): Observable<'online' | 'indisponivel'>` — GET `/actuator/health`; `status === 'UP'` → `'online'`; qualquer outro status ou erro HTTP → `'indisponivel'` (nunca propaga erro).
- Produces: rotas `''` → `Inicio`; `'admin'` → `ShellAdmin` (lazy, toolbar "LocGym — Painel" + sidenav vazio com comentário de onde os módulos entram); `'aluno'` → `ShellAluno` (lazy, toolbar "LocGym — Aluno", layout mobile-first); `'**'` → redirect `''`. Fase 2 adiciona guards e login nessas rotas.
- Produces: em dev, `proxy.conf.json` encaminha `/api` e `/actuator` para `http://localhost:8080`; em produção o nginx faz o mesmo para `http://backend:8080`.

- [ ] **Step 1: Gerar o projeto** — `npx @angular/cli@latest new frontend --style=scss --routing --ssr=false --skip-git --package-manager=npm`; `ng add @angular/material` (tema próprio, tipografia ativada); `ng add @angular-eslint/schematics`. `provideHttpClient(withFetch())` em `app.config.ts`. Idioma `pt-BR` (`LOCALE_ID` + `registerLocaleData`).

- [ ] **Step 2: Escrever os testes que falham** (runner padrão do CLI, com `provideHttpClientTesting`):
  - `saude.service.spec.ts`:
    - `retornaOnlineQuandoUp`: responde `{status:'UP'}` → emite `'online'`.
    - `retornaIndisponivelQuandoDown`: responde 503 `{status:'DOWN'}` → emite `'indisponivel'`.
    - `retornaIndisponivelQuandoRedeFalha`: `req.error(new ProgressEvent('error'))` → emite `'indisponivel'`.
  - `inicio.spec.ts` (com `SaudeService` falso):
    - `exibeOnline`: serviço emite `'online'` → texto "Backend online" visível; links "Painel administrativo" (`/admin`) e "Portal do aluno" (`/aluno`) presentes.
    - `exibeIndisponivelQuandoHealthFalha`: emite `'indisponivel'` → texto "Backend indisponível" visível, sem erro no console.

- [ ] **Step 3: Rodar e ver falhar** — `cd frontend && npx ng test --watch=false` → FAIL (serviço/componente inexistentes).

- [ ] **Step 4: Implementar** `SaudeService`, `Inicio` (signal com o estado; `mat-card` com nome do produto, status do backend e os dois links), os shells e as rotas conforme Interfaces.

- [ ] **Step 5: Rodar e ver passar** — `npx ng test --watch=false && npx ng lint && npx ng build` → todos verdes.

- [ ] **Step 6: Dockerfile e nginx** — multi-stage: `node:22-alpine` (`npm ci`, `npx ng build`) → `nginx:1.27-alpine` servindo `dist/frontend/browser`; `nginx.conf` com `try_files $uri $uri/ /index.html`, `location /api/` e `location = /actuator/health` com `proxy_pass http://backend:8080`, gzip, cache longo para assets com hash e `no-cache` para `index.html`. Verificar: `docker build -t locgym-frontend:dev frontend` → sucesso.

- [ ] **Step 7: Commit** — `git add frontend && git commit -m "feat(frontend): esqueleto Angular com áreas admin/aluno e status do backend"`

---

### Task 6: Docker Compose

**Files:**
- Create: `docker-compose.yml`, `.env.example`

**Interfaces:**
- Consumes: imagens das Tasks 4 e 5; variáveis de ambiente da Task 4.
- Produces: `docker compose up -d --build` sobe `postgres`, `kafka`, `backend`, `frontend`; `docker compose --profile tools up -d` adiciona `kafka-ui`. Portas no host: frontend `4200→80`, backend `8080`, postgres `5432`, kafka `9094` (listener EXTERNAL), kafka-ui `8081`.

- [ ] **Step 1: Escrever o compose.**
  - `postgres:17-alpine` — `POSTGRES_DB/USER/PASSWORD` de `${...}` com defaults de `.env.example` (`locgym/locgym/locgym`), volume nomeado `pgdata`, healthcheck `pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB`.
  - `apache/kafka:<versão>` em KRaft single-node (`KAFKA_NODE_ID=1`, `KAFKA_PROCESS_ROLES=broker,controller`, listeners `PLAINTEXT://kafka:9092`, `CONTROLLER://kafka:9093`, `EXTERNAL://localhost:9094`, `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1`, `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`), volume `kafkadata`, healthcheck com `/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:9092`.
  - `backend` — `build: ./backend`, env `DB_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}`, `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`, `SPRING_PROFILES_ACTIVE=dev`, `depends_on` postgres e kafka com `condition: service_healthy`, healthcheck em `/actuator/health/readiness`, `restart: unless-stopped`.
  - `frontend` — `build: ./frontend`, `depends_on: backend (service_healthy)`.
  - `kafka-ui` (`provectuslabs/kafka-ui` ou sucessor mantido `kafbat/kafka-ui`) com `profiles: [tools]`.

- [ ] **Step 2: Verificar de ponta a ponta** — `cp .env.example .env && docker compose up -d --build`; aguardar `docker compose ps` mostrar os 4 serviços `healthy`; o serviço `frontend` declara `depends_on` apenas `backend` (nunca `kafka`); `curl -s localhost:8080/actuator/health` → `{"status":"UP"}`; `curl -s localhost:4200/actuator/health` → `{"status":"UP"}` (via nginx); abrir `http://localhost:4200` → "Backend online". Depois `docker compose stop kafka` → `curl localhost:8080/actuator/health/readiness` continua `UP` e `http://localhost:4200` continua mostrando "Backend online"; `docker compose restart frontend` com Kafka parado → frontend sobe normalmente; `docker compose start kafka`. `docker compose down` (sem `-v`).

- [ ] **Step 3: Commit** — `git add docker-compose.yml .env.example && git commit -m "build: docker compose com postgres, kafka, backend e frontend"`

---

### Task 7: CI no GitHub Actions e documentação técnica

**Files:**
- Create: `.github/workflows/ci.yml`, `README.md`, `docs/01-visao-geral.md`, `docs/02-requisitos-funcionais.md`, `docs/03-requisitos-nao-funcionais.md`, `docs/04-modelo-de-dados.md`, `docs/05-arquitetura.md`, `docs/06-api.md`, `docs/07-eventos.md`, `docs/08-roadmap.md`, `docs/09-decisoes-tecnicas.md`

**Interfaces:**
- Consumes: comandos de build/teste das Tasks 1–6.

- [ ] **Step 1: Workflow** — `ci.yml` em `push` para `main` e em `pull_request`; `concurrency` cancelando execuções antigas do mesmo ref. Jobs paralelos:
  - `backend`: `actions/setup-java@v4` (temurin 21, cache maven) → `./mvnw -B verify` em `backend/` (Testcontainers funciona nativamente no runner `ubuntu-latest`); publicar relatórios surefire/failsafe como artifact em caso de falha.
  - `frontend`: `actions/setup-node@v4` (Node 22, cache npm, `cache-dependency-path: frontend/package-lock.json`) → `npm ci`, `npx ng lint`, `npx ng test --watch=false`, `npx ng build`.
  - `docker`: `needs: [backend, frontend]` → `docker compose build` (valida os Dockerfiles; sem push).
  - Verificar sintaxe localmente com `npx --yes @action-validator/cli .github/workflows/ci.yml` (ou `actionlint` se disponível) → sem erros.

- [ ] **Step 2: Documentação** — em português, curta e factual, refletindo o **design aprovado** e marcando claramente o que já existe (Fase 1) e o que é planejado:
  - `01` visão, público, escopo/não-escopo do MVP; `02` requisitos funcionais por módulo (do spec, com as regras aprovadas: cobrança recorrente, carência de 10 dias, suspensão, cancelamento); `03` requisitos não funcionais e como cada um será atendido; `04` modelo de dados — tabela `outbox_evento` atual + diagrama ER planejado (Mermaid) das entidades do design; `05` arquitetura — diagrama de containers (Mermaid), módulos, camadas, regras de dependência, multi-tenancy planejada (`academia_id` + RLS); `06` API — convenções (`/api/v1`, Problem Details, `X-Academia-Id`, JWT planejado), endpoints atuais (`/actuator/health*`, `/v3/api-docs`, `/swagger-ui.html`); `07` eventos — envelope, outbox, garantias at-least-once, tabela de todos os tópicos do design com produtor/consumidor planejados; `08` roadmap — fases 1–6 com status; `09` ADRs: 001 monolito modular, 002 schema compartilhado + RLS, 003 transactional outbox, 004 Kafka fora do readiness, 005 aluno por academia/login global, 006 cobrança recorrente, 007 QR rotativo assinado (30s), 008 inadimplência derivada, 009 domínio em português.
  - `README.md`: pitch de 2 linhas, stack, diagrama Mermaid, pré-requisitos (Docker; JDK 21 e Node 22 só para desenvolvimento), "Como rodar" (`cp .env.example .env && docker compose up -d --build`, URLs: app `:4200`, Swagger `:8080/swagger-ui.html`, Kafka UI com `--profile tools` em `:8081`), como rodar testes, estrutura do repo, link para `docs/`, badge do CI (com placeholder do repositório GitHub — o remoto ainda não existe).

- [ ] **Step 3: Verificar** — todos os links relativos do README e de `docs/` apontam para arquivos existentes (`grep -o '](\./[^)]*)' -r README.md docs | …` ou checagem manual); `./mvnw -B verify` e `npx ng test --watch=false` continuam verdes.

- [ ] **Step 4: Commit** — `git add .github README.md docs && git commit -m "docs: documentação técnica inicial e pipeline de CI"`

---

## Critério de pronto da Fase 1

- `cd backend && ./mvnw -B verify` verde; `cd frontend && npx ng lint && npx ng test --watch=false && npx ng build` verdes.
- `docker compose up -d --build` → 4 serviços `healthy`, `http://localhost:4200` mostra "Backend online".
- Nenhum código de módulo de negócio no repositório.
