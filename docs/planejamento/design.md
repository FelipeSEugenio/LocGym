# LocGym — Documento de Design

Data: 2026-11-21
Status: aprovado pelo usuário em 2026-11-21

## 1. Intenção e porquê

O LocGym é um sistema web multi-tenant de gestão para academias de Jiu-Jitsu (requisitos completos em `docs/planejamento/especificacao.md`, que serão desdobrados em `docs/01..09`).

**Por quê:** é um **projeto de portfólio**. Ele precisa demonstrar, para recrutadores e avaliadores técnicos, domínio de arquitetura backend (Clean Architecture, modularidade, multi-tenancy, segurança, mensageria), frontend Angular moderno, testes automatizados e DevOps — e precisa ser fácil de rodar e de ler.

**Sucesso significa:**
- `docker compose up` sobe tudo (Postgres, Kafka, backend, frontend) com dados de demonstração e logins prontos no README.
- Um avaliador consegue, em poucos minutos, entender a arquitetura pelo README e por `docs/`, e encontrar decisões técnicas justificadas (`09-decisoes-tecnicas.md`, em formato ADR).
- CI verde no GitHub Actions, com testes de unidade, integração (Testcontainers) e testes de arquitetura.

## 2. Objetivos, não-objetivos e anti-objetivos

**Objetivos:** todo o escopo de MVP da especificação (seção 2), entregue em fases conforme o roadmap (Fases 1–5).

**Não-objetivos (da especificação):** app mobile nativo, gateway de pagamento, nota fiscal, financeiro completo, estoque, loja, catraca, redes sociais, marketing, app de professor, envio real de WhatsApp, recuperação de senha por e-mail, pagamento parcial, parcelamento de planos não mensais.

**Fora do MVP, para fase posterior (decidido):** demo pública online — deploy/hospedagem, HTTPS, Kafka gerenciado e reset periódico de dados de demo. O MVP roda localmente via Docker Compose.

**Anti-objetivos (falharia mesmo "funcionando"):**
- Qualquer vazamento de dados entre academias.
- Projeto que não roda com um comando, ou que exige configuração manual obscura.
- Over-engineering que o autor não consiga defender numa entrevista (ex.: microsserviços, Kubernetes, CQRS/event sourcing completo).
- Código sem testes nas regras de negócio centrais (matrícula, inadimplência, check-in, isolamento de tenant).

## 3. Restrições

- Stack fixada: Angular + TypeScript; Java 21 + Spring Boot; PostgreSQL; Apache Kafka; Docker/Docker Compose; GitHub Actions; documentação em Markdown.
- Monorepo com a estrutura da seção 17 da especificação.
- Ambiente local atual não tem JDK 21 nem Docker; precisam ser instalados para build/teste.

## 4. Abordagens consideradas

| Abordagem | Trade-off | Decisão |
|---|---|---|
| **A) Monolito modular + Clean Architecture por módulo, schema compartilhado com `academia_id` + RLS, Kafka via outbox** | Menos "chamativo" que microsserviços, mas defensável, rápido de construir e simples de rodar | **Escolhida pelo usuário** |
| B) Microsserviços + API gateway | Infra multiplicada, auth distribuída, consistência eventual em tudo; pesado para rodar e lento para construir; lê como over-engineering | Descartada |
| C) Monolito modular com schema por academia | Isolamento forte, mas migrations N vezes e relatórios difíceis; complexidade que não se paga no MVP | Descartada |

## 5. Arquitetura (decidido)

### 5.1 Visão geral

```
[Angular SPA] --HTTPS/JSON--> [Spring Boot (monolito modular)] --JPA--> [PostgreSQL]
                                         |
                                         +--outbox poller--> [Kafka] --> consumidor interno (notificacao)
```

### 5.2 Backend
- Java 21, Spring Boot 3.x, Maven, Flyway (migrations), Spring Data JPA, Spring Security, springdoc-openapi (Swagger UI), Spring Boot Actuator + Micrometer/Prometheus.
- **Monolito modular** com Spring Modulith para verificar fronteiras entre módulos. Módulos:
  `identidade` (usuários, login, tokens), `academia`, `aluno`, `professor`, `plano`, `matricula`, `pagamento`, `agenda` (horários e aulas), `frequencia` (check-in), `relatorio`, `notificacao`, e `compartilhado` (tenant, outbox, auditoria, erros).
- **Clean Architecture dentro de cada módulo:** `domain` (entidades e regras, sem Spring), `application` (casos de uso, portas), `infrastructure` (JPA, Kafka), `web` (controllers, DTOs). Testes ArchUnit garantem que `domain` não depende de framework.
- Módulos se comunicam por APIs públicas do módulo ou eventos de domínio — nunca acessando repositórios uns dos outros.
- API REST versionada em `/api/v1`, erros no formato RFC 7807 (Problem Details).

### 5.3 Multi-tenancy
- Schema único; toda tabela de dados de academia tem `academia_id` (NOT NULL, FK, indexado).
- **Tenant ativo** vem do header `X-Academia-Id`, validado contra a lista de academias presente no token do usuário. Sem vínculo → 403.
- Duas camadas de isolamento:
  1. Aplicação: `TenantContext` + filtro Hibernate aplicado automaticamente às entidades tenant-scoped.
  2. Banco: **Row-Level Security do PostgreSQL**; a aplicação faz `SET LOCAL app.academia_id` por transação e as policies filtram por esse valor. A aplicação conecta com um usuário de banco sem `BYPASSRLS`.
- Teste de integração dedicado tenta ler/escrever dados de outra academia em cada endpoint e espera 403/404.

### 5.4 Identidade e autenticação
- Tabela global `usuario` = **credencial de login** (e-mail único no sistema, hash de senha **BCrypt**). Não carrega perfil nem dados cadastrais.
- **Perfis vêm dos vínculos**, por academia:
  - `administrador_academia` (usuário ↔ academia) → perfil ADMIN naquela academia.
  - `aluno` (tenant-scoped, com `academia_id` e `usuario_id`) → perfil ALUNO naquela academia.
  - A mesma pessoa pode ser admin numa academia e aluno em outra (ou na mesma).
- **Aluno é separado por academia (aprovado):** cada academia tem seu próprio registro de aluno, com todos os dados cadastrais e o status do aluno (ATIVO/INATIVO/CANCELADO). **CPF é único por academia** (`UNIQUE (academia_id, cpf)`). Editar um aluno numa academia nunca afeta outra. Ao cadastrar um aluno cujo e-mail já tem `usuario`, o sistema apenas liga o novo registro a esse login — sem revelar ao admin que o e-mail existe em outra academia (resposta idêntica em ambos os casos; a senha existente é mantida).
- Login por e-mail/senha → **JWT de acesso (15 min)** + **refresh token (7 dias, opaco, armazenado com hash, rotacionado)**. Token contém `sub` e a lista de vínculos `[{academia_id, perfil}]`.
- Quem tem mais de um vínculo escolhe a academia/perfil ativo após o login (seletor no topo do layout).
- **Administradores (aprovado):** todos os admins de uma academia têm o mesmo poder (sem "dono"). Um admin pode:
  - convidar/criar outro admin para a academia (com senha inicial, troca obrigatória no primeiro acesso) e remover vínculos de admin — nunca o último admin;
  - criar novas academias (fica vinculado automaticamente).
- Cadastro público apenas para administrador (cria conta + primeira academia). Alunos e professores são criados pelo admin; aluno recebe senha inicial definida pelo admin e troca no primeiro acesso.
- **Academia INATIVA (aprovado) fica somente leitura:** logins e consultas funcionam; escritas, check-ins e geração de cobranças são bloqueados; o job diário a ignora.

### 5.5 Regras de domínio
- **Plano:** nome, descrição, valor, periodicidade (MENSAL=1, TRIMESTRAL=3, SEMESTRAL=6, ANUAL=12 meses), status.
- **Matrícula:** um aluno tem no máximo **uma matrícula não cancelada por academia**. O valor é copiado do plano no momento da matrícula. Criar matrícula gera a primeira cobrança (**pagamento PENDENTE**) com vencimento = data de início, cobrindo o primeiro período.
- **Renovação por cobrança recorrente (aprovado):**
  - A matrícula permanece vigente até ser cancelada; não existe "clique de renovar" obrigatório.
  - **Uma cobrança por período** do plano (mensal, trimestral, semestral ou anual), sem parcelamento.
  - O job diário **gera a próxima cobrança antes do vencimento** (antecedência configurável por academia, padrão 7 dias), com vencimento = `data_vencimento` atual da matrícula.
  - **Registrar o pagamento de uma cobrança estende a vigência** por mais um período (`data_vencimento += periodicidade`) e grava o período em `matricula_periodo`. Isso é a "renovação" (evento `matricula.renovada`).
  - A ação manual "Renovar" no painel continua existindo como atalho: gera/antecipa a cobrança do próximo período.
- **Status da matrícula:**
  - PENDENTE: criada, primeira cobrança ainda não paga (check-in bloqueado).
  - ATIVA: em vigência. Cobrança atrasada há até 10 dias mantém ATIVA (aluno já aparece como inadimplente).
  - VENCIDA: **cobrança atrasada há mais de 10 dias** (carência configurável por academia, padrão 10) → check-in bloqueado. Pagar as cobranças em aberto volta a ATIVA, com a vigência estendida a partir do vencimento original (não de hoje).
  - SUSPENSA (aprovado): ação do admin, com motivo. Pausa a geração de cobranças e bloqueia check-in. Ao reativar, `data_vencimento` é **estendida pelo número de dias suspensos** e a cobrança pendente, se houver, tem o vencimento ajustado. Cobranças já ATRASADAS antes da suspensão continuam devidas.
  - CANCELADA (aprovado): ação do admin, com motivo; terminal. Cobranças PENDENTES são canceladas; **cobranças ATRASADAS são preservadas** como dívida (o aluno continua inadimplente até pagá-las).
- **Pagamentos:** admin registra o pagamento (data, forma: dinheiro, PIX, cartão de crédito, cartão de débito, transferência, observações) → PAGO. Cancelamento manual de cobrança por admin, com motivo. Pagamento é sempre do valor integral da cobrança (sem pagamento parcial no MVP).
- **Job diário** (Spring `@Scheduled`, fuso `America/Sao_Paulo`, com ShedLock), por academia ATIVA:
  1. gera cobranças dos próximos períodos dentro da antecedência;
  2. PENDENTE com vencimento < hoje → ATRASADO (`pagamento.atrasado`);
  3. matrícula ATIVA com cobrança atrasada há > carência → VENCIDA (`matricula.vencida`).
  - Idempotente (pode rodar mais de uma vez no dia sem efeitos duplicados).
- **Inadimplência é derivada**, não armazenada: aluno é inadimplente numa academia se tem ao menos um pagamento ATRASADO.
- **Status do aluno é independente do status da matrícula (aprovado):** o admin altera o status do aluno (ATIVO/INATIVO/CANCELADO) manualmente; mudar a matrícula não muda o aluno. Relatórios de "alunos ativos/inativos" contam o status do aluno; os de matrícula contam o status da matrícula. Check-in exige **aluno ATIVO e matrícula ATIVA**.
- **Agenda:** `horario` é o slot semanal recorrente (dia da semana, início, fim, professor, tipo de aula, status). `aula` é a ocorrência concreta de um horário numa data, criada sob demanda no primeiro check-in. Isso permite relatórios de frequência por horário e professor.
- **Check-in por QR Code:**
  - O portal do aluno exibe um QR com **token assinado (HMAC) de curta duração** (renovado a cada 30s), contendo aluno e emissão — screenshot não funciona depois de expirar.
  - A leitura é feita no **painel admin, em uma tela "Recepção"** que usa a câmera do dispositivo (biblioteca de leitura de QR no Angular).
  - Validação: academia ATIVA; token válido e não expirado; aluno ATIVO na academia ativa; matrícula ATIVA. Aluno inadimplente dentro da carência **passa, mas a tela de recepção exibe alerta**; após a carência a matrícula está VENCIDA e o check-in é bloqueado.
  - Associação à aula: o horário ativo do dia cuja janela vai de 30 min antes do início até o fim. Sem horário correspondente → check-in "livre" sem aula.
  - **Pontualidade:** PONTUAL se check-in ≤ início + tolerância (configurável por academia, padrão 10 min); senão ATRASADO.
  - Check-in duplicado na mesma aula é rejeitado.
- **Presença manual:** admin escolhe aluno e aula/data/hora; origem MANUAL.
- **Correção/exclusão de frequência:** exclusão lógica (status EXCLUIDA) e correção (status CORRIGIDA no registro antigo + novo registro), sempre com motivo e auditoria.

### 5.6 Eventos e Kafka
- **Transactional outbox:** eventos gravados na tabela `outbox_evento` na mesma transação; um publisher agendado envia ao Kafka e marca como publicado (entrega at-least-once; consumidores idempotentes por `evento_id`).
- Tópicos: `checkin.registrado`, `matricula.criada`, `matricula.renovada`, `matricula.vencida`, `matricula.suspensa`, `matricula.reativada`, `matricula.cancelada`, `pagamento.gerado`, `pagamento.registrado`, `pagamento.atrasado`.
- Payload JSON com envelope: `evento_id`, `tipo`, `academia_id`, `ocorrido_em`, `versao`, `dados`. Chave da mensagem = `academia_id`.
- **Módulo `notificacao`** consome os eventos e grava registros em `notificacao` (canal, destinatário, template, status PENDENTE) por meio de uma porta `CanalNotificacao` com implementação "log" no MVP — o ponto de extensão para WhatsApp na Fase 6.

### 5.7 Auditoria e observabilidade
- Tabela `auditoria` (usuário, academia, ação, entidade, id, antes/depois em JSONB, data) para operações críticas: pagamentos, cancelamento/suspensão de matrícula, correção/exclusão de frequência, mudanças de vínculo.
- Logs estruturados em JSON com `academia_id` e `trace_id`; Actuator health/metrics; endpoint Prometheus.

### 5.8 Frontend
- Angular (versão estável atual), standalone components, signals, roteamento lazy, **Angular Material** com tema próprio; responsivo (mobile-first no portal do aluno).
- Uma SPA com duas áreas: `/admin` (painel por módulos: Dashboard, Alunos, Professores, Planos, Matrículas, Pagamentos, Inadimplência, Agenda, Frequência, Recepção/Check-in, Relatórios, Configurações) e `/aluno` (Início, Meu plano, Pagamentos, Horários, QR Code, Frequência). Guards por perfil.
- Interceptor HTTP adiciona `Authorization` e `X-Academia-Id`, e faz refresh automático do token.
- Dashboard e relatórios com gráficos simples (indicadores da seção 14), filtros por período.

### 5.9 Testes
- Backend: JUnit 5 + AssertJ para domínio; testes de casos de uso com fakes; integração com **Testcontainers** (PostgreSQL e Kafka); `ApplicationModules.verify()` (Spring Modulith) + ArchUnit; testes de isolamento de tenant.
- Frontend: testes unitários com o runner padrão do Angular CLI; um conjunto pequeno de testes E2E com Playwright para os fluxos principais (login admin, matricular aluno, registrar pagamento, check-in).

### 5.10 Infra e CI/CD
- `docker-compose.yml`: postgres, kafka (modo KRaft, sem Zookeeper), backend, frontend (build servido via nginx, que também faz proxy de `/api`). Kafka UI em profile opcional `tools`.
- Dockerfiles multi-stage.
- Perfil `demo` com seed: 1–2 academias, admin, ~30 alunos, planos, matrículas em todos os estados, pagamentos atrasados, horários e histórico de frequência.
- GitHub Actions (`ci.yml`): jobs paralelos backend (build + testes) e frontend (lint + testes + build), e build das imagens Docker.

### 5.11 Documentação
- `README.md`: pitch, screenshots, diagrama de arquitetura, como rodar, credenciais de demo.
- `docs/01..09` conforme a especificação; `09-decisoes-tecnicas.md` com ADRs (monolito modular, RLS, outbox, QR assinado, inadimplência derivada, etc.).
- Convenção: **domínio, entidades, tabelas e endpoints em português** (coerente com a especificação e eventos como `checkin.registrado`); termos técnicos/infra em inglês. *(Decisão minha.)*
- Commits no estilo Conventional Commits.

## 6. Entrega em fases

Segue o roadmap da especificação; cada fase vira um plano de implementação próprio e termina com CI verde e o sistema rodando via compose.

1. **Fundação:** monorepo, backend esqueleto (módulos, Flyway, segurança base, multi-tenancy + RLS, outbox), frontend esqueleto (layouts, auth, seletor de academia), compose, CI, docs iniciais.
2. **Cadastros:** academias, administradores, alunos (com vínculo), professores, planos.
3. **Matrículas e pagamentos:** matrícula, renovação, cancelamento/suspensão, pagamentos, job diário, inadimplência, eventos.
4. **Frequência:** horários, aulas, QR assinado, tela de recepção, presença manual, correção/exclusão, histórico.
5. **Portal e relatórios:** portal do aluno completo, dashboard e relatórios, seed de demo, polimento do README.

## 7. Deixado para o construtor
- Versões exatas de bibliotecas, nomes de pacotes, estrutura de DTOs, layout visual detalhado, escolha da lib de QR e de gráficos.
- Paginação/ordenação/filtros padrão das listagens.
- Formato exato de logs e métricas customizadas.

## 8. Registro de decisões

**Confirmadas pelo usuário:**
- Abordagem A (monolito modular, schema compartilhado + RLS, Kafka via outbox).
- Renovação por cobrança recorrente; uma cobrança por período; carência de 10 dias até VENCIDA.
- Sem demo pública no MVP; apenas Docker Compose local.
- Aluno separado por academia, CPF único por academia; login global por e-mail.
- Administradores com o mesmo poder; academia inativa somente leitura.
- Suspensão estende o vencimento pelos dias suspensos; cancelamento preserva cobranças atrasadas.
- Status do aluno independente do status da matrícula.

**Decisões minhas (revisáveis):**
- QR rotativo assinado (30s), lido por tela "Recepção" no painel admin com câmera.
- Inadimplente dentro da carência pode fazer check-in, com alerta na recepção.
- Cobrança gerada 7 dias antes do vencimento (configurável); sem pagamento parcial.
- Mesma pessoa pode ser admin e aluno (perfis derivados dos vínculos).
- Fuso `America/Sao_Paulo` para regras de data.
- Domínio nomeado em português; Angular Material como biblioteca de UI.
