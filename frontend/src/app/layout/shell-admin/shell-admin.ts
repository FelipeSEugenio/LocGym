import { Component } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterOutlet } from '@angular/router';

/** Layout do painel administrativo. Os módulos (Alunos, Planos, ...) entram no menu a partir da Fase 2. */
@Component({
  selector: 'app-shell-admin',
  imports: [MatToolbarModule, MatSidenavModule, MatListModule, RouterLink, RouterOutlet],
  template: `
    <mat-toolbar color="primary">LocGym — Painel</mat-toolbar>
    <mat-sidenav-container class="conteiner">
      <mat-sidenav mode="side" opened>
        <mat-nav-list>
          <a mat-list-item routerLink="/">Início</a>
          <!-- Módulos do painel (Fase 2+) -->
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content class="conteudo">
        <router-outlet />
        <p>Os módulos do painel administrativo serão adicionados nas próximas fases.</p>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    .conteiner { height: calc(100dvh - 64px); }
    mat-sidenav { width: 220px; }
    .conteudo { padding: 24px; }
  `,
})
export class ShellAdmin {}
