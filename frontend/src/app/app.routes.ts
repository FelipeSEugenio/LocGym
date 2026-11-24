import { Routes } from '@angular/router';
import { autenticadoGuard, perfilGuard, trocaDeSenhaGuard } from './core/auth/guards';
import { Inicio } from './paginas/inicio/inicio';

const logado = [autenticadoGuard, trocaDeSenhaGuard];

export const routes: Routes = [
  { path: '', component: Inicio, title: 'LocGym' },
  { path: 'login', loadComponent: () => import('./paginas/login/login').then((m) => m.Login), title: 'Entrar — LocGym' },
  {
    path: 'criar-academia',
    loadComponent: () => import('./paginas/cadastro-administrador/cadastro-administrador').then((m) => m.CadastroAdministrador),
    title: 'Criar academia — LocGym',
  },
  {
    path: 'trocar-senha',
    canActivate: [autenticadoGuard],
    loadComponent: () => import('./paginas/trocar-senha/trocar-senha').then((m) => m.TrocarSenha),
    title: 'Definir senha — LocGym',
  },
  {
    path: 'selecionar-academia',
    canActivate: logado,
    loadComponent: () => import('./paginas/selecionar-academia/selecionar-academia').then((m) => m.SelecionarAcademia),
  },
  {
    path: 'sem-academia',
    canActivate: logado,
    loadComponent: () => import('./paginas/selecionar-academia/selecionar-academia').then((m) => m.SelecionarAcademia),
  },
  {
    path: 'nova-academia',
    canActivate: logado,
    loadComponent: () => import('./paginas/nova-academia/nova-academia').then((m) => m.NovaAcademia),
    title: 'Nova academia — LocGym',
  },
  {
    path: 'admin',
    canActivate: [...logado, perfilGuard('ADMIN')],
    loadComponent: () => import('./layout/shell-admin/shell-admin').then((m) => m.ShellAdmin),
    title: 'LocGym — Painel',
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'alunos' },
      { path: 'alunos', loadComponent: () => import('./paginas/admin/alunos/alunos-lista').then((m) => m.AlunosLista) },
      { path: 'alunos/novo', loadComponent: () => import('./paginas/admin/alunos/aluno-form').then((m) => m.AlunoForm) },
      { path: 'alunos/:id', loadComponent: () => import('./paginas/admin/alunos/aluno-form').then((m) => m.AlunoForm) },
      { path: 'professores', loadComponent: () => import('./paginas/admin/professores/professores-lista').then((m) => m.ProfessoresLista) },
      { path: 'professores/novo', loadComponent: () => import('./paginas/admin/professores/professor-form').then((m) => m.ProfessorForm) },
      { path: 'professores/:id', loadComponent: () => import('./paginas/admin/professores/professor-form').then((m) => m.ProfessorForm) },
      { path: 'planos', loadComponent: () => import('./paginas/admin/planos/planos-lista').then((m) => m.PlanosLista) },
      { path: 'planos/novo', loadComponent: () => import('./paginas/admin/planos/plano-form').then((m) => m.PlanoForm) },
      { path: 'planos/:id', loadComponent: () => import('./paginas/admin/planos/plano-form').then((m) => m.PlanoForm) },
      { path: 'matriculas', loadComponent: () => import('./paginas/admin/matriculas/matriculas-lista').then((m) => m.MatriculasLista) },
      { path: 'matriculas/nova', loadComponent: () => import('./paginas/admin/matriculas/nova-matricula').then((m) => m.NovaMatricula) },
      { path: 'matriculas/:id', loadComponent: () => import('./paginas/admin/matriculas/matricula-detalhe').then((m) => m.MatriculaDetalhePagina) },
      { path: 'cobrancas', loadComponent: () => import('./paginas/admin/cobrancas/cobrancas-lista').then((m) => m.CobrancasLista) },
      { path: 'inadimplencia', loadComponent: () => import('./paginas/admin/inadimplencia/inadimplencia').then((m) => m.InadimplenciaPagina) },
      { path: 'administradores', loadComponent: () => import('./paginas/admin/administradores/administradores').then((m) => m.Administradores) },
      { path: 'academia', loadComponent: () => import('./paginas/admin/academia/academia').then((m) => m.AcademiaPagina) },
      { path: 'agenda', loadComponent: () => import('./paginas/admin/agenda/agenda-lista').then((m) => m.AgendaLista) },
      { path: 'agenda/novo', loadComponent: () => import('./paginas/admin/agenda/horario-form').then((m) => m.HorarioForm) },
      { path: 'agenda/:id', loadComponent: () => import('./paginas/admin/agenda/horario-form').then((m) => m.HorarioForm) },
      { path: 'recepcao', loadComponent: () => import('./paginas/admin/recepcao/recepcao').then((m) => m.Recepcao) },
      { path: 'frequencia', loadComponent: () => import('./paginas/admin/frequencia/frequencia-lista').then((m) => m.FrequenciaLista) },
    ],
  },
  {
    path: 'aluno',
    canActivate: [...logado, perfilGuard('ALUNO')],
    loadComponent: () => import('./layout/shell-aluno/shell-aluno').then((m) => m.ShellAluno),
    title: 'LocGym — Aluno',
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'meus-dados' },
      { path: 'meus-dados', loadComponent: () => import('./paginas/aluno/meus-dados/meus-dados').then((m) => m.MeusDados) },
      { path: 'meu-qr', loadComponent: () => import('./paginas/aluno/meu-qr/meu-qr').then((m) => m.MeuQr) },
    ],
  },
  { path: '**', redirectTo: '' },
];
