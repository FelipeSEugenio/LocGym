import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Vinculo } from '../../core/auth/modelos';
import { ContextoService } from '../../core/contexto/contexto.service';

/** Escolha da academia/perfil quando o usuário tem mais de um vínculo — ou nenhum. */
@Component({
  selector: 'app-selecionar-academia',
  imports: [MatCardModule, MatListModule, MatButtonModule, RouterLink],
  template: `
    <main class="centro">
      <mat-card appearance="outlined">
        <mat-card-header><mat-card-title>Escolha onde entrar</mat-card-title></mat-card-header>
        <mat-card-content>
          @if (auth.vinculos().length) {
            <mat-action-list>
              @for (v of auth.vinculos(); track v.academiaId + v.perfil) {
                <button mat-list-item (click)="escolher(v)">
                  {{ v.academiaNome }} — {{ v.perfil === 'ADMIN' ? 'Administrador' : 'Aluno' }}
                  @if (v.academiaStatus === 'INATIVA') { (inativa) }
                </button>
              }
            </mat-action-list>
          } @else {
            <p>Você não tem acesso a nenhuma academia no momento.</p>
          }
        </mat-card-content>
        <mat-card-actions>
          <a mat-button routerLink="/nova-academia">Criar academia</a>
          <button mat-button (click)="sair()">Sair</button>
        </mat-card-actions>
      </mat-card>
    </main>
  `,
  styleUrl: '../formularios.scss',
})
export class SelecionarAcademia {
  readonly auth = inject(AuthService);
  private readonly contexto = inject(ContextoService);
  private readonly router = inject(Router);

  escolher(v: Vinculo): void {
    this.contexto.selecionar(v.academiaId, v.perfil);
    void this.router.navigateByUrl(v.perfil === 'ADMIN' ? '/admin' : '/aluno');
  }

  sair(): void {
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/login'));
  }
}
