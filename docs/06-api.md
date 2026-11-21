# 06 — API

## Convenções

- REST sob **`/api/v1`**, JSON, nomes de recursos em português (`/api/v1/alunos`).
- **Erros em Problem Details (RFC 7807)**, `Content-Type: application/problem+json`:
  - Validação → `400` com a lista de campos:
    ```json
    {
      "title": "Dados inválidos",
      "status": 400,
      "detail": "Requisição inválida",
      "erros": [{ "campo": "nome", "mensagem": "não deve estar em branco" }]
    }
    ```
  - Erro inesperado → `500` com `"detail": "Erro interno inesperado"`, sem mensagem nem stack trace internos (o detalhe vai apenas para o log).
- Datas/horas em ISO-8601; instantes em UTC.
- Documentação interativa: **`/swagger-ui.html`** (OpenAPI em `/v3/api-docs`).

### Planejado (Fase 2)

- `Authorization: Bearer <JWT>` em todas as rotas protegidas.
- `X-Academia-Id: <uuid>` define a academia ativa; validado contra os vínculos do token.
- Listagens paginadas (`page`, `size`, `sort`).

## Endpoints disponíveis (Fase 1)

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/actuator/health` | Estado agregado (`UP`/`DOWN`). |
| GET | `/actuator/health/liveness` | Processo respondendo. Não verifica dependências. |
| GET | `/actuator/health/readiness` | Pronto para tráfego: verifica o banco. **Não verifica o Kafka.** |
| GET | `/actuator/info` | Informações da aplicação. |
| GET | `/actuator/prometheus` | Métricas no formato Prometheus. |
| GET | `/v3/api-docs` | Especificação OpenAPI. |
| GET | `/swagger-ui.html` | Swagger UI. |

Os endpoints de negócio serão documentados aqui a cada fase.
