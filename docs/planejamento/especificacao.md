# LocGym — Especificação original

## 1. Objetivo do projeto

O **LocGym** é um sistema web de gestão para academias de Jiu-Jitsu, desenvolvido desde o início para suportar múltiplas academias na mesma plataforma.

O sistema será uma plataforma multi-tenant, na qual cada academia possui seus próprios alunos, planos, pagamentos, professores, horários, aulas, check-ins e configurações.

O objetivo do LocGym é permitir que o dono ou administrador da academia gerencie a operação de forma simples, organizada e escalável.

## 2. Escopo do MVP

O MVP do LocGym deve incluir:

- Cadastro e autenticação de administradores.
- Cadastro de academias.
- Cadastro e autenticação de alunos.
- Vinculação de alunos a academias.
- Cadastro de professores.
- Cadastro de planos por academia.
- Matrícula de alunos em planos.
- Controle de status da matrícula.
- Controle de vencimento e renovação.
- Controle de inadimplência.
- Registro de pagamentos.
- Cadastro de horários e aulas.
- Check-in por QR Code.
- Registro manual de presença pelo administrador.
- Painel administrativo para gestão da academia.
- Portal do aluno.
- Relatórios operacionais iniciais.
- Arquitetura preparada para futura integração com WhatsApp.

O sistema não deve incluir, no MVP inicial:

- Aplicativo mobile nativo.
- Integração com gateway de pagamento.
- Emissão automática de notas fiscais.
- Controle financeiro completo.
- Gestão de estoque.
- Gestão de loja.
- Controle de acesso por catraca.
- Integração com redes sociais.
- Sistema de marketing automatizado.
- Aplicativo para professores.

## 3. Perfis de usuário

O sistema terá dois perfis principais:

### Administrador

O administrador é o usuário responsável por gerenciar uma ou mais academias.

Ele poderá:

- Criar e editar alunos.
- Criar e editar professores.
- Criar e editar planos.
- Matricular alunos em planos.
- Registrar pagamentos.
- Consultar inadimplência.
- Criar horários e aulas.
- Registrar presença manual.
- Consultar frequência dos alunos.
- Visualizar relatórios e indicadores.
- Gerenciar configurações da academia.

### Aluno

O aluno é a pessoa matriculada em uma academia.

Ele poderá:

- Fazer login com e-mail e senha.
- Visualizar seus dados cadastrais.
- Visualizar sua matrícula.
- Visualizar o plano contratado.
- Visualizar a data de vencimento.
- Visualizar pagamentos e situação financeira.
- Visualizar horários e aulas.
- Realizar check-in por QR Code.
- Visualizar seu histórico de frequência.

## 4. Estrutura multi-tenant

O LocGym será multi-tenant desde o início.

Cada academia será um tenant independente.

Os dados de alunos, professores, planos, matrículas, pagamentos, horários, aulas, check-ins e relatórios devem estar sempre vinculados à academia correta.

Um administrador poderá estar vinculado a uma ou mais academias.

Um aluno poderá estar vinculado a uma ou mais academias, conforme a necessidade futura.

Todas as consultas, cadastros, atualizações e relatórios devem respeitar o isolamento por academia.

## 5. Cadastro de academias

Cada academia deve possuir:

- Nome.
- CNPJ.
- Endereço.
- Cidade.
- Estado.
- CEP.
- Telefone.
- E-mail.
- Status: ativa ou inativa.
- Data de criação.
- Data de atualização.

O administrador poderá criar e editar os dados da academia.

## 6. Cadastro de alunos

Cada aluno deve possuir:

- Nome completo.
- E-mail.
- CPF.
- Telefone.
- Data de nascimento.
- Endereço.
- Cidade.
- Estado.
- CEP.
- Status: ativo, inativo ou cancelado.
- Data de cadastro.
- Data de atualização.

O e-mail será a credencial de login do aluno.

O CPF será um dado cadastral e identificador único do aluno.

O CPF não será usado como senha.

O aluno poderá estar vinculado a uma ou mais academias.

## 7. Cadastro de professores

Cada professor deve possuir:

- Nome completo.
- E-mail.
- Telefone.
- Data de nascimento.
- Especialidade.
- Status: ativo ou inativo.
- Data de cadastro.
- Data de atualização.

O professor deve estar vinculado a uma academia.

Um professor poderá ministrar uma ou mais aulas.

## 8. Planos e matrículas

Cada academia poderá criar seus próprios planos.

Cada plano deve possuir:

- Nome.
- Descrição.
- Valor.
- Periodicidade: mensal, trimestral, semestral ou anual.
- Status: ativo ou inativo.
- Data de criação.
- Data de atualização.

O aluno poderá ser matriculado em um plano.

Cada matrícula deve possuir:

- Aluno.
- Academia.
- Plano.
- Data de início.
- Data de vencimento.
- Valor.
- Status.
- Data de criação.
- Data de atualização.

Os status possíveis da matrícula são:

- Ativa.
- Pendente.
- Vencida.
- Cancelada.
- Suspensa.

A matrícula deve permitir renovação.

A renovação deve gerar um novo período de vigência e atualizar a data de vencimento.

## 9. Check-in e frequência

O check-in será realizado por QR Code.

Cada aluno terá um QR Code exclusivo no portal do aluno.

O fluxo de check-in será:

1. O aluno acessa o portal e apresenta o QR Code.
2. A academia realiza a leitura do QR Code.
3. O sistema valida se o aluno está ativo e vinculado à academia correta.
4. O sistema registra a entrada, data, horário e situação de pontualidade.
5. O sistema publica o evento `checkin.registrado` no Kafka.

Cada entrada de frequência deve registrar:

- Aluno.
- Academia.
- Data da entrada.
- Horário da entrada.
- Origem: QR Code ou manual.
- Situação de pontualidade: pontual ou atrasado.
- Status: ativa, corrigida ou excluída.

O administrador poderá cadastrar uma presença manualmente para um aluno que não conseguiu realizar o check-in por QR Code.

## 10. Pagamentos e inadimplência

O sistema deve registrar pagamentos relacionados às matrículas.

Cada pagamento deve possuir:

- Aluno.
- Academia.
- Matrícula.
- Valor.
- Data de vencimento.
- Data de pagamento.
- Forma de pagamento.
- Status.
- Observações.
- Data de criação.
- Data de atualização.

Os status possíveis do pagamento são:

- Pendente.
- Pago.
- Atrasado.
- Cancelado.

O sistema deve identificar automaticamente pagamentos vencidos e marcar o aluno como inadimplente.

A inadimplência deve ser calculada com base em pagamentos atrasados vinculados à matrícula do aluno.

O MVP não deve integrar gateway de pagamento.

O registro de pagamento será manual pelo administrador.

## 11. Horários e aulas

Cada academia poderá cadastrar horários e aulas.

Cada horário deve possuir:

- Academia.
- Professor.
- Dia da semana.
- Horário de início.
- Horário de término.
- Tipo de aula.
- Status: ativo ou inativo.
- Data de criação.
- Data de atualização.

O sistema deve permitir consultar os horários disponíveis por academia.

O aluno poderá visualizar os horários disponíveis no portal.

## 12. Portal do aluno

O portal do aluno deve permitir:

- Login com e-mail e senha.
- Visualização dos dados cadastrais.
- Visualização da matrícula.
- Visualização do plano contratado.
- Visualização da data de vencimento.
- Visualização da situação financeira.
- Visualização dos pagamentos.
- Visualização dos horários e aulas.
- Acesso ao QR Code para check-in.
- Visualização do histórico de frequência.

O portal deve ser simples, claro e responsivo.

## 13. Painel administrativo

O painel administrativo deve permitir:

- Login com e-mail e senha.
- Seleção da academia ativa.
- Cadastro e edição de alunos.
- Cadastro e edição de professores.
- Cadastro e edição de planos.
- Matrícula de alunos.
- Renovação de matrículas.
- Registro de pagamentos.
- Consulta de inadimplência.
- Cadastro de horários e aulas.
- Registro manual de presença.
- Consulta de frequência.
- Visualização de relatórios e indicadores.

O painel deve ser organizado por módulos.

## 14. Relatórios iniciais

O sistema deve incluir relatórios iniciais para o administrador.

Os relatórios devem incluir:

- Total de alunos ativos.
- Total de alunos inativos.
- Total de matrículas ativas.
- Total de matrículas vencidas.
- Total de alunos inadimplentes.
- Total de pagamentos recebidos.
- Total de pagamentos pendentes.
- Total de pagamentos atrasados.
- Frequência por período.
- Frequência por aluno.
- Frequência por horário.
- Frequência por professor.

## 15. Notificações

O sistema deve ser preparado para envio de notificações futuras.

As notificações iniciais podem incluir:

- Aviso de vencimento de matrícula.
- Aviso de pagamento pendente.
- Aviso de pagamento atrasado.
- Aviso de inadimplência.
- Aviso de renovação.
- Confirmação de check-in.

No MVP, o sistema deve apenas preparar a estrutura para notificações.

O envio automático por WhatsApp poderá ser implementado em uma fase posterior.

## 16. Stack tecnológica

O projeto deve utilizar a seguinte stack:

- Frontend: Angular com TypeScript.
- Backend: Java 21 com Spring Boot.
- Banco de dados: PostgreSQL.
- Mensageria: Apache Kafka.
- Containerização: Docker e Docker Compose.
- CI/CD: GitHub Actions.
- Documentação: Markdown.

A arquitetura deve ser modular, organizada e preparada para evolução futura.

O sistema deve seguir boas práticas de:

- Separação de responsabilidades.
- Clean Architecture.
- SOLID.
- Testes automatizados.
- Versionamento de código.
- Documentação técnica.
- Segurança.
- Escalabilidade.
- Manutenibilidade.

## 17. Estrutura inicial do projeto

O projeto deve ser criado em um monorepo.

A estrutura inicial sugerida é:

```text
locgym/
├── backend/
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/
│   ├── src/
│   ├── package.json
│   └── Dockerfile
├── docs/
│   ├── 01-visao-geral.md
│   ├── 02-requisitos-funcionais.md
│   ├── 03-requisitos-nao-funcionais.md
│   ├── 04-modelo-de-dados.md
│   ├── 05-arquitetura.md
│   ├── 06-api.md
│   ├── 07-eventos.md
│   ├── 08-roadmap.md
│   └── 09-decisoes-tecnicas.md
├── docker-compose.yml
├── .github/
│   └── workflows/
│       └── ci.yml
└── README.md
```

## 18. Banco de dados

O banco de dados deve ser PostgreSQL.

As principais entidades iniciais são:

- Academia.
- Usuário.
- Administrador.
- Aluno.
- Professor.
- Plano.
- Matrícula.
- Pagamento.
- Horário.
- Aula.
- Check-in.
- Frequência.

Todas as entidades devem possuir:

- ID.
- Data de criação.
- Data de atualização.
- Status, quando aplicável.
- Vínculo com a academia, quando aplicável.

## 19. Autenticação

O acesso ao sistema será feito por e-mail e senha.

### Aluno

- O aluno fará login com e-mail e senha.
- O e-mail será a credencial de acesso.
- O CPF será um dado cadastral e identificador único do aluno.
- O CPF não será usado como senha.
- O aluno acessará apenas os dados da academia em que estiver cadastrado.

### Administrador

- O administrador fará login com e-mail e senha.
- O administrador poderá estar vinculado a uma ou mais academias.
- O administrador acessará apenas os dados das academias às quais estiver vinculado.

### Segurança

- Senhas devem ser armazenadas com hash seguro.
- O sistema deve implementar autenticação baseada em tokens.
- O sistema deve controlar permissões por perfil: administrador e aluno.
- A recuperação de senha por e-mail poderá ser implementada em uma fase posterior, caso não faça parte do MVP inicial.

## 20. Regras de negócio iniciais

- Cada academia é um tenant independente.
- Um aluno pode estar vinculado a uma ou mais academias.
- Um administrador pode gerenciar uma ou mais academias.
- Um plano pertence a uma academia.
- Uma matrícula pertence a um aluno e a uma academia.
- Uma matrícula pode ter um ou mais pagamentos.
- Um pagamento pode estar pendente, pago, atrasado ou cancelado.
- Um aluno é considerado inadimplente quando possui pagamento atrasado.
- O check-in pode ser feito por QR Code ou registrado manualmente pelo administrador.
- O check-in deve registrar data, horário e situação de pontualidade.
- O sistema deve permitir correção ou exclusão lógica de registros de frequência.
- O sistema deve permitir renovação de matrícula.
- O sistema deve permitir cancelamento de matrícula.
- O sistema deve permitir suspensão de matrícula.

## 21. Requisitos não funcionais

O sistema deve atender aos seguintes requisitos:

- Segurança.
- Escalabilidade.
- Manutenibilidade.
- Performance.
- Disponibilidade.
- Observabilidade.
- Testabilidade.
- Documentação.
- Versionamento.
- Backup e recuperação.
- Isolamento de dados por academia.
- Controle de acesso por perfil.
- Auditoria de operações críticas.

## 22. Roadmap inicial

### Fase 1 — Fundação

- Estrutura do monorepo.
- Backend em Java com Spring Boot.
- Frontend em Angular com TypeScript.
- Banco PostgreSQL.
- Docker Compose.
- GitHub Actions.
- Documentação inicial.

### Fase 2 — Cadastros

- Cadastro de academias.
- Cadastro de administradores.
- Cadastro de alunos.
- Cadastro de professores.
- Cadastro de planos.

### Fase 3 — Matrículas e pagamentos

- Matrícula de alunos.
- Controle de vencimento.
- Renovação.
- Cancelamento.
- Registro de pagamentos.
- Controle de inadimplência.

### Fase 4 — Frequência

- Cadastro de horários.
- Cadastro de aulas.
- Check-in por QR Code.
- Registro manual de presença.
- Histórico de frequência.

### Fase 5 — Portal e relatórios

- Portal do aluno.
- Painel administrativo.
- Relatórios iniciais.
- Indicadores operacionais.

### Fase 6 — Evolução

- Notificações.
- Integração com WhatsApp.
- Integração com gateway de pagamento.
- Relatórios avançados.
- Aplicativo mobile.
