import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AlunosApi } from '../../../core/api/cadastros';
import { mensagemDeErro } from '../../../core/api/erros';
import { Aluno, DadosAluno, StatusAluno } from '../../../core/api/modelos';
import { cpfValido, senhaMinima } from '../../../core/validadores';
import { FinanceiroApi, MatriculaDetalhe } from '../../../core/api/financeiro';
import { COR_STATUS, formatarData, ROTULO_MATRICULA, ROTULO_STATUS } from '../rotulos';

@Component({
  selector: 'app-aluno-form',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, RouterLink],
  template: `
    <h2>{{ id ? 'Editar aluno' : 'Novo aluno' }}</h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" class="formulario">
      <div class="grade">
        <mat-form-field><mat-label>Nome completo</mat-label><input matInput formControlName="nome" /></mat-form-field>
        <mat-form-field><mat-label>E-mail (login)</mat-label><input matInput type="email" formControlName="email" />
          @if (id) { <mat-hint>O e-mail de login não pode ser alterado aqui.</mat-hint> }</mat-form-field>
        <mat-form-field><mat-label>CPF</mat-label><input matInput formControlName="cpf" />
          <mat-error>CPF inválido</mat-error></mat-form-field>
        <mat-form-field><mat-label>Telefone</mat-label><input matInput formControlName="telefone" /></mat-form-field>
        <mat-form-field><mat-label>Data de nascimento</mat-label><input matInput type="date" formControlName="dataNascimento" /></mat-form-field>
        <mat-form-field><mat-label>Endereço</mat-label><input matInput formControlName="endereco" /></mat-form-field>
        <mat-form-field><mat-label>Cidade</mat-label><input matInput formControlName="cidade" /></mat-form-field>
        <mat-form-field><mat-label>UF</mat-label><input matInput formControlName="estado" maxlength="2" /></mat-form-field>
        <mat-form-field><mat-label>CEP</mat-label><input matInput formControlName="cep" /></mat-form-field>
        @if (!id) {
          <mat-form-field><mat-label>Senha inicial</mat-label><input matInput type="password" formControlName="senhaInicial" />
            <mat-hint>O aluno troca no primeiro acesso.</mat-hint><mat-error>Mínimo de 8 caracteres</mat-error></mat-form-field>
        }
      </div>
      @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
      <div class="acoes">
        <button mat-flat-button type="submit">Salvar</button>
        <a mat-button routerLink="..">Voltar</a>
      </div>
    </form>

    @if (id) {
      <h3>Matrícula</h3>
      @if (matricula(); as m) {
        <p>
          <span class="chip" [class]="'chip ' + cor[m.status]">{{ rotuloMatricula[m.status] }}</span>
          {{ m.plano.nome }} — vigente até {{ data(m.vigenteAte) }}
          @if (m.inadimplente) { <strong class="erro"> · Inadimplente</strong> }
        </p>
        <a mat-stroked-button [routerLink]="['/admin/matriculas', m.id]">Ver matrícula</a>
      } @else if (semMatricula()) {
        <p>Sem matrícula ativa.</p>
        <a mat-stroked-button routerLink="/admin/matriculas/nova" [queryParams]="{ alunoId: id }">Matricular</a>
      }
      <h3>Frequência</h3>
      <a mat-stroked-button routerLink="/admin/frequencia" [queryParams]="{ alunoId: id }">Ver frequência</a>
    }

    @if (aluno(); as a) {
      <h3>Status</h3>
      <mat-form-field>
        <mat-label>Status do aluno</mat-label>
        <mat-select [value]="a.status" (selectionChange)="alterarStatus($event.value)">
          @for (s of opcoesStatus; track s) { <mat-option [value]="s">{{ rotulos[s] }}</mat-option> }
        </mat-select>
        <mat-hint>Cancelado perde o acesso ao portal desta academia.</mat-hint>
      </mat-form-field>
    }
  `,
  styleUrl: '../listas.scss',
})
export class AlunoForm {
  private readonly api = inject(AlunosApi);
  private readonly router = inject(Router);
  private readonly avisos = inject(MatSnackBar);
  readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id');

  readonly opcoesStatus: StatusAluno[] = ['ATIVO', 'INATIVO', 'CANCELADO'];
  readonly rotulos = ROTULO_STATUS;
  readonly aluno = signal<Aluno | null>(null);
  readonly matricula = signal<MatriculaDetalhe | null>(null);
  readonly semMatricula = signal(false);
  readonly rotuloMatricula = ROTULO_MATRICULA;
  readonly cor = COR_STATUS;
  readonly erro = signal<string | null>(null);

  readonly form = inject(FormBuilder).nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email]],
    cpf: ['', [Validators.required, cpfValido()]],
    telefone: ['', [Validators.required, Validators.maxLength(20)]],
    dataNascimento: ['', Validators.required],
    endereco: ['', [Validators.required, Validators.maxLength(200)]],
    cidade: ['', [Validators.required, Validators.maxLength(100)]],
    estado: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    cep: ['', [Validators.required, Validators.pattern(/^\d{5}-?\d{3}$/)]],
    senhaInicial: ['', [Validators.required, senhaMinima(8)]],
  });

  constructor() {
    if (this.id) {
      inject(FinanceiroApi).matriculaDoAluno(this.id).subscribe({
        next: (m) => this.matricula.set(m),
        error: () => this.semMatricula.set(true),
      });
      this.form.controls.email.disable();
      this.form.controls.senhaInicial.disable();
      this.api.obter(this.id).subscribe((a) => {
        this.aluno.set(a);
        this.form.patchValue(a);
      });
    }
  }

  salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, senhaInicial, ...dados } = this.form.getRawValue();
    const operacao = this.id
      ? this.api.atualizar(this.id, dados as DadosAluno)
      : this.api.criar({ ...(dados as DadosAluno), email, senhaInicial });
    operacao.subscribe({
      next: () => {
        this.avisos.open('Aluno salvo.', 'OK', { duration: 3000 });
        void this.router.navigateByUrl('/admin/alunos');
      },
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }

  alterarStatus(status: StatusAluno): void {
    this.api.alterarStatus(this.id!, status).subscribe({
      next: (a) => {
        this.aluno.set(a);
        this.avisos.open('Status atualizado.', 'OK', { duration: 3000 });
      },
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }

  data(d: string): string {
    return formatarData(d);
  }
}
