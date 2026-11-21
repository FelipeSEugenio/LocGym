import { Component } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterOutlet } from '@angular/router';

/** Layout mobile-first do portal do aluno. As telas do portal entram na Fase 5. */
@Component({
  selector: 'app-shell-aluno',
  imports: [MatToolbarModule, RouterOutlet],
  template: `
    <mat-toolbar color="primary">LocGym — Aluno</mat-toolbar>
    <main class="conteudo">
      <router-outlet />
      <p>O portal do aluno será construído nas próximas fases.</p>
    </main>
  `,
  styles: `
    .conteudo { padding: 16px; max-width: 720px; margin: 0 auto; }
  `,
})
export class ShellAluno {}
