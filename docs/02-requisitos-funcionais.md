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

## RF06 — Matrículas e cobrança recorrente
- No máximo uma matrícula não cancelada por aluno por academia; o valor é copiado do plano.
- Criar a matrícula gera a primeira cobrança (vencimento = data de início).
- **Cobrança recorrente:** uma cobrança por período (sem parcelamento), gerada **7 dias antes** do vencimento (configurável). Pagar estende a vigência por mais um período.
- Status: **PENDENTE** (primeira cobrança não paga) → **ATIVA** → **VENCIDA** (cobrança atrasada há mais de **10 dias**, carência configurável); pagar as pendências volta a ATIVA a partir do vencimento original.
- **SUSPENSA:** pausa cobranças e bloqueia check-in; ao reativar, o vencimento é estendido pelos dias suspensos.
- **CANCELADA:** terminal; cobranças pendentes são canceladas, **cobranças atrasadas são preservadas** como dívida.

## RF07 — Pagamentos e inadimplência
- Aluno, academia, matrícula, valor, vencimento, data de pagamento, forma (dinheiro, PIX, crédito, débito, transferência), status (PENDENTE/PAGO/ATRASADO/CANCELADO), observações.
- Registro manual pelo admin, sempre do valor integral.
- Job diário (fuso `America/Sao_Paulo`): gera cobranças, marca ATRASADO, marca matrículas VENCIDAS. Idempotente.
- **Inadimplente** = tem ao menos um pagamento ATRASADO (calculado, não armazenado).

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
