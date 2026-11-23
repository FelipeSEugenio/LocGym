# 09 — Decisões técnicas (ADRs)

Formato: contexto → decisão → consequências. Status entre parênteses.

## ADR-001 — Monolito modular (aceita)
**Contexto:** MVP de um produto multi-tenant, desenvolvido por uma pessoa, que precisa rodar com um comando.
**Decisão:** um único Spring Boot dividido em módulos de negócio, com fronteiras verificadas pelo Spring Modulith e Clean Architecture dentro de cada módulo (regras em ArchUnit).
**Alternativas descartadas:** microsserviços (infraestrutura multiplicada, autenticação distribuída, consistência eventual em tudo); schema por academia (migrations N vezes, relatórios difíceis).
**Consequências:** deploy e testes simples; extrair um módulo para serviço próprio no futuro é viável porque os módulos já conversam por API pública e eventos.

## ADR-002 — Schema compartilhado com `academia_id` + Row-Level Security (aceita, implementada na Fase 2)
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

## ADR-005 — Aluno por academia com login global (aceita, implementada na Fase 2)
**Decisão:** `usuario` (e-mail + senha) é global; cada academia tem seu próprio registro de aluno, com CPF único por academia. Perfis derivam dos vínculos; a mesma pessoa pode ser admin e aluno.
**Consequências:** isolamento total dos dados cadastrais; um admin nunca descobre que um e-mail ou CPF existe em outra academia; dados da pessoa podem divergir entre academias (aceito).

## ADR-006 — Cobrança recorrente (aceita, implementada na Fase 3)
**Decisão:** a matrícula vale até ser cancelada; o job diário gera a cobrança do próximo período 7 dias antes do vencimento; pagar estende a vigência; após 10 dias de atraso a matrícula fica VENCIDA e o check-in é bloqueado. Uma cobrança por período, sem parcelamento nem pagamento parcial.
**Consequências:** o admin não precisa "renovar" ninguém manualmente; inadimplência surge automaticamente.

## ADR-007 — QR Code rotativo assinado (aceita, Fase 4)
**Decisão:** o QR do aluno carrega um token HMAC que expira e é renovado a cada 30 s; a leitura acontece na tela "Recepção" do painel admin.
**Consequências:** captura de tela não serve para check-in de terceiros; a câmera do navegador exige HTTPS fora de `localhost` (relevante para a futura demo pública).

## ADR-008 — Inadimplência derivada (aceita, implementada na Fase 3)
**Decisão:** inadimplente = possui pagamento ATRASADO; calculado por consulta, nunca armazenado.
**Consequências:** sem flag para dessincronizar; consultas de relatório precisam de índice em `(academia_id, status)` de pagamento.

## ADR-009 — Domínio em português (aceita)
**Decisão:** entidades, tabelas, colunas, endpoints e eventos em português sem acentos nos identificadores; termos de framework em inglês.
**Consequências:** linguagem ubíqua alinhada ao negócio e à especificação.

## ADR-010 — Versões da stack (aceita, Fase 1)
Spring Boot 4.2, Spring Modulith 2.2, Java 21, PostgreSQL 17, Apache Kafka 4.3 (KRaft, sem ZooKeeper), Angular 22 com Vitest, Node 24. `KafkaTemplate<String, String>` explícito para o outbox (o padrão do Boot 4 não é tipado). nginx usa o DNS do Docker com re-resolução para tolerar reinícios do backend.

## ADR-011 — JWT RSA + refresh rotativo em cookie HttpOnly (aceita, Fase 2)
**Decisão:** access token JWT RS256 de 15 min, emitido e validado pelo suporte nativo do Spring Security (sem biblioteca externa), guardado só em memória no navegador. Refresh token aleatório de 256 bits, 7 dias, guardado como SHA-256, entregue em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`, rotacionado a cada uso; reuso de um token já usado revoga a sessão inteira, exceto numa janela de 10 s (corrida entre abas), e o frontend serializa o refresh entre abas com Web Locks.
**Consequências:** um XSS não consegue roubar a sessão longa; roubo do refresh é detectado; a chave RSA vem de `JWT_CHAVE_PRIVADA` (em dev, gerada na subida — sessões não sobrevivem a reinício).

## ADR-012 — Tenancy com `@TenantId` + RLS + papel `locgym_app` (aceita, Fase 2)
**Decisão:** entidades de tenant estendem `EntidadeDeTenant` (`@TenantId`); o gerenciador de transação executa `set_config('app.academia_id', …, true)` no início de cada transação; as tabelas de tenant têm RLS forçado; a aplicação conecta como `locgym_app` (sem `BYPASSRLS`, não dona) e as migrations como dono. Os testes de integração usam a mesma separação.
**Alternativas descartadas:** só RLS (erros difíceis de diagnosticar, `academia_id` à mão); `academiaId` explícito nos repositórios (fácil de esquecer).
**Consequências:** esquecer o filtro é impossível na aplicação e inofensivo no banco; operações que atravessam academias precisam de portas explícitas (ADR-013).

## ADR-013 — Função `SECURITY DEFINER` para vínculos (aceita, Fase 2)
**Decisão:** `vinculos_do_usuario(usuario)` é a única leitura que atravessa academias; executa como dono, devolve só os vínculos do próprio usuário e exclui aluno CANCELADO. É consultada a cada requisição para validar o contexto.
**Consequências:** remoção de admin e cancelamento de aluno valem na hora, ao custo de uma consulta indexada por requisição.

## ADR-014 — Contexto como par (academia, perfil) em headers (aceita, Fase 2)
**Decisão:** `X-Academia-Id` + `X-Perfil` em toda rota de tenant, porque a mesma pessoa pode ser ADMIN e ALUNO na mesma academia. O token não carrega vínculos.
**Consequências:** o frontend persiste o par escolhido; trocar de academia não exige novo login.

## ADR-015 — Rotina diária multi-tenant sem contornar o RLS (aceita, Fase 3)
**Contexto:** a rotina diária roda sem usuário logado, e a aplicação conecta como `locgym_app`, que não ignora RLS.
**Decisão:** percorrer as academias ativas (tabela global) e processar cada uma numa transação própria, com o contexto daquela academia (`Perfil.SISTEMA`). ShedLock garante uma instância por vez; a rotina é idempotente.
**Alternativas descartadas:** papel de banco com `BYPASSRLS` só para o job (abre caminho de vazamento); atualizações em massa por função SQL (regra de negócio fora do domínio e dos testes).
**Consequências:** um pouco mais lenta (irrelevante no volume); falha numa academia não afeta as outras; os mesmos casos de uso e eventos das requisições.

## ADR-016 — Quitação síncrona na mesma transação (aceita, Fase 3)
**Decisão:** o pagamento publica um evento Spring síncrono (`CobrancaQuitada`) que o módulo `matricula` trata antes do commit; Kafka só para fora.
**Alternativa descartada:** `matricula` consumir `pagamento.registrado` do Kafka — vigência atrasada e, com falha do consumidor, aluno pago aparecendo vencido.
**Consequências:** consistência imediata; dependência de módulos `matricula → pagamento` sem ciclo.

## ADR-017 — Calendário com dia-âncora (aceita, Fase 3)
**Decisão:** cada período termina `meses` depois do início, no dia do mês da âncora (`data_base`), limitado ao fim do mês — nunca somando mês sobre mês.
**Consequências:** 31/01 → 28/02 → 31/03, sem "encolher" para dia 28.

## ADR-018 — Suspensão "para o tempo" (aceita, Fase 3)
**Contexto:** o design previa deslocar âncora e vigência na reativação; com cobrança atrasada ou vigência encerrada antes da suspensão, isso quebrava a sequência dos períodos e a quitação seguinte falhava.
**Decisão:** na reativação, toda data **posterior** ao início da suspensão (vigência, inícios/fins de período e de cobrança) anda os dias suspensos; as anteriores ficam. O vencimento de cobrança pendente anda se vencer no dia da suspensão ou depois. As cobranças são deslocadas da mais tardia para a mais cedo (o índice único de período não é deferrable). A nova âncora é o primeiro limite de período após a suspensão, deslocado.
**Consequências:** uma regra única para todos os casos; a dívida anterior à suspensão continua vencida.

## ADR-019 — Abono estende a vigência e recuperação de períodos parados (aceita, Fase 3)
**Decisão:** abonar (cancelar com motivo) uma cobrança concede o período sem contar como recebido. Quando a quitação deixa a vigência no passado, o sistema gera na hora as cobranças de todos os períodos que faltam; o admin cobra ou abona em lote. A volta a ATIVA exige nenhuma atrasada **e** vigência cobrindo hoje.
**Consequências:** mantém a regra aprovada de "vigência a partir do vencimento original", com decisão caso a caso da academia.

## ADR-020 — Data de negócio em America/Sao_Paulo (aceita, Fase 3)
**Decisão:** `Calendario.hoje()` converte o relógio do sistema para São Paulo; regras de vencimento nunca usam a data UTC nem `LocalDate.now()`. Nos testes, `CalendarioDeTeste` controla só a data de negócio — JWT, outbox e auditoria seguem no relógio real.
**Consequências:** nada vence "um dia antes" entre 21h e meia-noite; testes de datas não quebram tokens.
