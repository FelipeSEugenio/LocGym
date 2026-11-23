# LocGym — Fase 3 (Matrículas e pagamentos) — Design

Data: 2026-11-23
Status: aprovado pelo usuário em 2026-11-23
Base: [design geral aprovado](design.md) (§5.5 regras de domínio, §5.6 eventos), [design da Fase 2](fase-2-design.md) (tenancy, auditoria, padrões de API) e [especificação](especificacao.md) (§8, §10, §20).

## 1. Intenção

Dar ao LocGym o **ciclo financeiro do aluno**: matricular num plano, cobrar de forma recorrente, registrar pagamentos manuais, identificar inadimplência automaticamente e controlar vencimento, suspensão e cancelamento — tudo isolado por academia e publicando eventos no Kafka.

Para o portfólio, o destaque é uma **máquina de estados de domínio bem testada** (matrícula × cobrança, com datas, carência e suspensão) e um **job diário multi-tenant que respeita o RLS** em vez de contorná-lo.

## 2. Objetivos, não-objetivos, anti-objetivos

**Objetivos**
- Matrícula de aluno em plano; renovação por cobrança recorrente; suspensão/reativação; cancelamento.
- Cobranças (pagamentos) geradas automaticamente; registro manual de pagamento; abono (cancelamento) de cobrança.
- Job diário: gerar cobranças, marcar atrasos, vencer matrículas.
- Inadimplência derivada e consultável.
- Eventos Kafka `matricula.*` e `pagamento.*` via outbox.
- Painel admin: Matrículas, Cobranças, Inadimplência; configuração de antecedência e carência na tela da Academia.

**Não-objetivos (ficam para depois)**
- Portal do aluno com matrícula e pagamentos (Fase 5); relatórios e dashboard (Fase 5).
- Consumidores dos eventos / notificações (Fase 6); check-in (Fase 4) — a regra "check-in exige matrícula ATIVA" é aplicada na Fase 4.
- Gateway de pagamento, pagamento parcial, parcelamento de planos não mensais (MVP).
- Estorno de pagamento registrado, troca de plano de uma matrícula existente (troca = cancelar + nova matrícula).
- As ideias candidatas do roadmap (autocadastro, "notificar administrador", e-mail da academia, prazo curto da primeira cobrança) — **rejeitadas pelo usuário para esta fase**.

**Anti-objetivos**
- Job ou consulta que leia/altere dados de uma academia no contexto de outra, ou que use papel de banco que ignore RLS.
- Aluno que pagou aparecer com matrícula vencida (vigência inconsistente com pagamentos).
- Cobrança duplicada para o mesmo período (job rodando duas vezes, duas instâncias, clique duplo).
- Datas calculadas em UTC em vez de `America/Sao_Paulo` (cobrança vencendo "um dia antes").

## 3. Abordagens consideradas

**Job diário × isolamento**

| Abordagem | Trade-off | Decisão |
|---|---|---|
| **A) Percorrer as academias ativas, uma transação por academia com `TenantContext` daquela academia** | RLS continua valendo; mesmo código de domínio; eventos pelo outbox; falha numa academia não para as outras; um pouco mais lento (irrelevante no volume) | **Escolhida pelo usuário** |
| B) Papel de banco que ignora RLS só para o job | Mais simples, mas abre caminho de vazamento — viola o anti-objetivo nº 1 | Descartada |
| C) Atualizações em massa por funções SQL | Mais rápido; lógica de negócio fora do domínio e dos testes; eventos difíceis | Descartada |

**Pagamento → vigência da matrícula**

| Abordagem | Trade-off | Decisão |
|---|---|---|
| **A) Na mesma transação: `pagamento` publica evento interno (Spring, síncrono); `matricula` estende a vigência antes do commit** | Consistência imediata; Kafka só para fora | **Escolhida pelo usuário** |
| B) Via Kafka: `matricula` consome `pagamento.registrado` | Demonstra Kafka ponta a ponta, mas vigência atrasada e falha do consumidor deixa aluno pago vencido | Descartada |

## 4. Módulos e dependências

- Novo módulo **`matricula`** (dono da matrícula, dos períodos e do job diário) e novo módulo **`pagamento`** (dono das cobranças).
- Direção de dependência (verificada pelo Spring Modulith): `matricula → pagamento` (chama a API pública `Cobrancas` para gerar, marcar atrasos e cancelar cobranças). `pagamento` **não** depende de `matricula`: ao registrar/abonar uma cobrança publica o evento interno `CobrancaQuitada` (record público do módulo `pagamento`), tratado por um `@EventListener` **síncrono** (não `@TransactionalEventListener`) em `matricula`, na mesma transação. Exceção no listener desfaz o registro do pagamento.
- `matricula` também consulta `aluno` e `plano` pelas APIs públicas desses módulos (status e dados do plano).
- O job roda num novo perfil técnico **`Perfil.SISTEMA`** no `ContextoTenant` (`usuarioId = null`; auditoria grava `usuario_id` nulo). *(Decisão minha.)*

## 5. Modelo de dados (migrations V5+)

**Alteração em `academia`** (global): `dias_antecedencia_cobranca int not null default 7 check (between 1 and 30)`, `dias_carencia int not null default 10 check (between 0 and 60)`. Editáveis pelo admin na tela da Academia (auditado).

**Tabelas de tenant** (com `academia_id`, `@TenantId`, RLS + `FORCE`, papel `locgym_app`, como na Fase 2):

- `matricula` — id, academia_id, aluno_id (FK), plano_id (FK), `valor` numeric(10,2) > 0 e `periodicidade` (copiados do plano na criação), `data_inicio` date, `data_base` date (âncora do calendário, ver §6.2), `vigente_ate` date (primeiro dia **não** coberto; = data de vencimento exibida), `status` (PENDENTE/ATIVA/VENCIDA/SUSPENSA/CANCELADA), `suspensa_desde` date null, `motivo` varchar(300) null (da última suspensão/cancelamento), `version` (locking otimista), criado_em, atualizado_em.
  Índice único parcial: `(academia_id, aluno_id) where status <> 'CANCELADA'` — **no máximo uma matrícula não cancelada por aluno por academia**.
- `matricula_periodo` — id, academia_id, matricula_id, `inicio`, `fim` (exclusivo), `cobranca_id`, criado_em. Um registro por período coberto (histórico da renovação).
- `pagamento` (= cobrança) — id, academia_id, matricula_id, aluno_id, `valor`, `vencimento` date, `periodo_inicio`, `periodo_fim` (exclusivo), `status` (PENDENTE/PAGO/ATRASADO/CANCELADO), `data_pagamento` date null, `forma` null (DINHEIRO/PIX/CARTAO_CREDITO/CARTAO_DEBITO/TRANSFERENCIA), `observacoes` varchar(500) null, `motivo_cancelamento` varchar(300) null, `version`, criado_em, atualizado_em.
  Único: `(matricula_id, periodo_inicio)` — **uma cobrança por período, para sempre** (inclusive canceladas), o que torna a geração idempotente.
  Índices: `(academia_id, status, vencimento)`, `(academia_id, aluno_id)`.
- `shedlock` (global, técnica, sem RLS) — tabela padrão do ShedLock (provider JDBC).

CHECKs para todos os enums; `data_pagamento` obrigatória sse `status = 'PAGO'`; `motivo_cancelamento` obrigatório sse `CANCELADO`.

## 6. Regras de domínio

### 6.1 Matrícula — criação
- `POST /api/v1/matriculas {alunoId, planoId, dataInicio, valor?}`. Exige aluno **ATIVO**, plano **ATIVO**, e nenhuma matrícula não cancelada do aluno (409 "Aluno já possui matrícula").
- `dataInicio` entre hoje − 30 e hoje + 30 dias (fuso da academia). *(Decisão minha: permite registrar alunos antigos e matrículas agendadas sem abrir para datas arbitrárias.)*
- **Início futuro:** se a primeira cobrança for paga antes de `dataInicio`, a matrícula fica ATIVA, mas a vigência só começa em `dataInicio`. A Fase 4 exigirá, para check-in, matrícula ATIVA **e** `data_inicio ≤ hoje`; a tela mostra "Começa em dd/mm". *(Decisão minha.)*
- `valor` opcional: padrão = valor do plano; o admin pode informar outro (desconto) **só na criação**. Mudança posterior do preço do plano não afeta matrículas existentes.
- Cria: matrícula **PENDENTE**, `data_base = vigente_ate = dataInicio`, e a **primeira cobrança** PENDENTE com `vencimento = dataInicio`, período `[dataInicio, dataInicio + 1 periodicidade)`. Eventos `matricula.criada` e `pagamento.gerado`. Auditado.

### 6.2 Calendário (sem "deriva" de mês)
- Meses por periodicidade: MENSAL 1, TRIMESTRAL 3, SEMESTRAL 6, ANUAL 12.
- O n-ésimo limite de período é `data_base.plusMonths(n × meses)` — sempre calculado a partir da âncora, nunca somando mês sobre mês. Ex.: início 31/01 mensal → 28/02 (ou 29), **31/03**, 30/04, 31/05…
- Suspensão desloca a âncora (`data_base += dias suspensos`), ver §6.5.

### 6.3 Cobrança recorrente e vigência
- **Pagar** uma cobrança (`registrar pagamento`) ou **abonar** (cancelar com motivo, §6.6) dispara `CobrancaQuitada` → `matricula`:
  - cria `matricula_periodo` para o período da cobrança;
  - `vigente_ate = periodo_fim` da cobrança;
  - PENDENTE ou VENCIDA → **ATIVA** somente se não restar nenhuma cobrança ATRASADA da matrícula **e** `vigente_ate > hoje` (a vigência cobre o dia de hoje); senão o status não muda;
  - evento `matricula.renovada` (exceto na primeira cobrança, que emite só `pagamento.registrado` + a transição para ATIVA — *decisão minha*);
  - **recuperação (aprovado pelo usuário — opção A):** gera **na hora** todas as cobranças que faltam, uma por período, de `vigente_ate` até o período cujo `inicio − antecedência ≤ hoje` (mesma regra do job, ver §6.4). Cobranças com vencimento já passado nascem **ATRASADO** (`pagamento.gerado` + `pagamento.atrasado`). Ex.: vencida desde 10/08, paga a de 10/08 em 15/11 → nascem as de 10/09 e 10/10 (ATRASADO) e 10/11 (ATRASADO); a matrícula só volta a ATIVA quando todas forem pagas ou abonadas.
- **Abonar meses parados em lote** (`POST /matriculas/{id}/abonar {ateCobrancaId, motivo}`): abona, em ordem, todas as cobranças em aberto da matrícula da mais antiga até a indicada (inclusive), cada uma como no §6.6; um único registro de auditoria. Permite ao admin cobrar só o mês atual de quem voltou. *(Opção A aprovada; formato do endpoint — decisão minha.)*
- **Ordem obrigatória:** só a cobrança em aberto (PENDENTE/ATRASADO) **mais antiga** da matrícula pode ser paga ou abonada (409 "Quite primeiro a cobrança de dd/mm/aaaa"). Garante que a vigência avança sem buracos. *(Decisão minha.)*
- Valor pago = valor da cobrança (sem pagamento parcial). `dataPagamento ≤ hoje`.
- **Renovar manualmente** (`POST /matriculas/{id}/renovar`): gera já a cobrança do próximo período (vencimento = `vigente_ate`), mesmo fora da janela de antecedência. Só para ATIVA; se já existir cobrança em aberto → 409.

- **Matrícula CANCELADA:** pagar ou abonar uma cobrança ATRASADA preservada só quita a dívida — o listener **não** altera a matrícula (sem período, sem `vigente_ate`, sem status, sem evento `matricula.*`); sai apenas `pagamento.registrado`/`pagamento.cancelado`. *(Decisão minha.)*

### 6.4 Job diário (por academia ATIVA, em transação própria, `Perfil.SISTEMA`)
Execução: `@Scheduled(cron = "0 5 0 * * *", zone = "America/Sao_Paulo")` com **ShedLock** (uma instância por vez). Idempotente: rodar duas vezes no mesmo dia não muda nada na segunda. `hoje` vem de um `Clock` injetado (fuso `America/Sao_Paulo`). Passos, nesta ordem:
1. **Gerar cobranças:** para matrícula **ATIVA**, gera — em ordem e sem pular períodos — as cobranças de cada período ainda sem cobrança cujo `inicio − dias_antecedencia_cobranca ≤ hoje`, começando em `vigente_ate` (vencimento = início do período; `pagamento.gerado`). Normalmente é uma só; após longa inatividade da academia, recupera todos os períodos que faltam (mesma regra do §6.3). Matrículas PENDENTE, VENCIDA, SUSPENSA e CANCELADA **não** geram novas cobranças (a dívida não cresce enquanto a matrícula está vencida — *decisão minha*).
2. **Marcar atrasos:** cobrança PENDENTE com `vencimento < hoje`, de matrícula **não SUSPENSA** → ATRASADO (`pagamento.atrasado`).
3. **Vencer matrículas:** matrícula **ATIVA** com cobrança ATRASADA cujo `vencimento + dias_carencia < hoje` → VENCIDA (`matricula.vencida`). Matrícula PENDENTE cuja primeira cobrança atrasou continua PENDENTE (nunca esteve ativa — *decisão minha*).
- Falha numa academia: log de erro + métrica `locgym.rotina_diaria.falhas{academia}`; segue para a próxima.
- Academias INATIVAS são ignoradas (regra aprovada: somente leitura).
- Perfil `dev`: `POST /api/v1/dev/rotina-diaria` executa o job **na data real** (sem simular datas — uma data simulada só no job contradiria as demais regras, que usam a data real). Para demonstrar históricos, a Fase 5 trará dados de demonstração criados no passado. Não existe fora do perfil `dev`. *(Decisão minha.)*
- O `Clock` de negócio é um bean próprio com fuso `America/Sao_Paulo`, separado do `Clock` UTC técnico já usado por JWT/outbox/auditoria; os testes o substituem por um relógio fixo/ajustável.

### 6.5 Suspensão e reativação (regra aprovada)
- `POST /matriculas/{id}/suspender {motivo}` — só de **ATIVA**. `suspensa_desde = hoje`. Evento `matricula.suspensa`. Auditado.
- Durante a suspensão: não gera cobranças, não marca atrasos, não vence. Cobranças já ATRASADAS antes da suspensão continuam devidas (aluno segue inadimplente).
- Cobranças de matrícula SUSPENSA **podem** ser pagas ou abonadas (a dívida pode ser quitada a qualquer momento): a vigência avança normalmente, mas o status continua SUSPENSA e nenhuma cobrança nova é gerada. *(Decisão minha.)*
- `POST /matriculas/{id}/reativar` — `dias = hoje − suspensa_desde`; `data_base += dias`; `vigente_ate += dias`; cobrança PENDENTE da matrícula, se houver, tem `vencimento`, `periodo_inicio` e `periodo_fim` deslocados em `dias`; registros de `matricula_periodo` com `inicio ≥ suspensa_desde` também são deslocados, para o histórico não ter buraco. Volta a **ATIVA**; se já houver cobrança atrasada além da carência, o job a levará a VENCIDA. Evento `matricula.reativada`. Auditado.

### 6.6 Cancelamentos
- **Cancelar matrícula** `POST /matriculas/{id}/cancelar {motivo}` — de qualquer status exceto CANCELADA; terminal. Cobranças **PENDENTES** → CANCELADO (motivo "Matrícula cancelada", sem estender vigência); **ATRASADAS preservadas** como dívida (regra aprovada). Evento `matricula.cancelada`. Auditado. O aluno pode então receber nova matrícula.
- **Abonar (cancelar) cobrança** `POST /cobrancas/{id}/cancelar {motivo}` — PENDENTE ou ATRASADO → CANCELADO; o período é considerado **concedido**: estende a vigência como um pagamento (§6.3), mas **não conta como recebido** nos totais. Mesma regra de ordem (mais antiga primeiro). Evento `pagamento.cancelado`. Auditado. *(Decisão minha: sem isso, uma cobrança cancelada deixaria a matrícula sem caminho para a próxima cobrança.)*

### 6.7 Inadimplência
- Derivada: aluno inadimplente na academia ⇔ existe cobrança **ATRASADO** dele (qualquer matrícula, inclusive cancelada).
- `GET /api/v1/inadimplencia?busca=&page=&size=` → por aluno: nome, CPF, total em atraso, quantidade de cobranças atrasadas, vencimento mais antigo, dias de atraso, status da matrícula atual. Ordenado por dias de atraso desc.

### 6.8 Status do aluno
Independente da matrícula (regra aprovada): nenhuma mudança de matrícula altera o aluno, e vice-versa. Única dependência: criar matrícula exige aluno ATIVO.

## 7. API (tenant, ADMIN; Problem Details; 404 para recurso de outra academia; 409 para regra violada)

- `GET /api/v1/matriculas?status=&busca=&page=&size=` · `POST /api/v1/matriculas` · `GET /api/v1/matriculas/{id}` (com períodos e cobranças) · `POST .../{id}/renovar|suspender|reativar|cancelar|abonar`.
- `GET /api/v1/alunos/{id}/matricula` — matrícula não cancelada do aluno (ou 404), para a ficha do aluno.
- `GET /api/v1/cobrancas?status=&vencimentoDe=&vencimentoAte=&alunoId=&page=&size=` · `POST /api/v1/cobrancas/{id}/pagamento {dataPagamento, forma, observacoes?}` · `POST /api/v1/cobrancas/{id}/cancelar {motivo}`.
- `GET /api/v1/inadimplencia`.
- `PUT /api/v1/academia` passa a aceitar `diasAntecedenciaCobranca` e `diasCarencia`.
- Concorrência: `version` em matrícula e cobrança; conflito de atualização simultânea → 409 "Registro alterado por outra operação, recarregue".
- Academia INATIVA: todas as escritas acima → 409 (regra da Fase 2); o job a ignora.

## 8. Eventos Kafka (via outbox, envelope da Fase 1)

`matricula.criada`, `matricula.renovada`, `matricula.vencida`, `matricula.suspensa`, `matricula.reativada`, `matricula.cancelada`, `pagamento.gerado`, `pagamento.registrado`, `pagamento.atrasado`, `pagamento.cancelado` (este último acrescentado — *decisão minha*). `dados` mínimos: ids (matrícula, aluno, cobrança), valor, datas relevantes (vencimento, vigente_ate), motivo quando houver — **sem CPF, e-mail ou outro dado pessoal** além de ids. Nenhum consumidor nesta fase; tópicos declarados como `NewTopic` pelos módulos donos.

## 9. Auditoria
Criação, suspensão, reativação e cancelamento de matrícula; renovação manual; registro de pagamento; abono de cobrança; alteração de antecedência/carência. Ações do job **não** são auditadas (geram eventos; a auditoria é para ações humanas — *decisão minha*).

## 10. Frontend (painel `/admin`)
- **Matrículas**: lista (filtro por status, busca por aluno, chip de status colorido, vigente até); "Nova matrícula" (aluno com autocomplete só de ATIVOS sem matrícula, plano ATIVO, data de início, valor pré-preenchido); **detalhe** com dados, histórico de períodos, cobranças e ações conforme o status (Renovar, Suspender, Reativar, Cancelar — com diálogo de motivo e confirmação).
- **Cobranças**: lista com filtros (status, intervalo de vencimento), ação "Registrar pagamento" (diálogo: data — padrão hoje —, forma, observações) e "Abonar".
- **Inadimplência**: lista com total em atraso e dias de atraso; atalho para a matrícula.
- **Ficha do aluno**: cartão com a matrícula atual e se está inadimplente.
- **Academia**: campos de antecedência e carência.
- Valores em `pt-BR` (R$), datas `dd/MM/aaaa`.

## 11. Testes
- **Domínio (unitários)**: máquina de estados da matrícula (todas as transições válidas e inválidas); calendário com âncora (31/01 mensal, 29/02 anual, trimestral); ordem de quitação; reativação desloca datas; cancelamento preserva atrasadas.
- **Job (IT, `Clock` fixo, app como `locgym_app`)**: gera 7 dias antes; não duplica ao rodar 2× nem com 2 execuções simultâneas; atrasa no dia seguinte ao vencimento; vence após 10 dias de carência (no 10º dia ainda ATIVA, no 11º VENCIDA); respeita antecedência/carência configuradas por academia; ignora suspensa e academia inativa; falha forçada numa academia não impede as demais; cada academia só vê as próprias linhas.
- **Fluxos (IT)**: matrícula → pagar primeira → ATIVA; ciclo completo de 3 meses avançando o `Clock`; vencida há 3 meses → pagar a atrasada → nascem as cobranças dos meses parados (ATRASADO) e a matrícula segue VENCIDA → abonar em lote até o mês anterior e pagar o atual → ATIVA, vigência contínua a partir do vencimento original; academia reativada após meses inativa → job recupera os períodos; abonar estende vigência; pagamento concorrente da mesma cobrança → um 200 e um 409; eventos corretos no outbox para cada operação.
- **Isolamento**: todos os endpoints novos — academia A não lê nem altera matrícula/cobrança da B (404); RLS nas tabelas novas.
- **Arquitetura**: `ApplicationModules.verify()` com `matricula → pagamento` e sem ciclo.
- **Frontend**: serviços e formulários principais (nova matrícula, registrar pagamento, diálogos de motivo).

## 12. Documentação
Atualizar 02 (regras da Fase 3), 04 (ER com matrícula/período/pagamento), 05 (módulos e job), 06 (endpoints), 07 (eventos — produtores reais), 08 (Fase 3 concluída; ideias candidatas marcadas como não adotadas), 09 (ADRs: job multi-tenant por academia; quitação síncrona na mesma transação; calendário com âncora; abono estende vigência).

## 13. Deixado para o construtor
- Estrutura interna de pacotes/DTOs, seguindo `dominio/aplicacao/infraestrutura/web`.
- Como o job abre uma transação por academia (ex.: `TransactionTemplate` dentro do laço com `TenantContext` definido e limpo em `finally`).
- Tamanho de página e ordenação padrão das listas; layout visual das telas.
- Versão do ShedLock.

## 14. Decisões minhas (revisáveis)

(Aprovadas pelo usuário: abordagem A no job e A na quitação; nenhuma ideia candidata; regras do design geral §5.5; **recuperação de períodos parados com abono em lote (opção A)**.)

- `Perfil.SISTEMA` para o job; ações do job não auditadas.
- `dataInicio` entre hoje − 30 e hoje + 30 dias; valor editável só na criação.
- Calendário com âncora (`data_base`).
- Quitação obrigatoriamente pela cobrança mais antiga.
- Matrícula VENCIDA (ou PENDENTE) não acumula novas cobranças; PENDENTE não vira VENCIDA.
- Abonar cobrança = período concedido (estende vigência, não conta como recebido); novo evento `pagamento.cancelado`.
- Endpoint de rotina diária só no perfil `dev`, na data real; `Clock` de negócio separado em `America/Sao_Paulo`.
- Matrícula paga antes do início fica ATIVA; check-in (Fase 4) exigirá `data_inicio ≤ hoje`.
- Cobranças de matrícula suspensa podem ser quitadas; períodos deslocados na reativação.
- Quitar dívida de matrícula cancelada não altera a matrícula.
- Inadimplência servida pelo módulo `matricula` (evita ciclo); `aluno` e `plano` ganham APIs públicas mínimas; `ContextoTenant` aceita `usuarioId` nulo.
- Sem estorno de pagamento nesta fase.
