import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { CamposAcademia, formularioAcademia } from '../../componentes/campos-academia/campos-academia';
import { mensagemDeErro } from '../../core/api/erros';
import { AuthService } from '../../core/auth/auth.service';
import { DadosAcademia } from '../../core/auth/modelos';
import { confirmaSenha, senhaMinima } from '../../core/validadores';

/** Cadastro público: o dono cria sua conta e a primeira academia. */
@Component({
  selector: 'app-cadastro-administrador',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink, CamposAcademia],
  template: `
    <main class="centro">
      <mat-card appearance="outlined" class="largo">
        <mat-card-header>
          <mat-card-title>Criar minha academia</mat-card-title>
          <mat-card-subtitle>Você será o administrador e poderá convidar outros depois.</mat-card-subtitle>
        </mat-card-header>
        <form [formGroup]="form" (ngSubmit)="cadastrar()">
          <mat-card-content>
            <h3>Seus dados</h3>
            <mat-form-field><mat-label>Seu nome</mat-label><input matInput formControlName="nome" /></mat-form-field>
            <mat-form-field><mat-label>E-mail</mat-label><input matInput type="email" formControlName="email" autocomplete="username" /></mat-form-field>
            <mat-form-field><mat-label>Senha</mat-label><input matInput type="password" formControlName="senha" autocomplete="new-password" />
              <mat-error>Mínimo de 8 caracteres</mat-error></mat-form-field>
            <mat-form-field><mat-label>Confirme a senha</mat-label><input matInput type="password" formControlName="confirmacao" autocomplete="new-password" /></mat-form-field>
            @if (form.hasError('confirmacaoDiferente') && form.controls.confirmacao.touched) {
              <p class="erro">As senhas não conferem.</p>
            }
            <h3>Academia</h3>
            <app-campos-academia [grupo]="form.controls.academia" />
            @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
          </mat-card-content>
          <mat-card-actions>
            <button mat-flat-button type="submit" [disabled]="enviando()">Criar academia</button>
            <a mat-button routerLink="/login">Já tenho conta</a>
          </mat-card-actions>
        </form>
      </mat-card>
    </main>
  `,
  styleUrl: '../formularios.scss',
})
export class CadastroAdministrador {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group(
    {
      nome: ['', [Validators.required, Validators.maxLength(150)]],
      email: ['', [Validators.required, Validators.email]],
      senha: ['', [Validators.required, senhaMinima(8)]],
      confirmacao: ['', Validators.required],
      academia: formularioAcademia(this.fb),
    },
    { validators: confirmaSenha('senha', 'confirmacao') },
  );
  readonly erro = signal<string | null>(null);
  readonly enviando = signal(false);

  cadastrar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { nome, email, senha, academia } = this.form.getRawValue();
    this.enviando.set(true);
    this.auth.cadastrarAdministrador({ nome, email, senha, academia: academia as DadosAcademia }).subscribe({
      next: () => void this.router.navigateByUrl('/admin'),
      error: (e) => {
        this.enviando.set(false);
        this.erro.set(mensagemDeErro(e));
      },
    });
  }
}
