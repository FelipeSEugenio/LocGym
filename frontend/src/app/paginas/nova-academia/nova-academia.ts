import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { CamposAcademia, formularioAcademia } from '../../componentes/campos-academia/campos-academia';
import { AcademiaApi } from '../../core/api/cadastros';
import { mensagemDeErro } from '../../core/api/erros';
import { AuthService } from '../../core/auth/auth.service';
import { DadosAcademia } from '../../core/auth/modelos';
import { ContextoService } from '../../core/contexto/contexto.service';

/** Usuário já logado cria mais uma academia e passa a administrá-la. */
@Component({
  selector: 'app-nova-academia',
  imports: [ReactiveFormsModule, MatCardModule, MatButtonModule, RouterLink, CamposAcademia],
  template: `
    <main class="centro">
      <mat-card appearance="outlined" class="largo">
        <mat-card-header><mat-card-title>Nova academia</mat-card-title></mat-card-header>
        <form [formGroup]="form" (ngSubmit)="criar()">
          <mat-card-content>
            <app-campos-academia [grupo]="form" />
            @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
          </mat-card-content>
          <mat-card-actions>
            <button mat-flat-button type="submit">Criar academia</button>
            <a mat-button routerLink="/">Voltar</a>
          </mat-card-actions>
        </form>
      </mat-card>
    </main>
  `,
  styleUrl: '../formularios.scss',
})
export class NovaAcademia {
  private readonly api = inject(AcademiaApi);
  private readonly auth = inject(AuthService);
  private readonly contexto = inject(ContextoService);
  private readonly router = inject(Router);

  readonly form = formularioAcademia(inject(FormBuilder));
  readonly erro = signal<string | null>(null);

  criar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.api
      .criar(this.form.getRawValue() as DadosAcademia)
      .pipe(switchMap((academia) => {
        this.contexto.selecionar(academia.id, 'ADMIN');
        return this.auth.atualizarVinculos();
      }))
      .subscribe({
        next: () => void this.router.navigateByUrl('/admin'),
        error: (e) => this.erro.set(mensagemDeErro(e)),
      });
  }
}
