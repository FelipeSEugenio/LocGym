import { Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ContextoService } from '../../core/contexto/contexto.service';
import { SeletorContexto } from '../seletor-contexto/seletor-contexto';

/** Layout do painel administrativo, organizado por módulos. */
@Component({
  selector: 'app-shell-admin',
  imports: [MatToolbarModule, MatSidenavModule, MatListModule, MatIconModule, RouterLink, RouterLinkActive, RouterOutlet, SeletorContexto],
  template: `
    <mat-toolbar color="primary">
      <span>LocGym — Painel</span>
      <span class="espaco"></span>
      <app-seletor-contexto />
    </mat-toolbar>
    @if (academiaInativa()) {
      <div class="aviso" role="status">Academia inativa — somente leitura. Reative em "Academia".</div>
    }
    <mat-sidenav-container class="conteiner">
      <mat-sidenav mode="side" opened>
        <mat-nav-list>
          @for (item of menu; track item.rota) {
            <a mat-list-item [routerLink]="item.rota" routerLinkActive="ativo">
              <mat-icon matListItemIcon>{{ item.icone }}</mat-icon>{{ item.rotulo }}
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content class="conteudo"><router-outlet /></mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    .espaco { flex: 1; }
    .conteiner { height: calc(100dvh - 64px); }
    mat-sidenav { width: 220px; }
    .conteudo { padding: 24px; }
    .aviso { background: #fff4e5; color: #663c00; padding: 8px 24px; }
    .ativo { font-weight: 600; }
  `,
})
export class ShellAdmin {
  private readonly auth = inject(AuthService);
  private readonly contexto = inject(ContextoService);

  readonly menu = [
    { rota: 'alunos', rotulo: 'Alunos', icone: 'group' },
    { rota: 'professores', rotulo: 'Professores', icone: 'sports_martial_arts' },
    { rota: 'planos', rotulo: 'Planos', icone: 'sell' },
    { rota: 'administradores', rotulo: 'Administradores', icone: 'admin_panel_settings' },
    { rota: 'academia', rotulo: 'Academia', icone: 'store' },
  ];

  readonly academiaInativa = computed(() => {
    const ctx = this.contexto.atual();
    return this.auth.vinculos().some((v) => v.academiaId === ctx?.academiaId && v.academiaStatus === 'INATIVA');
  });
}
