import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
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
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, CamposAcademia],
  template: `
    <h2>Dados da academia</h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" class="formulario">
      <app-campos-academia [grupo]="form" />
      <h3>Cobrança recorrente</h3>
      <div class="grade" [formGroup]="parametros">
        <mat-form-field><mat-label>Gerar cobrança com antecedência de (dias)</mat-label>
          <input matInput type="number" min="1" max="30" formControlName="diasAntecedenciaCobranca" />
          <mat-error>De 1 a 30 dias</mat-error></mat-form-field>
        <mat-form-field><mat-label>Carência até vencer a matrícula (dias)</mat-label>
          <input matInput type="number" min="0" max="60" formControlName="diasCarencia" />
          <mat-error>De 0 a 60 dias</mat-error></mat-form-field>
      </div>
      <h3>Frequência</h3>
      <div class="grade" [formGroup]="frequencia">
        <mat-form-field><mat-label>Tolerância de pontualidade (minutos)</mat-label>
          <input matInput type="number" min="0" max="60" formControlName="toleranciaPontualidade" />
          <mat-hint>Check-in até o início da aula mais estes minutos é pontual</mat-hint>
          <mat-error>De 0 a 60 minutos</mat-error></mat-form-field>
      </div>
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
  readonly parametros = inject(FormBuilder).nonNullable.group({
    diasAntecedenciaCobranca: [7, [Validators.required, Validators.min(1), Validators.max(30)]],
    diasCarencia: [10, [Validators.required, Validators.min(0), Validators.max(60)]],
  });
  readonly frequencia = inject(FormBuilder).nonNullable.group({
    toleranciaPontualidade: [10, [Validators.required, Validators.min(0), Validators.max(60)]],
  });
  readonly academia = signal<Academia | null>(null);
  readonly erro = signal<string | null>(null);

  constructor() {
    this.api.ativa().subscribe((a) => {
      this.academia.set(a);
      this.form.patchValue(a);
      this.parametros.patchValue(a);
      this.frequencia.patchValue(a);
    });
  }

  salvar(): void {
    if (this.form.invalid || this.parametros.invalid || this.frequencia.invalid) {
      this.form.markAllAsTouched();
      this.parametros.markAllAsTouched();
      this.frequencia.markAllAsTouched();
      return;
    }
    const p = this.parametros.getRawValue();
    this.api.atualizar({ ...(this.form.getRawValue() as DadosAcademia), diasAntecedenciaCobranca: Number(p.diasAntecedenciaCobranca),
      diasCarencia: Number(p.diasCarencia),
      toleranciaPontualidade: Number(this.frequencia.getRawValue().toleranciaPontualidade) }).subscribe({
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
