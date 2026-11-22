# 06 — API

## Convenções

- REST sob **`/api/v1`**, JSON, nomes de recursos em português.
- **Autenticação:** `Authorization: Bearer <access token>` (JWT RS256, 15 min). O refresh token (7 dias) vive no cookie `locgym_refresh` — `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` — e nunca é lido pelo JavaScript.
- **Contexto de tenant:** toda rota de academia exige os headers **`X-Academia-Id`** (uuid) e **`X-Perfil`** (`ADMIN` ou `ALUNO`). O backend confere o vínculo no banco a cada requisição.
- **Erros em Problem Details (RFC 7807)**, `Content-Type: application/problem+json`, com `codigo` estável nos erros de regra:
  ```json
  { "title": "Operação não permitida", "status": 409, "detail": "CPF já cadastrado nesta academia", "codigo": "CPF_JA_CADASTRADO" }
  ```
  Validação → `400` com `erros: [{ campo, mensagem }]`. Erro inesperado → `500` sem detalhes internos.
- Listagens paginadas: `pagina` (começa em 0), `tamanho` (padrão 20, máximo 100) → `{ itens, pagina, tamanho, total }`.
- Datas ISO-8601; CPF, CNPJ e CEP aceitam máscara na entrada e são devolvidos só com dígitos.
- Documentação interativa: **`/swagger-ui.html`** (OpenAPI em `/v3/api-docs`).

### Status HTTP e códigos

| Situação | Status | `codigo` |
|---|---|---|
| Sem token / token inválido / credenciais inválidas | 401 | — |
| Senha inicial ainda não trocada | 403 | `TROCA_DE_SENHA_OBRIGATORIA` |
| Headers de contexto ausentes ou inválidos | 400 | `CONTEXTO_AUSENTE` |
| Sem vínculo com a academia nesse perfil (inclui aluno cancelado e admin removido) | 403 | `SEM_VINCULO` |
| Perfil sem permissão para a rota (ex.: aluno em rota de admin) | 403 | — |
| Escrita em academia inativa | 409 | `ACADEMIA_INATIVA` |
| Recurso inexistente **ou de outra academia** (indistinguíveis) | 404 | — |
| Regras: `EMAIL_JA_CADASTRADO`, `CNPJ_JA_CADASTRADO`, `JA_E_ADMINISTRADOR`, `ULTIMO_ADMINISTRADOR`, `PLANO_JA_EXISTE`, `CPF_JA_CADASTRADO`, `ALUNO_JA_CADASTRADO` | 409 | o próprio |

## Autenticação e sessão (sem contexto)

| Método | Caminho | Descrição |
|---|---|---|
| POST | `/api/v1/auth/cadastro-administrador` | Público. `{nome, email, senha, academia{nome, cnpj, endereco, cidade, estado, cep, telefone, email}}` → 201 com a sessão e o cookie. |
| POST | `/api/v1/auth/login` | Público. `{email, senha}` → sessão `{accessToken, expiraEm, usuario{id, nome, email, deveTrocarSenha}, vinculos[{academiaId, academiaNome, academiaStatus, perfil}]}` + cookie. Credenciais inválidas → 401 genérico. |
| POST | `/api/v1/auth/refresh` | Público (usa o cookie). Rotaciona o refresh e devolve nova sessão. Reuso de token já usado: tolerado por 10 s; depois, revoga a sessão inteira (401). |
| POST | `/api/v1/auth/logout` | Revoga a sessão e apaga o cookie (204). |
| POST | `/api/v1/auth/trocar-senha` | `{senhaAtual, novaSenha}` → nova sessão (e novo cookie) com `deveTrocarSenha: false`. **Todas as outras sessões do usuário são revogadas** — quem conhecia a senha antiga, inclusive a inicial, perde o acesso. |
| GET | `/api/v1/eu/vinculos` | "Minhas academias": vínculos atuais do usuário. |
| POST | `/api/v1/academias` | Qualquer usuário autenticado cria uma academia e vira administrador dela (201). |

## Rotas de administrador (`X-Perfil: ADMIN`)

| Método | Caminho | Descrição |
|---|---|---|
| GET / PUT | `/api/v1/academia` | Dados da academia ativa. |
| PATCH | `/api/v1/academia/status` | `{status: ATIVA \| INATIVA}` — única escrita aceita com a academia inativa. Auditado. |
| GET | `/api/v1/administradores` | Lista `{usuarioId, nome, email}` (nome informado no convite). |
| POST | `/api/v1/administradores` | `{nome, email, senhaInicial}` → 201. Mesma resposta para e-mail novo ou existente (conta existente mantém a senha). Auditado. |
| DELETE | `/api/v1/administradores/{usuarioId}` | 204; nunca o último (409). Vale na hora para o removido. Auditado. |
| GET | `/api/v1/alunos?busca=&status=&pagina=&tamanho=` | Busca por nome, e-mail ou CPF. |
| POST | `/api/v1/alunos` | `{nome, email, cpf, telefone, dataNascimento, endereco, cidade, estado, cep, senhaInicial}` → 201. E-mail existente é ligado sem revelar. |
| GET / PUT | `/api/v1/alunos/{id}` | PUT não altera o e-mail de login. |
| PATCH | `/api/v1/alunos/{id}/status` | `{status: ATIVO \| INATIVO \| CANCELADO}`. Auditado. |
| GET | `/api/v1/professores?busca=&status=&pagina=&tamanho=` | Busca por nome ou e-mail. |
| POST / GET / PUT | `/api/v1/professores[/{id}]` | Só o nome é obrigatório. |
| PATCH | `/api/v1/professores/{id}/status` | `{status: ATIVO \| INATIVO}`. Auditado. |
| GET | `/api/v1/planos?status=` | Lista completa, ordenada por nome. |
| POST / GET / PUT | `/api/v1/planos[/{id}]` | `{nome, descricao, valor, periodicidade: MENSAL \| TRIMESTRAL \| SEMESTRAL \| ANUAL}`. Sem exclusão (405). |
| PATCH | `/api/v1/planos/{id}/status` | `{status: ATIVO \| INATIVO}`. Auditado. |

## Rotas do aluno (`X-Perfil: ALUNO`)

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/v1/eu/aluno` | "Meus dados" na academia ativa (somente leitura). Aluno INATIVO consulta; CANCELADO perde o acesso. |

## Operacionais

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | Liveness: só o processo. Readiness: banco; **não** o Kafka. |
| GET | `/actuator/prometheus` | Métricas. |
| GET | `/v3/api-docs`, `/swagger-ui.html` | OpenAPI. |
