# LocGym — Fase 2 (Identidade e cadastros) — Design

Data: 2026-11-21
Status: aprovado pelo usuário em 2026-11-21
Base: [design geral aprovado](design.md) (§5.3 multi-tenancy, §5.4 identidade, §5.7 auditoria) e [especificação](especificacao.md).

## 1. Intenção

Dar ao LocGym **quem é quem** e **de qual academia é cada dado**: login seguro, perfis por academia, isolamento entre academias garantido pela aplicação **e** pelo banco, auditoria das operações críticas e os cadastros base (academias, administradores, alunos, professores, planos). É a fundação de negócio sobre a qual matrícula, cobrança e check-in (Fases 3–4) serão construídos.

Para o portfólio, o destaque é o **multi-tenancy verificável**: testes que provam que uma academia não lê nem altera dados de outra, mesmo com SQL nativo.

## 2. Objetivos, não-objetivos, anti-objetivos

**Objetivos**
- Login por e-mail/senha, JWT + refresh token, troca de senha obrigatória no primeiro acesso.
- Perfis ADMIN/ALUNO derivados dos vínculos; seletor de academia ativa.
- Isolamento por academia em duas camadas (Hibernate `@TenantId` + RLS).
- Auditoria das operações críticas desta fase.
- Cadastros: autocadastro de administrador com a primeira academia; criar novas academias; convidar/remover administradores; alunos; professores; planos.
- Frontend: login, troca de senha, cadastro de administrador, seletor de academia, telas de cadastro no painel, "Meus dados" do aluno.

**Não-objetivos (ficam para depois)**
- Matrícula, cobrança, pagamentos, check-in, relatórios (Fases 3–5).
- Envio de e-mail, confirmação de e-mail, recuperação de senha, autocadastro de aluno.
- Portal do aluno completo (Fase 5); tela de consulta da auditoria.
- Eventos Kafka de negócio (o catálogo começa na Fase 3).

**Anti-objetivos**
- Qualquer caminho em que um usuário veja ou altere dado de academia à qual não está vinculado.
- Revelar a um admin que um e-mail ou CPF existe em outra academia.
- Aplicação conectando ao banco com um papel que ignore RLS (superusuário ou dono das tabelas sem `FORCE`).

## 3. Abordagens consideradas (isolamento)

| Abordagem | Trade-off | Decisão |
|---|---|---|
| **A) Hibernate `@TenantId` grava/filtra `academia_id` + RLS no Postgres** | Impossível esquecer o filtro na aplicação; SQL nativo barrado pelo banco; um pouco mais de configuração | **Escolhida pelo usuário** |
| B) Só RLS | Um mecanismo só, falha segura; `academia_id` gravado à mão, erros aparecem só como "não encontrado" | Descartada |
| C) `academiaId` explícito nos repositórios + RLS | Explícito, mas verboso e fácil de esquecer | Descartada |

Autenticação (decisão minha): suporte nativo do Spring Security (OAuth2 Resource Server + `JwtEncoder`/`JwtDecoder` Nimbus), chave **RSA**; sem biblioteca JWT externa.

## 4. Modelo de dados (migrations V2+)

Tabelas **globais** (sem RLS — acesso controlado pela aplicação):
- `usuario` — id, `email` (único, armazenado em minúsculas), `nome`, `senha_hash` (BCrypt), `deve_trocar_senha` (bool), criado_em, atualizado_em.
- `academia` — id, nome, cnpj (único), endereço, cidade, estado (UF), cep, telefone, e-mail, `status` (ATIVA/INATIVA), criado_em, atualizado_em.
- `administrador_academia` — (usuario_id, academia_id) PK, `nome` (nome informado para esta academia), criado_em. A lista de admins mostra esse `nome`, nunca o nome global do usuário — assim um convite para e-mail já existente não revela a conta. *(Decisão minha.)*
- `refresh_token` — id, usuario_id, `token_hash` (SHA-256, único), `familia` (uuid), expira_em, `usado_em`, `revogado_em`, criado_em.

Tabelas **de tenant** (com `academia_id` NOT NULL, FK, indexado, RLS + `FORCE ROW LEVEL SECURITY`):
- `aluno` — id, academia_id, usuario_id (FK), nome, cpf (11 dígitos), telefone, data_nascimento, endereço, cidade, estado, cep, `status` (ATIVO/INATIVO/CANCELADO), criado_em, atualizado_em. `UNIQUE (academia_id, cpf)`, `UNIQUE (academia_id, usuario_id)`.
- `professor` — id, academia_id, nome, e-mail, telefone, data_nascimento, especialidade, `status` (ATIVO/INATIVO), criado_em, atualizado_em.
- `plano` — id, academia_id, nome, descrição, `valor` numeric(10,2) > 0, `periodicidade` (MENSAL/TRIMESTRAL/SEMESTRAL/ANUAL), `status` (ATIVO/INATIVO), criado_em, atualizado_em. `UNIQUE (academia_id, nome)`.
- `auditoria` — id, academia_id, usuario_id, `acao`, `entidade`, `entidade_id`, `antes` jsonb, `depois` jsonb, ocorrido_em.

Constraints `CHECK` para todos os enums, UF com 2 letras, CEP e CPF só dígitos. Plano nunca é apagado — só inativado (matrículas futuras referenciam).

`outbox_evento` continua **sem RLS** (tabela técnica lida pelo relay para todas as academias).

## 5. Multi-tenancy

**Contexto ativo** = headers `X-Academia-Id` e `X-Perfil` (ADMIN ou ALUNO) em toda rota de tenant. O seletor do frontend escolhe um par (academia, perfil) — necessário porque a mesma pessoa pode ser ADMIN e ALUNO na mesma academia. *(Decisão minha.)*

1. Filtro de requisição (após autenticar o JWT): exige o header nas rotas de tenant; verifica **no banco** que o usuário tem o vínculo com aquela academia **naquele perfil**; define `TenantContext` (academiaId + perfil) e uma única authority (`ROLE_ADMIN` ou `ROLE_ALUNO`). Sem vínculo → **403**. O vínculo é consultado a cada requisição (não confiamos só no token), para que a remoção de um admin valha imediatamente.
2. **Hibernate `@TenantId`** no campo `academiaId` de toda entidade de tenant + `CurrentTenantIdentifierResolver` lendo o `TenantContext`: o Hibernate preenche `academia_id` nos INSERTs e acrescenta o filtro nas consultas JPQL/Criteria.
3. **RLS**: no início de cada transação, a aplicação executa `select set_config('app.academia_id', <id>, true)` (escopo da transação). Policies:
   `using (academia_id = nullif(current_setting('app.academia_id', true), '')::uuid)` e `with check` igual. Sem academia definida → nenhuma linha visível e nenhum INSERT aceito.
4. **Papéis de banco**: as migrations rodam com o dono do schema (`locgym`); a aplicação conecta como **`locgym_app`** — não é superusuário, não é dono das tabelas, `NOBYPASSRLS`, só com `SELECT/INSERT/UPDATE/DELETE` nas tabelas e `USAGE` nas sequences. Senha via variável de ambiente (placeholder do Flyway). **Os testes de integração também rodam a aplicação como `locgym_app`**, senão o RLS não estaria sendo testado.
5. **Login e listagem de vínculos** precisam ler `aluno` de várias academias: feito por uma função `SECURITY DEFINER` `vinculos_do_usuario(usuario_id)` que retorna `(academia_id, academia_nome, perfil)` — a única porta controlada que atravessa academias.
6. **Acesso conforme status (aprovado pelo usuário):**

   | Situação | Aparece no seletor e faz login? | O que pode fazer |
   |---|---|---|
   | Aluno ATIVO | Sim | Tudo o que o perfil permite |
   | Aluno INATIVO | Sim | Só consultar os próprios dados (depois também pagamentos e frequência) |
   | Aluno CANCELADO | **Não** — o vínculo ALUNO deixa de existir para login, seletor e filtro de requisição | Nada; reativado, volta a ter acesso |
   | Academia INATIVA | Sim, para admins e alunos | Somente leitura |

   A regra fica em `vinculos_do_usuario` **e** no filtro de requisição, com testes próprios. Nesta fase o aluno só tem leitura ("Meus dados"), então ATIVO e INATIVO se comportam igual; a distinção passa a valer nas fases 3–4.
7. **Academia INATIVA = somente leitura**: escritas em rotas de tenant retornam **409** com mensagem clara; a única escrita permitida é reativar a academia.
8. Rotas sem tenant: autenticação, cadastro de administrador, "minhas academias", troca de senha, criação de nova academia.

## 6. Identidade e autenticação

- **Login** `POST /api/v1/auth/login {email, senha}` → `{accessToken, expiraEm, usuario{id,nome,email,deveTrocarSenha}, vinculos[{academiaId, academiaNome, perfil}]}` + cookie do refresh. Credenciais inválidas → **401 com mensagem genérica** ("E-mail ou senha inválidos"), mesmo tempo de resposta aproximado (BCrypt executado mesmo quando o e-mail não existe).
- **Access token**: JWT RS256, 15 min, claims `sub` (usuarioId), `nome`, `deve_trocar_senha`. Guardado **em memória** no frontend.
- **Refresh token**: valor aleatório de 256 bits, 7 dias, guardado só como hash SHA-256; entregue em **cookie `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`**. `POST /api/v1/auth/refresh` **rotaciona** (marca o atual como usado, emite outro da mesma família). **Reuso** de um token já usado → revoga a família inteira (sinal de roubo), **exceto numa janela de tolerância de 10 s** após o uso: aí o reuso é tratado como corrida entre abas/requisições e emite um novo token da mesma família, sem revogar. No frontend, o refresh é serializado entre abas com Web Locks. *(Decisão minha.)* `POST /api/v1/auth/logout` revoga o token atual e apaga o cookie.
- **Chave RSA**: lida de variável de ambiente (PEM) quando presente; em dev/test, gerada na subida (com log de aviso; tokens não sobrevivem a reinício — aceitável em dev).
- **Senha**: mínimo 8 caracteres; BCrypt.
- **Troca de senha obrigatória**: enquanto `deve_trocar_senha = true`, toda rota exceto `trocar-senha`, `logout` e `refresh` responde **403** com código `TROCA_DE_SENHA_OBRIGATORIA`. `POST /api/v1/auth/trocar-senha {senhaAtual, novaSenha}` devolve um **novo access token** (o claim `deve_trocar_senha` do anterior ficaria desatualizado).
- **"Minhas academias"** `GET /api/v1/eu/vinculos` (frontend chama após login/refresh e ao trocar de academia).

## 7. Cadastros e regras

**Cadastro público de administrador** `POST /api/v1/auth/cadastro-administrador {nome, email, senha, academia{...}}`: cria usuário, academia ATIVA e vínculo de admin; já retorna o login. E-mail já existente → **409** "E-mail já cadastrado" (aqui não há o que esconder: é o próprio dono do e-mail se cadastrando).

**Academias**
- `POST /api/v1/academias` (qualquer usuário autenticado, inclusive quem só é aluno — espelha o cadastro público) cria nova academia e vincula o criador como admin. CNPJ duplicado → 409 (CNPJ é dado público). *(Decisão minha.)*
- `GET/PUT /api/v1/academia` (tenant, ADMIN) — dados da academia ativa; `PATCH .../status` ativa/inativa (auditado).

**Administradores** (tenant, ADMIN) — todos com o mesmo poder.
- `GET /api/v1/administradores`.
- `POST /api/v1/administradores {nome, email, senhaInicial}`: se o e-mail não existe, cria usuário com `deve_trocar_senha`; se existe, só cria o vínculo e **ignora** a senha informada. Resposta idêntica nos dois casos (201). Já vinculado → 409.
- `DELETE /api/v1/administradores/{usuarioId}`: nunca remove o último admin (409); um admin pode remover a si mesmo se não for o último. Auditado.

**Alunos** (tenant, ADMIN)
- `GET /api/v1/alunos?busca=&status=&page=&size=` — busca por nome, CPF ou e-mail.
- `POST /api/v1/alunos {nome, email, cpf, telefone, dataNascimento, endereço..., senhaInicial}`: CPF validado (dígitos verificadores) e único na academia (409 "CPF já cadastrado nesta academia"); e-mail: mesma regra dos administradores (liga ao usuário existente sem revelar; senha ignorada se já existe). Mesmo usuário duas vezes na mesma academia → 409.
- `GET/PUT /api/v1/alunos/{id}`; `PATCH /api/v1/alunos/{id}/status` (auditado).
- **E-mail não é editável pelo admin** nesta fase: pertence ao login global e mudaria o acesso do aluno em outras academias. *(Decisão minha.)*
- Status do aluno independente de matrícula (regra aprovada).

**Professores** (tenant, ADMIN) — CRUD sem login: `GET` com busca/paginação, `POST`, `GET/PUT {id}`, `PATCH {id}/status` (auditado).

**Planos** (tenant, ADMIN) — `GET` (filtro por status), `POST`, `GET/PUT {id}`, `PATCH {id}/status` (auditado). Sem exclusão. Nome único na academia.

**Aluno logado** (tenant, ALUNO) — `GET /api/v1/eu/aluno`: seus dados naquela academia (somente leitura).

**Erros**: Problem Details da Fase 1; 404 para recurso de outra academia (indistinguível de inexistente); 409 para conflitos de regra; 403 para falta de vínculo/perfil.

## 8. Auditoria

- Porta `Auditoria.registrar(acao, entidade, entidadeId, antes, depois)` chamada explicitamente pelos casos de uso, **na mesma transação** (sem listener mágico); usuário e academia vêm do contexto.
- Operações auditadas nesta fase: criação de academia, mudança de status da academia, adição/remoção de admin, mudança de status de aluno, professor e plano.
- `antes`/`depois` em JSONB com os campos relevantes (nunca senha/hash).
- Sem tela de consulta nesta fase; os testes verificam a tabela.

## 9. Frontend

- **Autenticação**: `AuthService` (estado em signals; access token em memória), interceptor que adiciona `Authorization` e `X-Academia-Id` e, num 401, tenta **um** refresh e repete a requisição; na falha, vai para o login. Na carga da página, tenta `refresh` (o cookie mantém a sessão).
- **Guards**: autenticado, perfil (ADMIN/ALUNO na academia ativa), troca de senha obrigatória.
- **Telas públicas**: Login, "Criar minha academia" (cadastro de administrador).
- **Troca de senha** (obrigatória no primeiro acesso).
- **Seletor de academia** na toolbar (quando há mais de um vínculo); a escolha fica salva no navegador; trocar de academia leva à área do perfil correspondente.
- **Painel (`/admin`)**: Alunos (lista com busca e paginação, criar, editar, status), Professores, Planos, Administradores (listar, convidar, remover), Academia (dados, ativar/inativar, criar nova academia).
- **Aluno (`/aluno`)**: "Meus dados" (somente leitura).
- Validações nos formulários espelhando as do backend (CPF, senha ≥ 8, campos obrigatórios); mensagens em português.

## 10. Testes

- **RLS** (IT, aplicação como `locgym_app`): SQL nativo sem academia definida → 0 linhas; com academia A → só linhas de A; INSERT com `academia_id` de outra academia → erro.
- **Isolamento por endpoint** (IT): para cada recurso de tenant (alunos, professores, planos, administradores, academia), admin da academia A tentando ler/alterar dado da B → 404; header de academia sem vínculo → 403; aluno tentando rota de admin → 403.
- **Autenticação**: login ok; senha errada e e-mail inexistente → mesma resposta 401; refresh rotaciona; reuso revoga a família; logout; troca de senha obrigatória bloqueia rotas; remoção de admin vale na hora.
- **Regras**: CPF inválido/duplicado; e-mail existente liga sem revelar (respostas idênticas); último admin não sai; academia inativa recusa escritas e aceita reativação; auditoria gravada nas operações listadas, sem senha.
- **Arquitetura**: novos módulos passam no `ApplicationModules.verify()` e nas regras de camadas (que passam a ter classes reais).
- **Frontend**: testes unitários de `AuthService`, interceptor (refresh único, header de academia), guards e dos formulários principais.

## 11. Infra e documentação

- Compose/`.env.example`: `DB_APP_USER`/`DB_APP_PASSWORD` (papel da aplicação) e `JWT_CHAVE_PRIVADA` opcional. Nenhum serviço novo.
- Docs: atualizar 02, 03, 04 (ER real), 05, 06 (endpoints), 08 (Fase 2 concluída; **ideias candidatas para a Fase 3**: autocadastro com confirmação de e-mail, botão "Notificar administrador", e-mail da academia configurável, prazo curto para a primeira cobrança), 09 (ADRs: JWT RSA + refresh em cookie HttpOnly; implementação do tenancy com `@TenantId` + RLS + papel `locgym_app`; função `SECURITY DEFINER` para vínculos).

## 12. Deixado para o construtor
- Mecanismo exato para executar `set_config` no início da transação (ex.: hook no gerenciador de transação ou wrapper de conexão), desde que coberto pelos testes de RLS.
- Como os testes obtêm a conexão do papel `locgym_app` com Testcontainers.
- Estrutura de pacotes/DTOs dentro de cada módulo, seguindo `dominio/aplicacao/infraestrutura/web`.
- Layout visual das telas, componentes de tabela/paginação.

## 13. Decisões minhas (revisáveis)

(Aprovadas pelo usuário: abordagem A de isolamento; tabela de acesso por status do §5.6.)

- JWT via Spring Security com RSA; access token em memória; refresh em cookie HttpOnly.
- Vínculo verificado no banco a cada requisição (remoção de admin vale na hora).
- Função `SECURITY DEFINER` como única leitura entre academias.
- E-mail do aluno não editável pelo admin.
- Cadastro de administrador revela "e-mail já cadastrado" (é o próprio dono); convites e alunos não revelam.
- Nenhum evento Kafka nesta fase.
- Par (academia, perfil) no contexto (`X-Perfil`); nome do admin por vínculo; janela de 10 s no reuso de refresh + Web Locks; novo access token após trocar senha; qualquer usuário autenticado cria academia; admin pode sair se não for o último.
- Plano sem exclusão, nome único por academia.
