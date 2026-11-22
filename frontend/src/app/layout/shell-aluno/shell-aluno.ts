import { Component } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterOutlet } from '@angular/router';
import { SeletorContexto } from '../seletor-contexto/seletor-contexto';

/** Layout mobile-first do portal do aluno. */
@Component({
  selector: 'app-shell-aluno',
  imports: [MatToolbarModule, RouterOutlet, SeletorContexto],
  template: `
    <mat-toolbar color="primary">
      <span>LocGym</span>
      <span class="espaco"></span>
      <app-seletor-contexto />
    </mat-toolbar>
    <main class="conteudo"><router-outlet /></main>
  `,
  styles: `
    .espaco { flex: 1; }
    .conteudo { padding: 16px; max-width: 720px; margin: 0 auto; }
  `,
})
export class ShellAluno {}
