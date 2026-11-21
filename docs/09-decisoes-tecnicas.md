# 09 — Decisões técnicas (ADRs)

Formato: contexto → decisão → consequências. Status entre parênteses.

## ADR-001 — Monolito modular (aceita)
**Contexto:** MVP de um produto multi-tenant, desenvolvido por uma pessoa, que precisa rodar com um comando.
**Decisão:** um único Spring Boot dividido em módulos de negócio, com fronteiras verificadas pelo Spring Modulith e Clean Architecture dentro de cada módulo (regras em ArchUnit).
**Alternativas descartadas:** microsserviços (infraestrutura multiplicada, autenticação distribuída, consistência eventual em tudo); schema por academia (migrations N vezes, relatórios difíceis).
**Consequências:** deploy e testes simples; extrair um módulo para serviço próprio no futuro é viável porque os módulos já conversam por API pública e eventos.

## ADR-002 — Schema compartilhado com `academia_id` + Row-Level Security (aceita, Fase 2)
**Decisão:** isolamento em duas camadas: filtro automático na aplicação e RLS no PostgreSQL, com a academia ativa definida por transação (`SET LOCAL`).
**Consequências:** um bug de filtro na aplicação não vaza dados (o banco barra); exige usuário de banco sem `BYPASSRLS` e cuidado com jobs que atravessam academias.

## ADR-003 — Transactional outbox para eventos (aceita, implementada)
**Contexto:** publicar no Kafka dentro de uma transação de banco não é atômico (dual write).
**Decisão:** eventos são gravados em `outbox_evento` na mesma transação; um relay publica em ordem de `sequencia`, interrompe o lote na primeira falha, tenta de novo no ciclo seguinte e usa advisory lock para ter um único publicador ativo.
**Alternativa descartada:** externalização de eventos do Spring Modulith — menos controle sobre envelope, ordem e retry.
**Consequências:** at-least-once (consumidores idempotentes por `evento_id`); latência de até ~1 s; um evento "envenenado" bloqueia os posteriores até ser resolvido — aceitável e visível por `tentativas`/`ultimo_erro`.

## ADR-004 — Kafka fora do readiness (aceita, implementada)
**Decisão:** liveness verifica só o processo; readiness verifica o banco, não o Kafka. O frontend não depende do Kafka.
**Consequências:** uma queda do Kafka não tira o sistema do ar; eventos acumulam no outbox e são publicados quando o Kafka volta. O monitoramento do atraso do outbox (pendentes antigos) passa a ser o sinal de problema no Kafka.

## ADR-005 — Aluno por academia com login global (aceita, Fase 2)
**Decisão:** `usuario` (e-mail + senha) é global; cada academia tem seu próprio registro de aluno, com CPF único por academia. Perfis derivam dos vínculos; a mesma pessoa pode ser admin e aluno.
**Consequências:** isolamento total dos dados cadastrais; um admin nunca descobre que um e-mail ou CPF existe em outra academia; dados da pessoa podem divergir entre academias (aceito).

## ADR-006 — Cobrança recorrente (aceita, Fase 3)
**Decisão:** a matrícula vale até ser cancelada; o job diário gera a cobrança do próximo período 7 dias antes do vencimento; pagar estende a vigência; após 10 dias de atraso a matrícula fica VENCIDA e o check-in é bloqueado. Uma cobrança por período, sem parcelamento nem pagamento parcial.
**Consequências:** o admin não precisa "renovar" ninguém manualmente; inadimplência surge automaticamente.

## ADR-007 — QR Code rotativo assinado (aceita, Fase 4)
**Decisão:** o QR do aluno carrega um token HMAC que expira e é renovado a cada 30 s; a leitura acontece na tela "Recepção" do painel admin.
**Consequências:** captura de tela não serve para check-in de terceiros; a câmera do navegador exige HTTPS fora de `localhost` (relevante para a futura demo pública).

## ADR-008 — Inadimplência derivada (aceita, Fase 3)
**Decisão:** inadimplente = possui pagamento ATRASADO; calculado por consulta, nunca armazenado.
**Consequências:** sem flag para dessincronizar; consultas de relatório precisam de índice em `(academia_id, status)` de pagamento.

## ADR-009 — Domínio em português (aceita)
**Decisão:** entidades, tabelas, colunas, endpoints e eventos em português sem acentos nos identificadores; termos de framework em inglês.
**Consequências:** linguagem ubíqua alinhada ao negócio e à especificação.

## ADR-010 — Versões da stack (aceita, Fase 1)
Spring Boot 4.2, Spring Modulith 2.2, Java 21, PostgreSQL 17, Apache Kafka 4.3 (KRaft, sem ZooKeeper), Angular 22 com Vitest, Node 24. `KafkaTemplate<String, String>` explícito para o outbox (o padrão do Boot 4 não é tipado). nginx usa o DNS do Docker com re-resolução para tolerar reinícios do backend.
