import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Vinculo } from '../../core/auth/modelos';
import { ContextoService } from '../../core/contexto/contexto.service';

const NOME_PERFIL = { ADMIN: 'Administrador', ALUNO: 'Aluno' } as const;

/** Menu da barra superior: troca de academia/perfil, criar academia e sair. */
@Component({
  selector: 'app-seletor-contexto',
  imports: [MatMenuModule, MatButtonModule, MatIconModule, MatDividerModule],
  template: `
    <button mat-button [matMenuTriggerFor]="menu" class="gatilho">
      <mat-icon>swap_horiz</mat-icon> {{ atual()?.rotulo ?? 'Escolher academia' }}
    </button>
    <mat-menu #menu="matMenu">
      @for (opcao of opcoes(); track opcao.rotulo) {
        <button mat-menu-item (click)="escolher(opcao.vinculo)">{{ opcao.rotulo }}</button>
      }
      <mat-divider />
      <button mat-menu-item (click)="navegar('/nova-academia')"><mat-icon>add_business</mat-icon> Criar academia</button>
      <button mat-menu-item (click)="sair()"><mat-icon>logout</mat-icon> Sair</button>
    </mat-menu>
  `,
  styles: `.gatilho { color: inherit; }`,
})
export class SeletorContexto {
  private readonly auth = inject(AuthService);
  private readonly contexto = inject(ContextoService);
  private readonly router = inject(Router);

  readonly opcoes = computed(() =>
    this.auth.vinculos().map((v) => ({ vinculo: v, rotulo: `${v.academiaNome} — ${NOME_PERFIL[v.perfil]}` })),
  );
  readonly atual = computed(() => {
    const ctx = this.contexto.atual();
    return this.opcoes().find((o) => o.vinculo.academiaId === ctx?.academiaId && o.vinculo.perfil === ctx?.perfil);
  });

  escolher(v: Vinculo): void {
    this.contexto.selecionar(v.academiaId, v.perfil);
    void this.router.navigateByUrl(v.perfil === 'ADMIN' ? '/admin' : '/aluno');
  }

  navegar(url: string): void {
    void this.router.navigateByUrl(url);
  }

  sair(): void {
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/login'));
  }
}
