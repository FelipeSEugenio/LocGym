import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { mensagemDeErro } from '../../core/api/erros';
import { AuthService } from '../../core/auth/auth.service';
import { rotaInicial } from '../../core/auth/navegacao';
import { ContextoService } from '../../core/contexto/contexto.service';
import { confirmaSenha, senhaMinima } from '../../core/validadores';

@Component({
  selector: 'app-trocar-senha',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <main class="centro">
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title>Defina sua senha</mat-card-title>
          <mat-card-subtitle>Por segurança, troque a senha recebida antes de continuar.</mat-card-subtitle>
        </mat-card-header>
        <form [formGroup]="form" (ngSubmit)="salvar()">
          <mat-card-content>
            <mat-form-field><mat-label>Senha atual</mat-label><input matInput type="password" formControlName="senhaAtual" autocomplete="current-password" /></mat-form-field>
            <mat-form-field><mat-label>Nova senha</mat-label><input matInput type="password" formControlName="novaSenha" autocomplete="new-password" />
              <mat-error>Mínimo de 8 caracteres</mat-error></mat-form-field>
            <mat-form-field><mat-label>Confirme a nova senha</mat-label><input matInput type="password" formControlName="confirmacao" autocomplete="new-password" /></mat-form-field>
            @if (form.hasError('confirmacaoDiferente') && form.controls.confirmacao.touched) {
              <p class="erro">As senhas não conferem.</p>
            }
            @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
          </mat-card-content>
          <mat-card-actions><button mat-flat-button type="submit">Salvar senha</button></mat-card-actions>
        </form>
      </mat-card>
    </main>
  `,
  styleUrl: '../formularios.scss',
})
export class TrocarSenha {
  private readonly auth = inject(AuthService);
  private readonly contexto = inject(ContextoService);
  private readonly router = inject(Router);

  readonly form = inject(FormBuilder).nonNullable.group(
    {
      senhaAtual: ['', Validators.required],
      novaSenha: ['', [Validators.required, senhaMinima(8)]],
      confirmacao: ['', Validators.required],
    },
    { validators: confirmaSenha('novaSenha', 'confirmacao') },
  );
  readonly erro = signal<string | null>(null);

  salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { senhaAtual, novaSenha } = this.form.getRawValue();
    this.auth.trocarSenha(senhaAtual, novaSenha).subscribe({
      next: () =>
        void this.router.navigateByUrl(rotaInicial(this.auth.usuario(), this.auth.vinculos(), this.contexto.atual())),
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
