import { Component, input } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { DadosAcademia } from '../../core/auth/modelos';

/** Formulário dos dados cadastrais de uma academia (mesmas regras do backend). */
export function formularioAcademia(fb: FormBuilder, valor?: Partial<DadosAcademia>): FormGroup {
  return fb.nonNullable.group({
    nome: [valor?.nome ?? '', [Validators.required, Validators.maxLength(150)]],
    cnpj: [valor?.cnpj ?? '', [Validators.required, Validators.pattern(/^\d{2}\.?\d{3}\.?\d{3}\/?\d{4}-?\d{2}$/)]],
    endereco: [valor?.endereco ?? '', [Validators.required, Validators.maxLength(200)]],
    cidade: [valor?.cidade ?? '', [Validators.required, Validators.maxLength(100)]],
    estado: [valor?.estado ?? '', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    cep: [valor?.cep ?? '', [Validators.required, Validators.pattern(/^\d{5}-?\d{3}$/)]],
    telefone: [valor?.telefone ?? '', [Validators.required, Validators.maxLength(20)]],
    email: [valor?.email ?? '', [Validators.required, Validators.email]],
  });
}

@Component({
  selector: 'app-campos-academia',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  template: `
    <div [formGroup]="grupo()" class="grade">
      <mat-form-field class="largo"><mat-label>Nome da academia</mat-label><input matInput formControlName="nome" /></mat-form-field>
      <mat-form-field><mat-label>CNPJ</mat-label><input matInput formControlName="cnpj" placeholder="00.000.000/0000-00" />
        <mat-error>CNPJ inválido</mat-error></mat-form-field>
      <mat-form-field><mat-label>Telefone</mat-label><input matInput formControlName="telefone" /></mat-form-field>
      <mat-form-field class="largo"><mat-label>Endereço</mat-label><input matInput formControlName="endereco" /></mat-form-field>
      <mat-form-field><mat-label>Cidade</mat-label><input matInput formControlName="cidade" /></mat-form-field>
      <mat-form-field><mat-label>UF</mat-label><input matInput formControlName="estado" maxlength="2" />
        <mat-error>UF inválida</mat-error></mat-form-field>
      <mat-form-field><mat-label>CEP</mat-label><input matInput formControlName="cep" />
        <mat-error>CEP inválido</mat-error></mat-form-field>
      <mat-form-field><mat-label>E-mail de contato</mat-label><input matInput type="email" formControlName="email" /></mat-form-field>
    </div>
  `,
  styles: `
    .grade { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 0 16px; }
    .largo { grid-column: span 2; }
    @media (max-width: 480px) { .largo { grid-column: auto; } }
  `,
})
export class CamposAcademia {
  readonly grupo = input.required<FormGroup>();
}
