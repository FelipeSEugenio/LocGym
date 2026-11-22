import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdministradoresApi } from '../../../core/api/cadastros';
import { mensagemDeErro } from '../../../core/api/erros';
import { Administrador } from '../../../core/api/modelos';
import { senhaMinima } from '../../../core/validadores';

@Component({
  selector: 'app-administradores',
  imports: [ReactiveFormsModule, MatListModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2>Administradores</h2>
    <p>Todos os administradores têm o mesmo acesso. A academia precisa ter pelo menos um.</p>
    <mat-list>
      @for (a of lista(); track a.usuarioId) {
        <mat-list-item>
          <span matListItemTitle>{{ a.nome }}</span>
          <span matListItemLine>{{ a.email }}</span>
          <button mat-button matListItemMeta [disabled]="lista().length === 1" (click)="remover(a)">Remover</button>
        </mat-list-item>
      }
    </mat-list>

    <h3>Convidar administrador</h3>
    <form [formGroup]="form" (ngSubmit)="convidar()" class="formulario">
      <div class="grade">
        <mat-form-field><mat-label>Nome</mat-label><input matInput formControlName="nome" /></mat-form-field>
        <mat-form-field><mat-label>E-mail</mat-label><input matInput type="email" formControlName="email" /></mat-form-field>
        <mat-form-field><mat-label>Senha inicial</mat-label><input matInput type="password" formControlName="senhaInicial" />
          <mat-hint>Usada só se o e-mail ainda não tiver conta.</mat-hint><mat-error>Mínimo de 8 caracteres</mat-error></mat-form-field>
      </div>
      @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
      <button mat-flat-button type="submit">Convidar</button>
    </form>
  `,
  styleUrl: '../listas.scss',
})
export class Administradores {
  private readonly api = inject(AdministradoresApi);
  private readonly avisos = inject(MatSnackBar);
  readonly lista = signal<Administrador[]>([]);
  readonly erro = signal<string | null>(null);

  readonly form = inject(FormBuilder).nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email]],
    senhaInicial: ['', [Validators.required, senhaMinima(8)]],
  });

  constructor() {
    this.carregar();
  }

  convidar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { nome, email, senhaInicial } = this.form.getRawValue();
    this.api.convidar(nome, email, senhaInicial).subscribe({
      next: () => {
        this.form.reset();
        this.erro.set(null);
        this.avisos.open('Administrador adicionado.', 'OK', { duration: 3000 });
        this.carregar();
      },
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }

  remover(a: Administrador): void {
    this.api.remover(a.usuarioId).subscribe({
      next: () => this.carregar(),
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }

  private carregar(): void {
    this.api.listar().subscribe((l) => this.lista.set(l));
  }
}
