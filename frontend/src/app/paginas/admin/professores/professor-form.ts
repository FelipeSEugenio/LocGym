import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ProfessoresApi } from '../../../core/api/cadastros';
import { mensagemDeErro } from '../../../core/api/erros';
import { DadosProfessor, Professor } from '../../../core/api/modelos';
import { ROTULO_STATUS } from '../rotulos';

@Component({
  selector: 'app-professor-form',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  template: `
    <h2>{{ id ? 'Editar professor' : 'Novo professor' }}</h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" class="formulario">
      <div class="grade">
        <mat-form-field><mat-label>Nome</mat-label><input matInput formControlName="nome" /></mat-form-field>
        <mat-form-field><mat-label>E-mail</mat-label><input matInput type="email" formControlName="email" /></mat-form-field>
        <mat-form-field><mat-label>Telefone</mat-label><input matInput formControlName="telefone" /></mat-form-field>
        <mat-form-field><mat-label>Data de nascimento</mat-label><input matInput type="date" formControlName="dataNascimento" /></mat-form-field>
        <mat-form-field><mat-label>Especialidade</mat-label><input matInput formControlName="especialidade" /></mat-form-field>
      </div>
      @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
      <div class="acoes">
        <button mat-flat-button type="submit">Salvar</button>
        @if (professor(); as p) {
          <button mat-stroked-button type="button" (click)="alternarStatus(p)">
            {{ p.status === 'ATIVO' ? 'Inativar' : 'Reativar' }}
          </button>
          <span>Status: {{ rotulos[p.status] }}</span>
        }
        <a mat-button routerLink="..">Voltar</a>
      </div>
    </form>
  `,
  styleUrl: '../listas.scss',
})
export class ProfessorForm {
  private readonly api = inject(ProfessoresApi);
  private readonly router = inject(Router);
  private readonly avisos = inject(MatSnackBar);
  readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id');
  readonly rotulos = ROTULO_STATUS;
  readonly professor = signal<Professor | null>(null);
  readonly erro = signal<string | null>(null);

  readonly form = inject(FormBuilder).group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', Validators.email],
    telefone: [''],
    dataNascimento: [''],
    especialidade: [''],
  });

  constructor() {
    if (this.id) {
      this.api.obter(this.id).subscribe((p) => {
        this.professor.set(p);
        this.form.patchValue(p);
      });
    }
  }

  salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const dados: DadosProfessor = {
      nome: v.nome ?? '',
      email: v.email || null,
      telefone: v.telefone || null,
      dataNascimento: v.dataNascimento || null,
      especialidade: v.especialidade || null,
    };
    (this.id ? this.api.atualizar(this.id, dados) : this.api.criar(dados)).subscribe({
      next: () => {
        this.avisos.open('Professor salvo.', 'OK', { duration: 3000 });
        void this.router.navigateByUrl('/admin/professores');
      },
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }

  alternarStatus(p: Professor): void {
    this.api.alterarStatus(p.id, p.status === 'ATIVO' ? 'INATIVO' : 'ATIVO').subscribe({
      next: (atualizado) => this.professor.set(atualizado),
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
