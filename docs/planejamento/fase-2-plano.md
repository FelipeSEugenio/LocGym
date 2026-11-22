# LocGym — Fase 2 (Identidade e cadastros) — Plano de implementação

**Goal:** Login seguro com perfis por academia, isolamento entre academias garantido pela aplicação (Hibernate `@TenantId`) e pelo banco (RLS), auditoria das operações críticas e os cadastros base (academias, administradores, alunos, professores, planos), com as telas correspondentes no Angular.

**Architecture:** Novos módulos Spring Modulith `identidade`, `academia`, `aluno`, `professor`, `plano`; infraestrutura transversal em `compartilhado` (`tenancy`, `auditoria`, `erros`). A aplicação conecta ao Postgres como `locgym_app` (sem `BYPASSRLS`, não dono); migrations rodam como dono. O contexto (academia + perfil) vem dos headers `X-Academia-Id`/`X-Perfil`, validado no banco a cada requisição.

**Tech Stack:** o da Fase 1 + Spring Security (OAuth2 Resource Server, Nimbus `JwtEncoder`/`JwtDecoder`, RSA), Hibernate `@TenantId`, PostgreSQL RLS, Web Locks API no frontend.

**Spec:** `docs/planejamento/fase-2-design.md` (base: `docs/planejamento/design.md`).

## Global Constraints

- Reaproveitar a Fase 1: não recriar outbox, compose, `TestcontainersConfig` (só estender), `TratadorGlobalDeErros` (só estender).
- Camadas por módulo: `dominio` (sem Spring/JPA/Jackson), `aplicacao`, `infraestrutura`, `web`; `ArquiteturaTest` deve continuar verde.
- Rotas sob `/api/v1`; erros em Problem Details com propriedade `codigo` nos 403/409 de regra.
- Nomes em português sem acento nos identificadores.
- Senha mínima **8** caracteres, BCrypt. Access token **15 min** (RS256). Refresh **7 dias**, cookie `locgym_refresh`, `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`, guardado como SHA-256, janela de tolerância de reuso **10 s**.
- Headers de contexto: `X-Academia-Id` (uuid) e `X-Perfil` (`ADMIN`|`ALUNO`).
- E-mail sempre normalizado (`trim` + minúsculas).
- Recurso de outra academia → **404**; sem vínculo/perfil → **403**; regra violada → **409**.
- Nenhum evento Kafka nesta fase.
- Commits Conventional Commits terminando com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Access token expira no meio do uso** → o usuário não percebe: o interceptor faz um refresh e repete a requisição. *(Task 8: `renovaUmaVezERepete`.)*
2. **Duas abas renovando ao mesmo tempo** → ninguém é deslogado. *(Task 3: `reusoDentroDaJanelaNaoRevoga`; Task 8: refresh sob Web Lock e `duasRequisicoes401CompartilhamUmRefresh`.)*
3. **Admin removido enquanto está logado** → a próxima requisição dele naquela academia já é 403. *(Task 5: `adminRemovidoPerdeAcessoNaHora`.)*
4. **E-mail com maiúsculas/espaços** → é o mesmo usuário no login e nos convites. *(Task 2: `emailENormalizado`.)*
5. **Backend reiniciado em dev (chave RSA nova)** → o frontend volta para o login sem tela quebrada. *(Task 8: `refreshFalhaVaiParaLogin`.)*

---

## Estrutura de arquivos (backend, novos)

```
com/locgym/
├── compartilhado/
│   ├── erros/        RegraVioladaException, NaoEncontradoException
│   ├── tenancy/      Perfil, ContextoTenant, TenantContext, ResolvedorTenant, GerenciadorTransacaoComTenant, TenancyConfig
│   ├── auditoria/    Auditoria (porta), infraestrutura/{RegistroAuditoria, AuditoriaJpa}
│   └── web/          PaginaResposta (+ TratadorGlobalDeErros estendido)
├── identidade/       dominio/, aplicacao/, infraestrutura/ (JWT, segurança, filtro de contexto, repositórios), web/
├── academia/         academia + administradores
├── plano/  professor/  aluno/
resources/db/migration/
  V2__papel_app_e_identidade.sql   V3__tabelas_tenant_e_rls.sql   V4__vinculos_do_usuario.sql
```

---

### Task 1: Papel `locgym_app`, schema da Fase 2 e RLS

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__papel_app_e_identidade.sql`, `V3__tabelas_tenant_e_rls.sql`, `V4__vinculos_do_usuario.sql`
- Modify: `backend/src/main/resources/application.yml`, `backend/src/test/java/com/locgym/TestcontainersConfig.java`, `docker-compose.yml`, `.env.example`
- Test: `backend/src/test/java/com/locgym/compartilhado/tenancy/RlsIT.java`

**Interfaces:**
- Produces: tabelas do design §4; papel `locgym_app`; função `vinculos_do_usuario(p_usuario uuid) returns table(academia_id uuid, academia_nome text, academia_status text, perfil text)` — `SECURITY DEFINER`, `set search_path = public`, exclui aluno `CANCELADO`, une `administrador_academia` (perfil `ADMIN`) e `aluno` (perfil `ALUNO`); `EXECUTE` só para `locgym_app`.
- Produces (testes): bean `JdbcTemplate` qualificado `@Qualifier("jdbcSuperusuario")` (conexão do dono, ignora RLS) para preparar/verificar dados; o `DataSource` da aplicação usa `locgym_app`.
- Config: datasource `${DB_APP_USER:locgym_app}`/`${DB_APP_PASSWORD:locgym_app}`; `spring.flyway.user=${DB_USER:locgym}`, `spring.flyway.password=${DB_PASSWORD:locgym}`; placeholders `app_usuario`, `app_senha`.

- [ ] **Step 1: Testes que falham** — `RlsIT` (`@SpringBootTest @Import(TestcontainersConfig.class)`), preparando duas academias A e B com um plano cada via `jdbcSuperusuario`:
  - `aplicacaoNaoESuperusuarioNemIgnoraRls`: `select rolsuper, rolbypassrls from pg_roles where rolname = current_user` (via `JdbcTemplate` da aplicação) → ambos `false`; `current_user == "locgym_app"`.
  - `semAcademiaDefinidaNaoVeNada`: em transação, `select count(*) from plano` → `0`.
  - `comAcademiaDefinidaVeSoAsSuas`: em transação, `select set_config('app.academia_id', A, true)`; `select count(*) from plano` → `1` e o `academia_id` é A.
  - `naoInsereEmOutraAcademia`: com A definida, `insert into plano (..., academia_id = B ...)` → lança `DataAccessException` (violação de policy).
  - `vinculosIgnoramAlunoCancelado`: usuário admin de A, aluno ATIVO em B e aluno CANCELADO em C → `select * from vinculos_do_usuario(u)` retorna exatamente `{(A, ADMIN), (B, ALUNO)}` com nome e status da academia.
  - `outboxContinuaSemRls`: `select count(*) from outbox_evento` sem academia definida não falha.
- [ ] **Step 2: Rodar e ver falhar** — `cd backend && ./mvnw -B verify -Dit.test=RlsIT` → FAIL (tabelas/papel inexistentes).
- [ ] **Step 3: Implementar.**
  - V2: cria o papel idempotentemente (`do $$ ... if not exists (select from pg_roles where rolname = '${app_usuario}') then execute format('create role %I login password %L nosuperuser nobypassrls', '${app_usuario}', '${app_senha}') ...`); `grant usage on schema public`; `alter default privileges ... grant select, insert, update, delete on tables to ...` e `grant usage, select on sequences`; grants explícitos em `outbox_evento`; tabelas globais `usuario`, `academia`, `administrador_academia` (com `nome`), `refresh_token` com os `CHECK` do design.
  - V3: `aluno`, `professor`, `plano`, `auditoria`; para cada uma `enable row level security`, `force row level security` e policy `tenant_isolamento` `using/with check (academia_id = nullif(current_setting('app.academia_id', true), '')::uuid)`; índices por `academia_id` e buscas (`aluno(academia_id, cpf)`, `lower(nome)`).
  - V4: função `vinculos_do_usuario` conforme Interfaces.
  - `TestcontainersConfig`: o container Postgres deixa de ser `@ServiceConnection`; um `DynamicPropertyRegistrar` aponta `spring.datasource.*` para `locgym_app` e `spring.flyway.*` para o superusuário do container; bean `jdbcSuperusuario` sobre um `DriverManagerDataSource` do superusuário. Kafka continua `@ServiceConnection`.
  - Compose/`.env.example`: `DB_APP_USER=locgym_app`, `DB_APP_PASSWORD=locgym_app` passados ao backend.
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → BUILD SUCCESS (inclui os testes da Fase 1, agora rodando como `locgym_app`).
- [ ] **Step 5: Commit** — `feat(backend): papel locgym_app, schema da Fase 2 e RLS`.

---

### Task 2: Login, cadastro de administrador e segurança base

**Files:**
- Create: `compartilhado/erros/{RegraVioladaException,NaoEncontradoException}.java`; `identidade/dominio/{Usuario,Email,Vinculo}.java`; `identidade/aplicacao/{ServicoAutenticacao,ContasDeUsuario,ResultadoLogin}.java`; `identidade/infraestrutura/{SegurancaConfig,ChavesJwt,EmissorDeTokens,UsuarioRepositorio,VinculosRepositorio,AcademiaInicialRepositorio}.java`; `identidade/web/{AuthController,EuController}.java` + DTOs
- Modify: `compartilhado/web/TratadorGlobalDeErros.java`, `application.yml`, `pom.xml` (`spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`)
- Test: `identidade/LoginIT.java`, `identidade/dominio/EmailTest.java`, `compartilhado/web/TratadorGlobalDeErrosTest.java` (novos casos)

**Interfaces:**
- Produces: `RegraVioladaException(String codigo, String mensagem)` → 409 com `codigo`; `NaoEncontradoException(String mensagem)` → 404.
- Produces: `Email.de(String bruto): Email` (trim + minúsculas, formato válido) — `EmailTest`.
- Produces (API pública do módulo, usada por `academia` e `aluno`): `ContasDeUsuario.garantirUsuario(Email email, String nome, String senhaInicial): UUID` — cria com `deve_trocar_senha = true` se não existe; se existe, retorna o id e **ignora** a senha.
- Produces: `EmissorDeTokens.emitirAcesso(Usuario u): TokenDeAcesso(String token, Instant expiraEm)`; claims `sub`, `nome`, `deve_trocar_senha`. `ChavesJwt` lê `locgym.jwt.chave-privada` (PEM PKCS#8, env `JWT_CHAVE_PRIVADA`) ou gera RSA 2048 com `WARN`.
- Produces: `VinculosRepositorio.doUsuario(UUID usuarioId): List<Vinculo>` (chama `vinculos_do_usuario`); `Vinculo(UUID academiaId, String academiaNome, String academiaStatus, Perfil perfil)`.
- Endpoints: `POST /api/v1/auth/login`, `POST /api/v1/auth/cadastro-administrador`, `GET /api/v1/eu/vinculos`. Resposta de login: `{accessToken, expiraEm, usuario{id,nome,email,deveTrocarSenha}, vinculos[...]}` + cookie de refresh (o emissor do refresh vem na Task 3; aqui o cookie ainda não é emitido).
- `SegurancaConfig`: stateless, CSRF desligado (API com Bearer; o cookie de refresh é `SameSite=Strict` e só vale em `/api/v1/auth`), públicos: `/api/v1/auth/login|cadastro-administrador|refresh|logout`, `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`; resto autenticado.

- [ ] **Step 1: Testes que falham** — `LoginIT` (MockMvc ou RestClient, `TestcontainersConfig`):
  - `cadastroDeAdministradorCriaAcademiaEVinculo`: POST cadastro → 201; `vinculos == [{academiaNome, perfil: ADMIN, academiaStatus: ATIVA}]`; `accessToken` não vazio.
  - `cadastroComEmailExistente409`: segundo cadastro com o mesmo e-mail → 409, `codigo == "EMAIL_JA_CADASTRADO"`.
  - `loginOk`: → 200 com os mesmos vínculos.
  - `senhaErradaEEmailInexistenteRespondemIgual`: ambos → 401, mesmo corpo (`detail == "E-mail ou senha inválidos"`).
  - `emailENormalizado`: cadastro com `"Ana@Exemplo.com "`, login com `"ana@exemplo.COM"` → 200.
  - `senhaCurtaRecusada`: senha de 7 caracteres → 400 com `erros[0].campo == "senha"`.
  - `rotaProtegidaSemToken401` / `euVinculosComToken200`.
  - `EmailTest`: normalização e rejeição de formato inválido.
- [ ] **Step 2: Ver falhar** — `./mvnw -B verify -Dit.test=LoginIT -Dtest=EmailTest`.
- [ ] **Step 3: Implementar** conforme Interfaces. Login executa `BCrypt.matches` contra um hash fixo quando o e-mail não existe (tempo semelhante). Cadastro de administrador cria usuário + academia + vínculo (com `nome`) numa transação (academia é global, sem tenant).
- [ ] **Step 4: Ver passar** — `./mvnw -B verify`.
- [ ] **Step 5: Commit** — `feat(identidade): login JWT, cadastro de administrador e segurança base`.

---

### Task 3: Refresh token, logout e troca de senha obrigatória

**Files:**
- Create: `identidade/dominio/RefreshToken.java`, `identidade/aplicacao/ServicoSessao.java`, `identidade/infraestrutura/{RefreshTokenRepositorio,FiltroTrocaDeSenha}.java`
- Modify: `identidade/web/AuthController.java`, `SegurancaConfig.java`
- Test: `identidade/SessaoIT.java`, `identidade/dominio/RefreshTokenTest.java`

**Interfaces:**
- Produces: `ServicoSessao.iniciar(UUID usuarioId): String` (valor bruto do novo refresh, nova família); `ServicoSessao.renovar(String bruto): Renovacao(UUID usuarioId, String novoBruto)`; `ServicoSessao.encerrar(String bruto)`.
- Regra de `renovar` (algoritmo):
  - token desconhecido, expirado ou revogado → 401;
  - token não usado → marca `usado_em = agora`, emite novo da mesma família;
  - token já usado e `agora - usado_em <= 10 s` → emite novo da mesma família, **sem revogar**;
  - token já usado fora da janela → revoga **toda a família** e responde 401.
- Endpoints: `POST /api/v1/auth/refresh` (lê cookie `locgym_refresh`, devolve corpo igual ao login + novo cookie); `POST /api/v1/auth/logout` (revoga e apaga o cookie, 204); `POST /api/v1/auth/trocar-senha {senhaAtual, novaSenha}` → novo access token, `deveTrocarSenha: false`. Login e cadastro passam a emitir o cookie.
- `FiltroTrocaDeSenha`: com claim `deve_trocar_senha = true`, qualquer rota fora de `trocar-senha|logout|refresh` → 403, `codigo == "TROCA_DE_SENHA_OBRIGATORIA"`.

- [ ] **Step 1: Testes que falham** — `RefreshTokenTest` (regra de janela com `Clock` fixo) e `SessaoIT`:
  - `loginEmiteCookieSeguro`: `Set-Cookie` contém `locgym_refresh=`, `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`.
  - `refreshRotaciona`: refresh → 200 e cookie diferente; banco guarda só hash (o valor bruto não aparece em `refresh_token`).
  - `reusoDentroDaJanelaNaoRevoga`: refresh com o mesmo cookie duas vezes em sequência → ambos 200.
  - `reusoForaDaJanelaRevogaFamilia`: com `Clock` avançado 11 s, reuso → 401 e o token mais novo da família também → 401.
  - `logoutRevoga`: após logout, refresh → 401; `Set-Cookie` com `Max-Age=0`.
  - `trocaObrigatoriaBloqueia`: usuário com `deve_trocar_senha` → `GET /api/v1/eu/vinculos` 403 `TROCA_DE_SENHA_OBRIGATORIA`; troca de senha → 200 e novo token libera a rota.
  - `trocaComSenhaAtualErrada401`.
- [ ] **Step 2: Ver falhar.** **Step 3: Implementar.** **Step 4: Ver passar** (`./mvnw -B verify`).
- [ ] **Step 5: Commit** — `feat(identidade): refresh token rotativo, logout e troca de senha obrigatória`.

---

### Task 4: Contexto de tenant (headers, vínculo no banco, `@TenantId`, `set_config`) e auditoria

**Files:**
- Create: `compartilhado/tenancy/{Perfil,ContextoTenant,TenantContext,ResolvedorTenant,GerenciadorTransacaoComTenant,TenancyConfig,EntidadeDeTenant}.java`; `compartilhado/auditoria/{Auditoria,AcaoAuditada}.java`, `compartilhado/auditoria/infraestrutura/{RegistroAuditoria,AuditoriaJpa}.java`; `identidade/infraestrutura/FiltroContextoTenant.java`
- Modify: `SegurancaConfig.java`
- Test: `compartilhado/tenancy/ContextoTenantIT.java` (com controller de teste interno), `compartilhado/auditoria/AuditoriaIT.java`

**Interfaces:**
- Produces: `enum Perfil { ADMIN, ALUNO }`; `record ContextoTenant(UUID academiaId, Perfil perfil, boolean academiaAtiva, UUID usuarioId)`; `TenantContext.atual(): Optional<ContextoTenant>`, `definir(...)`, `limpar()` (ThreadLocal).
- Produces: `@MappedSuperclass EntidadeDeTenant` com `@TenantId @Column(name="academia_id") UUID academiaId`, `criadoEm`, `atualizadoEm` (via `@CreationTimestamp/@UpdateTimestamp`). Toda entidade de tenant estende esta classe.
- Produces: `ResolvedorTenant implements CurrentTenantIdentifierResolver<UUID>` → academia do contexto ou `new UUID(0,0)` (sentinela: nada visível, nenhum insert aceito).
- Produces: `GerenciadorTransacaoComTenant extends JpaTransactionManager` — após `doBegin`, se há contexto, executa `select set_config('app.academia_id', :id, true)` na conexão da transação; registrado como o `transactionManager` primário.
- Produces: `FiltroContextoTenant` (após a autenticação Bearer), aplicado a `/api/v1/**` exceto `/api/v1/auth/**`, `GET /api/v1/eu/vinculos`, `POST /api/v1/academias`:
  - sem `X-Academia-Id`/`X-Perfil` válidos → 400 `codigo == "CONTEXTO_AUSENTE"`;
  - par não presente em `VinculosRepositorio.doUsuario` → 403 `codigo == "SEM_VINCULO"`;
  - academia INATIVA e método não `GET/HEAD`, exceto `PATCH /api/v1/academia/status` → 409 `codigo == "ACADEMIA_INATIVA"`;
  - senão define `TenantContext` e a authority `ROLE_<perfil>`; limpa no `finally`.
- Produces: `Auditoria.registrar(AcaoAuditada acao, String entidade, UUID entidadeId, Object antes, Object depois)` — `@Transactional(MANDATORY)`, usuário e academia do contexto; `antes/depois` serializados em JSONB. `AcaoAuditada`: `ACADEMIA_CRIADA, ACADEMIA_STATUS_ALTERADO, ADMINISTRADOR_ADICIONADO, ADMINISTRADOR_REMOVIDO, ALUNO_STATUS_ALTERADO, PROFESSOR_STATUS_ALTERADO, PLANO_STATUS_ALTERADO`.

- [ ] **Step 1: Testes que falham** — `ContextoTenantIT` com um `@RestController` de teste em `/api/v1/teste-contexto` (GET retorna `current_setting('app.academia_id', true)` lido em transação; POST retorna 204):
  - `semHeaders400`, `academiaSemVinculo403`, `perfilErrado403` (admin enviando `X-Perfil: ALUNO`).
  - `contextoDefineSetConfig`: GET com A/ADMIN → corpo == A.
  - `academiaInativaRecusaEscrita`: academia INATIVA → GET 200, POST 409 `ACADEMIA_INATIVA`.
  - `alunoCanceladoSemAcesso`: usuário com aluno CANCELADO em A → 403 `SEM_VINCULO`.
  - `contextoNaoVazaEntreRequisicoes`: após requisição com A, requisição sem contexto numa rota não-tenant não enxerga A (`TenantContext.atual()` vazio).
  - `AuditoriaIT`: `registrar` fora de transação → `IllegalTransactionStateException`; dentro → linha com `academia_id`, `usuario_id`, `acao`, `antes/depois` JSON.
- [ ] **Step 2: Ver falhar.** **Step 3: Implementar** (`hibernate.tenant_identifier_resolver` apontando para o bean). **Step 4: Ver passar** (`./mvnw -B verify`, `ArquiteturaTest` verde).
- [ ] **Step 5: Commit** — `feat(backend): contexto de tenant com @TenantId, set_config por transação e auditoria`.

---

### Task 5: Academias e administradores

**Files:**
- Create: `academia/{dominio,aplicacao,infraestrutura,web}/...` (`Academia`, `StatusAcademia`, `ServicoAcademia`, `ServicoAdministradores`, `AcademiaController`, `AdministradoresController`, repositórios, DTOs)
- Test: `academia/AcademiaIT.java`, `academia/AdministradoresIT.java`

**Interfaces:**
- Consumes: `ContasDeUsuario.garantirUsuario` (Task 2), `Auditoria` e `TenantContext` (Task 4).
- Endpoints (design §7): `POST /api/v1/academias` (sem tenant; cria + vincula criador; CNPJ duplicado → 409 `CNPJ_JA_CADASTRADO`); `GET/PUT /api/v1/academia`; `PATCH /api/v1/academia/status {status}`; `GET /api/v1/administradores` (lista `{usuarioId, nome (do vínculo), email}`); `POST /api/v1/administradores {nome, email, senhaInicial}` (201 idêntico existindo ou não; já vinculado → 409 `JA_E_ADMINISTRADOR`); `DELETE /api/v1/administradores/{usuarioId}` (último → 409 `ULTIMO_ADMINISTRADOR`).
- `academia` não é entidade de tenant (tabela global): o serviço sempre usa o `academiaId` do contexto.

- [ ] **Step 1: Testes que falham:**
  - `criarNovaAcademiaVinculaCriador`; `alunoTambemPodeCriarAcademia`; `cnpjDuplicado409`.
  - `editarDadosDaAcademiaAtiva`; `inativarEReativar` (inativa → PUT 409 `ACADEMIA_INATIVA`; PATCH status ATIVA → 200).
  - `conviteParaEmailNovoEExistenteRespondemIgual`: mesmo status e mesmo corpo (exceto ids); para o e-mail existente a senha antiga continua valendo; a lista mostra o **nome digitado no convite**.
  - `convidadoNovoDeveTrocarSenha`.
  - `naoRemoveUltimoAdministrador`; `adminPodeSairSeNaoForOUltimo`.
  - `adminRemovidoPerdeAcessoNaHora`: B removido por A → próxima requisição de B com token ainda válido → 403 `SEM_VINCULO`.
  - `isolamento`: admin de A com `X-Academia-Id` de B → 403.
  - `auditoria`: criação, status e adição/remoção de admin geram linhas com a `AcaoAuditada` certa e sem campos de senha.
- [ ] **Step 2: Ver falhar.** **Step 3: Implementar.** **Step 4: Ver passar.**
- [ ] **Step 5: Commit** — `feat(academia): academias e administradores`.

---

### Task 6: Planos e professores

**Files:**
- Create: `plano/{dominio,aplicacao,infraestrutura,web}/...` (`Plano extends EntidadeDeTenant`, `Periodicidade`, `StatusPlano`), `professor/{...}/...` (`Professor extends EntidadeDeTenant`, `StatusProfessor`); `compartilhado/web/PaginaResposta.java`
- Test: `plano/PlanosIT.java`, `professor/ProfessoresIT.java`, `plano/dominio/PlanoTest.java`

**Interfaces:**
- Produces: `record PaginaResposta<T>(List<T> itens, int pagina, int tamanho, long total)` com `static <T> PaginaResposta<T> de(Page<T>)`.
- Planos: `GET /api/v1/planos?status=` (lista completa, sem paginação — poucos itens), `POST`, `GET/PUT /{id}`, `PATCH /{id}/status`. `valor > 0` com 2 casas; nome único na academia → 409 `PLANO_JA_EXISTE`.
- Professores: `GET /api/v1/professores?busca=&status=&pagina=&tamanho=` (busca em nome/e-mail, case-insensitive), `POST`, `GET/PUT /{id}`, `PATCH /{id}/status`.

- [ ] **Step 1: Testes que falham:**
  - `PlanoTest`: valor zero/negativo recusado; nome em branco recusado.
  - `PlanosIT`: `crudCompleto`; `nomeDuplicadoNaMesmaAcademia409`; `mesmoNomeEmOutraAcademiaPermitido`; `filtroPorStatus`; `semExclusao` (`DELETE` → 405); `academiaIdVemDoContexto` (corpo com `academiaId` de B é ignorado; o plano fica em A); `isolamento` (GET/PUT/PATCH de plano de B com contexto A → 404); `alunoNaoAcessa` (contexto ALUNO → 403); `statusAuditado`.
  - `ProfessoresIT`: `crudCompleto`; `buscaPorNomeEEmail`; `paginacao` (25 itens, tamanho 10 → 3 páginas, `total == 25`); `isolamento`; `statusAuditado`.
- [ ] **Step 2: Ver falhar.** **Step 3: Implementar.** **Step 4: Ver passar.**
- [ ] **Step 5: Commit** — `feat(plano,professor): cadastros de planos e professores`.

---

### Task 7: Alunos e "Meus dados"

**Files:**
- Create: `aluno/{dominio,aplicacao,infraestrutura,web}/...` (`Aluno extends EntidadeDeTenant`, `Cpf`, `StatusAluno`, `ServicoAlunos`, `AlunosController`, `MeusDadosController`)
- Test: `aluno/dominio/CpfTest.java`, `aluno/AlunosIT.java`

**Interfaces:**
- Consumes: `ContasDeUsuario.garantirUsuario`, `Auditoria`, `PaginaResposta`.
- Produces: `Cpf.de(String bruto): Cpf` — remove não-dígitos, exige 11 dígitos, rejeita sequências repetidas e valida os dígitos verificadores; `toString()` = 11 dígitos.
- Endpoints: `GET /api/v1/alunos?busca=&status=&pagina=&tamanho=` (busca em nome, CPF e e-mail); `POST /api/v1/alunos {nome, email, cpf, telefone, dataNascimento, endereco, cidade, estado, cep, senhaInicial}`; `GET/PUT /api/v1/alunos/{id}` (PUT **não** altera e-mail); `PATCH /api/v1/alunos/{id}/status`; `GET /api/v1/eu/aluno` (perfil ALUNO).
- Erros: CPF inválido → 400 (`erros[].campo == "cpf"`); CPF duplicado na academia → 409 `CPF_JA_CADASTRADO`; mesmo usuário já aluno na academia → 409 `ALUNO_JA_CADASTRADO`.

- [ ] **Step 1: Testes que falham:**
  - `CpfTest`: válido com e sem máscara; dígito verificador errado; `11111111111`; tamanho errado.
  - `AlunosIT`: `crudCompleto`; `cpfInvalido400`; `cpfDuplicadoNaAcademia409`; `mesmoCpfEmOutraAcademiaPermitido`; `emailExistenteLigaSemRevelar` (resposta igual à de e-mail novo; a senha antiga continua valendo; o aluno passa a ter vínculo ALUNO em `vinculos`); `putNaoAlteraEmail`; `buscaPorNomeCpfEmail`; `isolamento` (404 entre academias); `statusAuditado`.
  - `meusDadosDoAluno`: aluno logado com contexto ALUNO → 200 com os próprios dados; contexto ADMIN em `/eu/aluno` → 403.
  - `alunoInativoAindaAcessaMeusDados`; `alunoCanceladoPerdeAcesso` (403 `SEM_VINCULO` e some de `/eu/vinculos`); `reativadoVoltaATerAcesso`.
  - `adminEAlunoNaMesmaAcademia`: o mesmo usuário com os dois vínculos acessa `/alunos` com `X-Perfil: ADMIN` e `/eu/aluno` com `X-Perfil: ALUNO`.
- [ ] **Step 2: Ver falhar.** **Step 3: Implementar.** **Step 4: Ver passar** (`./mvnw -B verify`).
- [ ] **Step 5: Commit** — `feat(aluno): cadastro de alunos e meus dados`.

---

### Task 8: Frontend — sessão, interceptor e guards

**Files:**
- Create: `frontend/src/app/core/auth/{auth.service.ts,auth.interceptor.ts,guards.ts,modelos.ts}` (+ `.spec.ts`), `core/contexto/contexto.service.ts` (+ spec)
- Modify: `app.config.ts`

**Interfaces:**
- Produces: `AuthService` — signals `usuario()`, `vinculos()`, `autenticado()`; `login(email, senha): Observable<void>`, `cadastrarAdministrador(dados): Observable<void>`, `renovar(): Observable<boolean>` (executado dentro de `navigator.locks.request('locgym-refresh', ...)` quando disponível), `trocarSenha(atual, nova)`, `logout()`, `accessToken(): string | null`, `iniciar(): Promise<void>` (tenta `renovar` na carga, via `provideAppInitializer`).
- Produces: `ContextoService` — signal `atual(): {academiaId, perfil} | null`; `selecionar(academiaId, perfil)`; persiste em `localStorage['locgym.contexto']`; ao carregar vínculos, descarta contexto que não existe mais e escolhe o primeiro vínculo se houver um só.
- Produces: `authInterceptor` (funcional) — adiciona `Authorization: Bearer` e, se houver contexto, `X-Academia-Id`/`X-Perfil` (não em `/api/v1/auth/**`); em 401 de rota não-auth: chama `renovar()` **uma vez** (requisições concorrentes compartilham a mesma renovação), repete; falhou → `logout()` local e navega para `/login`. Em 403 `TROCA_DE_SENHA_OBRIGATORIA` → `/trocar-senha`.
- Produces: guards `autenticadoGuard`, `trocaDeSenhaGuard`, `perfilGuard(perfil)`.

- [ ] **Step 1: Testes que falham (Vitest):**
  - `auth.service.spec`: `loginGuardaTokenEVinculos`; `logoutLimpaEstado`; `iniciarRenovaSessao` (refresh 200 → autenticado) e `iniciarSemSessao` (refresh 401 → não autenticado, sem erro).
  - `auth.interceptor.spec`: `adicionaHeaders`; `naoAdicionaContextoEmAuth`; `renovaUmaVezERepete`; `duasRequisicoes401CompartilhamUmRefresh` (só 1 chamada a `/auth/refresh`); `refreshFalhaVaiParaLogin`; `trocaDeSenhaRedireciona`.
  - `guards.spec`: não autenticado → `/login`; `deveTrocarSenha` → `/trocar-senha`; perfil diferente → `/`.
  - `contexto.service.spec`: persiste e restaura; descarta contexto inválido; vínculo único é selecionado.
- [ ] **Step 2: Ver falhar** — `cd frontend && npx ng test --watch=false`.
- [ ] **Step 3: Implementar.** **Step 4: Ver passar** (`npx ng test --watch=false && npx ng lint`).
- [ ] **Step 5: Commit** — `feat(frontend): sessão com refresh, interceptor e guards`.

---

### Task 9: Frontend — telas públicas, seletor e painel

**Files:**
- Create: `paginas/login/`, `paginas/cadastro-administrador/`, `paginas/trocar-senha/`, `layout/seletor-contexto/`, `paginas/admin/{alunos,professores,planos,administradores,academia}/` (lista + formulário em cada), `paginas/aluno/meus-dados/`, `core/validadores.ts` (+ specs)
- Modify: `app.routes.ts`, `layout/shell-admin/shell-admin.ts`, `layout/shell-aluno/shell-aluno.ts`, `paginas/inicio/inicio.*`

**Interfaces:**
- Rotas: `/login`, `/criar-academia`, `/trocar-senha` (públicas/autenticadas); `/admin/{alunos,professores,planos,administradores,academia}` com `perfilGuard('ADMIN')`; `/aluno/meus-dados` com `perfilGuard('ALUNO')`. `/` redireciona conforme sessão/contexto (sem sessão → página inicial atual com botões "Entrar" e "Criar minha academia").
- Seletor na toolbar dos dois shells: lista pares (academia, perfil) de `vinculos()`; escolher navega para `/admin` ou `/aluno`; item "Criar academia" e "Sair".
- Validadores: `cpfValido`, `senhaMinima(8)`, `confirmaSenha`.
- Listas com `mat-table` + `mat-paginator` (alunos, professores), filtro de busca com debounce de 300 ms; formulários com `MatSnackBar` para sucesso e mensagem do `detail` do Problem Details em erro; 409 `ACADEMIA_INATIVA` exibe aviso fixo "Academia inativa — somente leitura".

- [ ] **Step 1: Testes que falham:**
  - `validadores.spec`: CPF válido/inválido (mesmos casos do `CpfTest`), senha curta, confirmação diferente.
  - `login.spec`: credenciais inválidas mostram "E-mail ou senha inválidos"; sucesso com um vínculo ADMIN navega para `/admin`.
  - `trocar-senha.spec`: envia e navega; senha curta bloqueia envio.
  - `seletor-contexto.spec`: lista os vínculos e, ao escolher ALUNO, navega para `/aluno`.
  - `alunos-lista.spec`: renderiza linhas e dispara busca com debounce; `aluno-form.spec`: CPF inválido bloqueia envio; e-mail desabilitado em edição.
  - `planos.spec`: lista com status e ação de ativar/inativar.
  - `administradores.spec`: botão remover desabilitado quando há um só admin.
  - `meus-dados.spec`: mostra os dados do aluno.
- [ ] **Step 2: Ver falhar.** **Step 3: Implementar.** **Step 4: Ver passar** — `npx ng test --watch=false && npx ng lint && npx ng build`.
- [ ] **Step 5: Commit** — `feat(frontend): login, troca de senha, seletor de academia e cadastros do painel`.

---

### Task 10: Ponta a ponta, documentação e roadmap

**Files:**
- Modify: `README.md`, `docs/02..09`, `docs/08-roadmap.md`, `.env.example`, `docker-compose.yml` (se faltou algo)

- [ ] **Step 1: Verificação de ponta a ponta** — `docker compose down` (sem `-v`) → `docker compose up -d --build --wait`; via `curl` em `localhost:4200`: cadastro de administrador → login → `GET /api/v1/eu/vinculos` → criar plano → criar aluno → login do aluno (403 `TROCA_DE_SENHA_OBRIGATORIA`) → trocar senha → `GET /api/v1/eu/aluno` 200. Confirmar no banco (`docker compose exec postgres psql`) que o backend conecta como `locgym_app`. Kafka parado → login e cadastros continuam funcionando.
- [ ] **Step 2: Documentação** — 02 (RF01–RF05 implementados, tabela de acesso por status); 03 (isolamento, segurança e auditoria ✅); 04 (ER real das migrations V2–V4, papéis de banco, função de vínculos); 05 (módulos novos, fluxo do contexto de tenant); 06 (todos os endpoints da fase com códigos de erro); 08 (Fase 2 ✅; seção "Ideias candidatas para a Fase 3": autocadastro com confirmação de e-mail, "Notificar administrador", e-mail da academia configurável, prazo curto da primeira cobrança); 09 (ADR-011 JWT RSA + refresh em cookie HttpOnly com rotação e janela de 10 s; ADR-012 `@TenantId` + RLS + papel `locgym_app` + `set_config` por transação; ADR-013 função `SECURITY DEFINER` para vínculos; ADR-014 contexto por par academia/perfil); README (como criar a primeira academia, variáveis novas).
- [ ] **Step 3: Suíte completa** — `cd backend && ./mvnw -B verify` e `cd frontend && npx ng lint && npx ng test --watch=false && npx ng build` → verdes; links da documentação sem quebra.
- [ ] **Step 4: Commit** — `docs: documentação da Fase 2`.

---

## Critério de pronto da Fase 2

- Suítes de backend e frontend verdes, incluindo RLS rodando como `locgym_app` e testes de isolamento por endpoint.
- Fluxo de ponta a ponta via compose funcionando, inclusive com o Kafka parado.
- Nenhum módulo de matrícula, cobrança, check-in ou e-mail.
