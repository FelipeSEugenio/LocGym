# 08 — Roadmap

| Fase | Conteúdo | Status |
|---|---|---|
| **1 — Fundação** | Monorepo; backend Spring Boot (health checks, Flyway, outbox → Kafka, regras de arquitetura, Problem Details); frontend Angular (esqueleto admin/aluno); Docker Compose; GitHub Actions; documentação inicial. | ✅ Concluída |
| **2 — Identidade e cadastros** | Autenticação (JWT + refresh rotativo), vínculos e perfis, multi-tenancy com `@TenantId` + RLS, auditoria; academias, administradores, alunos, professores, planos; login, troca de senha, seletor de academia e painel no frontend. | ✅ Concluída |
| **3 — Matrículas e pagamentos** | Matrícula, cobrança recorrente, renovação, suspensão, cancelamento, registro de pagamentos, job diário, inadimplência e eventos. | Próxima |
| **4 — Frequência** | Horários, aulas, QR Code rotativo assinado, tela de recepção, presença manual, correção/exclusão, histórico. | Planejada |
| **5 — Portal e relatórios** | Portal do aluno completo, dashboard e relatórios, dados de demonstração, polimento do README. | Planejada |
| **6 — Evolução** | Notificações e WhatsApp, gateway de pagamento, relatórios avançados, app mobile, **demo pública online** (deploy, HTTPS, Kafka gerenciado, reset de dados de demo). | Futuro |

## Ideias candidatas para a Fase 3

Surgidas durante o planejamento da Fase 2 e ainda **não aprovadas** — a avaliar no design da Fase 3, sem abrir mão do multi-tenancy:

- Autocadastro do aluno com confirmação de e-mail (exige envio de e-mail e um link de convite por academia) e recuperação de senha por e-mail.
- Botão "Notificar administrador" para o aluno avisar que pagou por fora do sistema.
- E-mail da academia configurável no painel, para receber essas notificações.
- Prazo curto para a primeira cobrança, com cancelamento automático da matrícula não paga.

Cada fase tem seu próprio plano em `docs/planejamento/` (`fase-N-plano.md`) e termina com CI verde e o sistema rodando via `docker compose up`.
