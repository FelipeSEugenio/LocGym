# 01 — Visão geral

## O que é

O **LocGym** é um sistema web de gestão para academias de Jiu-Jitsu, **multi-tenant desde o início**: várias academias usam a mesma plataforma, cada uma com seus próprios alunos, professores, planos, matrículas, pagamentos, horários, aulas e check-ins, totalmente isolados entre si.

## Para quem

| Perfil | Usa o sistema para |
|---|---|
| **Administrador** | Gerir uma ou mais academias: cadastros, matrículas, cobranças, inadimplência, horários, presença e relatórios. |
| **Aluno** | Consultar matrícula, plano, vencimento, pagamentos, horários e frequência, e fazer check-in por QR Code. |

## Escopo do MVP

- Cadastro e autenticação de administradores e alunos; cadastro de academias e professores.
- Planos por academia; matrícula com cobrança recorrente, suspensão e cancelamento.
- Registro manual de pagamentos e controle automático de inadimplência.
- Horários e aulas; check-in por QR Code rotativo e presença manual.
- Painel administrativo, portal do aluno (responsivo) e relatórios operacionais.
- Arquitetura preparada para notificações (WhatsApp em fase posterior).

## Fora do MVP

App mobile nativo, gateway de pagamento, nota fiscal, financeiro completo, estoque, loja, catraca, redes sociais, marketing automatizado, app de professor, recuperação de senha por e-mail, pagamento parcial, parcelamento de planos não mensais e demo pública online (deploy/hospedagem ficam para fase posterior).

## Estado atual

**Fase 1 — Fundação concluída:** monorepo, backend Spring Boot com health checks, migrations e publicação de eventos (outbox → Kafka), frontend Angular com o esqueleto das áreas admin/aluno, Docker Compose e CI. Os módulos de negócio começam na Fase 2 — veja [08 — Roadmap](08-roadmap.md).

## Documentos

1. [Visão geral](01-visao-geral.md)
2. [Requisitos funcionais](02-requisitos-funcionais.md)
3. [Requisitos não funcionais](03-requisitos-nao-funcionais.md)
4. [Modelo de dados](04-modelo-de-dados.md)
5. [Arquitetura](05-arquitetura.md)
6. [API](06-api.md)
7. [Eventos](07-eventos.md)
8. [Roadmap](08-roadmap.md)
9. [Decisões técnicas (ADRs)](09-decisoes-tecnicas.md)

Fontes: [especificação original](superpowers/specs/2026-11-21-locgym-especificacao.md) e [design aprovado](superpowers/specs/2026-11-21-locgym-design.md).
