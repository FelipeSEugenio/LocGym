# 08 — Roadmap

| Fase | Conteúdo | Status |
|---|---|---|
| **1 — Fundação** | Monorepo; backend Spring Boot (health checks, Flyway, outbox → Kafka, regras de arquitetura, Problem Details); frontend Angular (esqueleto admin/aluno); Docker Compose; GitHub Actions; documentação inicial. | ✅ Concluída |
| **2 — Identidade e cadastros** | Autenticação (JWT + refresh), vínculos e perfis, multi-tenancy com RLS, auditoria; academias, administradores, alunos, professores, planos; login e seletor de academia no frontend. | Próxima |
| **3 — Matrículas e pagamentos** | Matrícula, cobrança recorrente, renovação, suspensão, cancelamento, registro de pagamentos, job diário, inadimplência e eventos. | Planejada |
| **4 — Frequência** | Horários, aulas, QR Code rotativo assinado, tela de recepção, presença manual, correção/exclusão, histórico. | Planejada |
| **5 — Portal e relatórios** | Portal do aluno completo, dashboard e relatórios, dados de demonstração, polimento do README. | Planejada |
| **6 — Evolução** | Notificações e WhatsApp, gateway de pagamento, relatórios avançados, app mobile, **demo pública online** (deploy, HTTPS, Kafka gerenciado, reset de dados de demo). | Futuro |

Cada fase tem seu próprio plano em `docs/superpowers/plans/` e termina com CI verde e o sistema rodando via `docker compose up`.
