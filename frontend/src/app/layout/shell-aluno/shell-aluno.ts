import { Component } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SeletorContexto } from '../seletor-contexto/seletor-contexto';

/** Layout mobile-first do portal do aluno. */
@Component({
  selector: 'app-shell-aluno',
  imports: [MatToolbarModule, RouterLink, RouterLinkActive, RouterOutlet, SeletorContexto],
  template: `
    <mat-toolbar color="primary">
      <span>LocGym</span>
      <span class="espaco"></span>
      <app-seletor-contexto />
    </mat-toolbar>
    <nav class="abas">
      <a routerLink="meus-dados" routerLinkActive="ativo">Meus dados</a>
      <a routerLink="meu-qr" routerLinkActive="ativo">Meu QR Code</a>
    </nav>
    <main class="conteudo"><router-outlet /></main>
  `,
  styles: `
    .espaco { flex: 1; }
    .conteudo { padding: 16px; max-width: 720px; margin: 0 auto; }
    .abas { display: flex; gap: 16px; padding: 8px 16px; max-width: 720px; margin: 0 auto; }
    .abas a { text-decoration: none; color: inherit; padding: 4px 0; }
    .abas .ativo { font-weight: 600; border-bottom: 2px solid currentColor; }
  `,
})
export class ShellAluno {}
