# 02 — Requisitos funcionais

> Status: **RF01–RF05 implementados na Fase 2** ([design da Fase 2](planejamento/fase-2-design.md)); RF06 em diante planejados. Regras conforme o [design aprovado](planejamento/design.md).

### Acesso conforme status (Fase 2)

| Situação | Aparece no seletor e faz login? | O que pode fazer |
|---|---|---|
| Aluno ATIVO | Sim | Tudo o que o perfil permite |
| Aluno INATIVO | Sim | Só consultar os próprios dados |
| Aluno CANCELADO | Não — perde o acesso àquela academia | Nada; reativado, volta a ter acesso |
| Academia INATIVA | Sim, para admins e alunos | Somente leitura (a única escrita é reativar) |

## RF01 — Identidade e acesso
- Login por e-mail e senha; o e-mail é a credencial, global no sistema.
- Perfis derivados dos vínculos: **ADMIN** numa academia (`administrador_academia`) e/ou **ALUNO** numa academia (registro `aluno`). A mesma pessoa pode ter ambos.
- Quem tem mais de um vínculo escolhe a academia/perfil ativo após o login.
- Cadastro público apenas para administrador (cria conta + primeira academia).
- Todos os admins de uma academia têm o mesmo poder; podem convidar outros admins (senha inicial com troca obrigatória), remover vínculos de admin (nunca o último; pode sair se não for o último) e criar novas academias. O nome exibido do admin é o informado no convite.
- Qualquer usuário autenticado (inclusive quem só é aluno) pode criar uma academia.
- O contexto ativo é o par (academia, perfil): a mesma pessoa pode ser admin e aluno na mesma academia.

## RF02 — Academias
- Nome, CNPJ, endereço, cidade, estado, CEP, telefone, e-mail, status (ATIVA/INATIVA).
- **Academia INATIVA é somente leitura:** consultas funcionam; escritas, check-ins e geração de cobranças são bloqueados.

## RF03 — Alunos
- Nome, e-mail, CPF, telefone, nascimento, endereço, cidade, estado, CEP, status (ATIVO/INATIVO/CANCELADO).
- **Separado por academia:** cada academia tem seu próprio registro; **CPF único por academia**. Editar numa academia nunca afeta outra.
- Cadastrar um e-mail que já tem login em outra academia apenas liga o novo registro a esse login, **sem revelar** a existência do outro vínculo.
- O status do aluno é **independente** do status da matrícula e é alterado manualmente pelo admin.

## RF04 — Professores
- Nome, e-mail, telefone, nascimento, especialidade, status; vinculado a uma academia; pode ministrar vários horários.

## RF05 — Planos
- Nome, descrição, valor, periodicidade (MENSAL=1, TRIMESTRAL=3, SEMESTRAL=6, ANUAL=12 meses), status.

## RF06 — Matrículas e cobrança recorrente ✅ Fase 3
- No máximo uma matrícula não cancelada por aluno por academia (garantido por índice único; duas tentativas simultâneas → uma 201 e uma 409). Exige aluno e plano ATIVOS.
- Data de início entre hoje − 30 e hoje + 30 dias. O valor é copiado do plano (o admin pode dar desconto **só na criação**); mudar o preço ou inativar o plano depois não afeta a matrícula.
- Criar a matrícula gera a primeira cobrança (vencimento = data de início). Paga antes do início, a matrícula fica ATIVA e mostra "Começa em dd/mm".
- **Cobrança recorrente:** uma cobrança por período (sem parcelamento), gerada **7 dias antes** do vencimento (antecedência de 1 a 30 dias, configurável por academia). Pagar — ou abonar — estende a vigência por mais um período, na mesma transação.
- **Calendário com dia-âncora:** cada período termina no mesmo dia do mês do início, limitado ao fim do mês. Ex.: início 31/01 → 28/02 → 31/03 → 30/04.
- **Quitação em ordem:** só a cobrança em aberto mais antiga pode ser paga ou abonada ("Quite primeiro a cobrança de dd/mm/aaaa").
- Status: **PENDENTE** (primeira cobrança não paga; nunca vence) → **ATIVA** → **VENCIDA** (cobrança atrasada há mais de **10 dias**; carência de 0 a 60, configurável). Volta a ATIVA quando não resta cobrança atrasada **e** a vigência cobre o dia de hoje.
- **Aluno que volta depois de meses:** ao quitar a cobrança atrasada, o sistema gera na hora as cobranças de todos os meses parados (já atrasadas). O admin cobra ou **abona em lote** ("Abonar até aqui"). Ex.: vencida desde 10/08, paga em 15/11 → nascem 10/09, 10/10 e 10/11; abonando 09 e 10 e pagando 11, volta a ATIVA até 10/12.
- **Renovar** (manual): gera já a cobrança do próximo período, mesmo fora da antecedência.
- **SUSPENSA** (só de ATIVA, com motivo): não gera cobranças, não atrasa nem vence. **O tempo para na data da suspensão** — na reativação, toda data posterior a ela (vigência, períodos, cobranças) anda os dias suspensos; as anteriores ficam. Cobranças podem ser pagas durante a suspensão.
- **CANCELADA** (com motivo): terminal; cobranças pendentes são canceladas, **cobranças atrasadas são preservadas** como dívida. Pagar essa dívida não altera a matrícula.
- Status do aluno e status da matrícula são independentes (aluno INATIVO continua sendo cobrado).

## RF07 — Pagamentos e inadimplência ✅ Fase 3
- Aluno, academia, matrícula, valor, vencimento, período, data de pagamento, forma (dinheiro, PIX, crédito, débito, transferência), status (PENDENTE/PAGO/ATRASADO/CANCELADO), observações, motivo do cancelamento.
- Registro manual pelo admin, sempre do valor integral; data do pagamento não pode ser futura (pagamento antecipado é aceito). Pagamento simultâneo da mesma cobrança → um 200 e um 409.
- **Abono:** cancelar uma cobrança com motivo concede o período (estende a vigência) sem contar como recebido.
- **Rotina diária** às 00:05 de São Paulo, em uma instância por vez (ShedLock), academia por academia: gera cobranças (inclusive períodos que faltam), marca ATRASADO no dia seguinte ao vencimento (exceto matrícula suspensa), vence matrículas além da carência. Idempotente; falha numa academia não para as outras. Academias inativas são ignoradas.
- **Inadimplente** = tem ao menos uma cobrança ATRASADA, em qualquer matrícula (calculado, não armazenado). Tela de inadimplência ordenada pelo atraso mais antigo.

## RF08 — Horários e aulas
- Horário: professor, dia da semana, início, término, tipo de aula, status.
- Aula: ocorrência concreta de um horário numa data, criada no primeiro check-in.

## RF09 — Check-in e frequência
- QR Code no portal do aluno com **token assinado, renovado a cada 30 s**; leitura pela tela "Recepção" do painel admin.
- Exige academia ATIVA, aluno ATIVO e matrícula ATIVA. Inadimplente dentro da carência passa, com alerta.
- Associa à aula cuja janela vai de 30 min antes do início até o fim; pontual se ≤ início + tolerância (padrão 10 min).
- Check-in duplicado na mesma aula é rejeitado. Presença manual pelo admin. Correção e exclusão lógica com motivo e auditoria.
- Publica `checkin.registrado`.

## RF10 — Portal do aluno
Dados cadastrais, matrícula, plano, vencimento, situação financeira, pagamentos, horários, QR Code e histórico de frequência. Responsivo.

## RF11 — Painel administrativo
Seleção da academia ativa e módulos: Dashboard, Alunos, Professores, Planos, Matrículas, Pagamentos, Inadimplência, Agenda, Frequência, Recepção, Relatórios, Configurações.

## RF12 — Relatórios
Alunos ativos/inativos; matrículas ativas/vencidas; inadimplentes; pagamentos recebidos/pendentes/atrasados; frequência por período, aluno, horário e professor.

## RF13 — Notificações (estrutura)
Consumidor de eventos grava notificações pendentes (vencimento, pagamento pendente/atrasado, inadimplência, renovação, confirmação de check-in) por uma porta `CanalNotificacao`; envio real (WhatsApp) em fase posterior.
