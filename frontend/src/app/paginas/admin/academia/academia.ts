import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CamposAcademia, formularioAcademia } from '../../../componentes/campos-academia/campos-academia';
import { AcademiaApi } from '../../../core/api/cadastros';
import { mensagemDeErro } from '../../../core/api/erros';
import { Academia } from '../../../core/api/modelos';
import { AuthService } from '../../../core/auth/auth.service';
import { DadosAcademia } from '../../../core/auth/modelos';

@Component({
  selector: 'app-academia',
  imports: [ReactiveFormsModule, MatButtonModule, CamposAcademia],
  template: `
    <h2>Dados da academia</h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" class="formulario">
      <app-campos-academia [grupo]="form" />
      @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
      <div class="acoes">
        <button mat-flat-button type="submit">Salvar</button>
        @if (academia(); as a) {
          <button mat-stroked-button type="button" (click)="alternarStatus(a)">
            {{ a.status === 'ATIVA' ? 'Inativar academia' : 'Reativar academia' }}
          </button>
        }
      </div>
    </form>
  `,
  styleUrl: '../listas.scss',
})
export class AcademiaPagina {
  private readonly api = inject(AcademiaApi);
  private readonly auth = inject(AuthService);
  private readonly avisos = inject(MatSnackBar);
  readonly form = formularioAcademia(inject(FormBuilder));
  readonly academia = signal<Academia | null>(null);
  readonly erro = signal<string | null>(null);

  constructor() {
    this.api.ativa().subscribe((a) => {
      this.academia.set(a);
      this.form.patchValue(a);
    });
  }

  salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.api.atualizar(this.form.getRawValue() as DadosAcademia).subscribe({
      next: (a) => {
        this.academia.set(a);
        this.erro.set(null);
        this.avisos.open('Dados salvos.', 'OK', { duration: 3000 });
        this.auth.atualizarVinculos().subscribe();
      },
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }

  alternarStatus(a: Academia): void {
    this.api.alterarStatus(a.status === 'ATIVA' ? 'INATIVA' : 'ATIVA').subscribe({
      next: (atualizada) => {
        this.academia.set(atualizada);
        this.erro.set(null);
        this.auth.atualizarVinculos().subscribe();
      },
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
