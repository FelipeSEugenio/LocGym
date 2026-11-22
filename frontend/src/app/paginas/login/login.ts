import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { mensagemDeErro } from '../../core/api/erros';
import { AuthService } from '../../core/auth/auth.service';
import { rotaInicial } from '../../core/auth/navegacao';
import { ContextoService } from '../../core/contexto/contexto.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  template: `
    <main class="centro">
      <mat-card appearance="outlined">
        <mat-card-header><mat-card-title>Entrar no LocGym</mat-card-title></mat-card-header>
        <form [formGroup]="form" (ngSubmit)="entrar()">
          <mat-card-content>
            <mat-form-field><mat-label>E-mail</mat-label><input matInput type="email" formControlName="email" autocomplete="username" /></mat-form-field>
            <mat-form-field><mat-label>Senha</mat-label><input matInput type="password" formControlName="senha" autocomplete="current-password" /></mat-form-field>
            @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
          </mat-card-content>
          <mat-card-actions>
            <button mat-flat-button type="submit" [disabled]="enviando()">Entrar</button>
            <a mat-button routerLink="/criar-academia">Criar minha academia</a>
          </mat-card-actions>
        </form>
      </mat-card>
    </main>
  `,
  styleUrl: '../formularios.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly contexto = inject(ContextoService);
  private readonly router = inject(Router);

  readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    senha: ['', Validators.required],
  });
  readonly erro = signal<string | null>(null);
  readonly enviando = signal(false);

  entrar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    const { email, senha } = this.form.getRawValue();
    this.auth.login(email, senha).subscribe({
      next: () => {
        this.enviando.set(false);
        void this.router.navigateByUrl(rotaInicial(this.auth.usuario(), this.auth.vinculos(), this.contexto.atual()));
      },
      error: (e) => {
        this.enviando.set(false);
        this.erro.set(mensagemDeErro(e, 'E-mail ou senha inválidos'));
      },
    });
  }
}
