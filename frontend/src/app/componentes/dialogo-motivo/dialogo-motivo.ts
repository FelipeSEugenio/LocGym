import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface DadosDialogoMotivo { titulo: string; texto?: string; confirmar: string }

/** Pede um motivo obrigatório (até 300 caracteres). Fecha com o motivo, ou com {@code undefined} se cancelado. */
@Component({
  selector: 'app-dialogo-motivo',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ dados.titulo }}</h2>
    <mat-dialog-content>
      @if (dados.texto) { <p>{{ dados.texto }}</p> }
      <mat-form-field class="campo"><mat-label>Motivo</mat-label>
        <textarea matInput [formControl]="motivo" maxlength="300" cdkFocusInitial></textarea>
        <mat-error>Informe um motivo de até 300 caracteres</mat-error>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Voltar</button>
      <button mat-flat-button (click)="confirmar()">{{ dados.confirmar }}</button>
    </mat-dialog-actions>
  `,
  styles: `.campo { width: min(480px, 80vw); }`,
})
export class DialogoMotivo {
  readonly dados = inject<DadosDialogoMotivo>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<DialogoMotivo, string>);
  readonly motivo = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(300),
    Validators.pattern(/\S/)] });

  confirmar(): void {
    if (this.motivo.invalid) {
      this.motivo.markAsTouched();
      return;
    }
    this.ref.close(this.motivo.value.trim());
  }
}
