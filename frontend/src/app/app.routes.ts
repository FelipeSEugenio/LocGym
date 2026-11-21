import { Routes } from '@angular/router';
import { Inicio } from './paginas/inicio/inicio';

export const routes: Routes = [
  { path: '', component: Inicio, title: 'LocGym' },
  {
    path: 'admin',
    loadComponent: () => import('./layout/shell-admin/shell-admin').then((m) => m.ShellAdmin),
    title: 'LocGym — Painel',
  },
  {
    path: 'aluno',
    loadComponent: () => import('./layout/shell-aluno/shell-aluno').then((m) => m.ShellAluno),
    title: 'LocGym — Aluno',
  },
  { path: '**', redirectTo: '' },
];
