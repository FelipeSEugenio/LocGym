import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, FormControl, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, filter, switchMap } from 'rxjs';
import { AlunosApi, PlanosApi } from '../../../core/api/cadastros';
import { mensagemDeErro } from '../../../core/api/erros';
import { FinanceiroApi } from '../../../core/api/financeiro';
import { Aluno, Plano } from '../../../core/api/modelos';
import { hojeEmSaoPaulo, somarDias } from '../../../core/tempo/hoje';
import { formatarMoeda, ROTULO_PERIODICIDADE } from '../rotulos';

const JANELA_DIAS = 30;

function dentroDaJanela(controle: AbstractControl<string>): ValidationErrors | null {
  const hoje = hojeEmSaoPaulo();
  const v = controle.value;
  return v && (v < somarDias(hoje, -JANELA_DIAS) || v > somarDias(hoje, JANELA_DIAS)) ? { janela: true } : null;
}

@Component({
  selector: 'app-nova-matricula',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatAutocompleteModule,
    MatButtonModule, RouterLink],
  template: `
    <h2>Nova matrícula</h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" class="formulario">
      <div class="grade">
        <mat-form-field><mat-label>Aluno</mat-label>
          <input matInput [formControl]="buscaAluno" [matAutocomplete]="auto" placeholder="Digite o nome ou CPF" />
          <mat-autocomplete #auto="matAutocomplete" [displayWith]="nomeDoAluno" (optionSelected)="escolherAluno($event.option.value)">
            @for (a of sugestoes(); track a.id) { <mat-option [value]="a">{{ a.nome }}</mat-option> }
          </mat-autocomplete>
          <mat-hint>Só alunos ativos</mat-hint>
        </mat-form-field>
        <mat-form-field><mat-label>Plano</mat-label>
          <mat-select formControlName="planoId">
            @for (p of planos(); track p.id) {
              <mat-option [value]="p.id">{{ p.nome }} — {{ moeda(p.valor) }} ({{ periodicidade[p.periodicidade] }})</mat-option>
            }
          </mat-select>
          <mat-error>Escolha um plano</mat-error>
        </mat-form-field>
        <mat-form-field><mat-label>Data de início</mat-label>
          <input matInput type="date" formControlName="dataInicio" />
          @if (form.controls.dataInicio.hasError('janela')) { <mat-error>Até 30 dias antes ou depois de hoje</mat-error> }
          @else { <mat-error>Informe a data de início</mat-error> }
        </mat-form-field>
        <mat-form-field><mat-label>Valor (R$)</mat-label>
          <input matInput type="number" step="0.01" min="0.01" formControlName="valor" />
          <mat-hint>Pré-preenchido com o valor do plano; ajuste para dar desconto</mat-hint>
          <mat-error>Informe um valor maior que zero</mat-error>
        </mat-form-field>
      </div>
      @if (faltaAluno()) { <p class="erro" role="alert">Escolha um aluno da lista.</p> }
      @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
      <div class="acoes">
        <button mat-flat-button type="submit">Matricular</button>
        <a mat-button routerLink="..">Voltar</a>
      </div>
    </form>
  `,
  styleUrl: '../listas.scss',
})
export class NovaMatricula {
  private readonly financeiro = inject(FinanceiroApi);
  private readonly alunos = inject(AlunosApi);
  private readonly router = inject(Router);
  private readonly avisos = inject(MatSnackBar);
  readonly periodicidade = ROTULO_PERIODICIDADE;

  private readonly todosOsPlanos = signal<Plano[]>([]);
  readonly planos = computed(() => this.todosOsPlanos().filter((p) => p.status === 'ATIVO'));
  readonly sugestoes = signal<Aluno[]>([]);
  readonly erro = signal<string | null>(null);
  readonly faltaAluno = signal(false);
  readonly buscaAluno = new FormControl<string | Aluno>('', { nonNullable: true });

  readonly form = inject(FormBuilder).group({
    alunoId: ['', Validators.required],
    planoId: ['', Validators.required],
    dataInicio: [hojeEmSaoPaulo(), [Validators.required, dentroDaJanela]],
    valor: [null as number | null, [Validators.required, Validators.min(0.01)]],
  });

  readonly nomeDoAluno = (a: Aluno | string | null): string => (typeof a === 'string' ? a : (a?.nome ?? ''));

  constructor() {
    inject(PlanosApi).listar().subscribe((p) => this.todosOsPlanos.set(p));
    this.form.controls.planoId.valueChanges.pipe(takeUntilDestroyed()).subscribe((id) => {
      const plano = this.todosOsPlanos().find((p) => p.id === id);
      if (plano) this.form.controls.valor.setValue(plano.valor);
    });
    this.buscaAluno.valueChanges.pipe(
      debounceTime(250),
      filter((v): v is string => typeof v === 'string' && v.trim().length >= 2),
      switchMap((texto) => this.alunos.buscar(texto.trim(), 0, 10, 'ATIVO')),
      takeUntilDestroyed(),
    ).subscribe((p) => this.sugestoes.set(p.itens));
    const alunoId = inject(ActivatedRoute).snapshot.queryParamMap.get('alunoId');
    if (alunoId) {
      this.alunos.obter(alunoId).subscribe((a) => this.escolherAluno(a));
    }
  }

  escolherAluno(aluno: Aluno): void {
    this.buscaAluno.setValue(aluno, { emitEvent: false });
    this.form.controls.alunoId.setValue(aluno.id);
    this.faltaAluno.set(false);
  }

  moeda(v: number): string {
    return formatarMoeda(v);
  }

  salvar(): void {
    this.faltaAluno.set(!this.form.controls.alunoId.value);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.financeiro.criarMatricula({ alunoId: v.alunoId ?? '', planoId: v.planoId ?? '', dataInicio: v.dataInicio ?? '',
      valor: v.valor === null ? null : Number(v.valor) }).subscribe({
      next: (m) => {
        this.avisos.open('Matrícula criada. Registre o pagamento da primeira cobrança para ativá-la.', 'OK', { duration: 5000 });
        void this.router.navigateByUrl(`/admin/matriculas/${m.id}`);
      },
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
