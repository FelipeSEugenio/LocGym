import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { debounceTime, filter, switchMap } from 'rxjs';
import { AlunosApi } from '../../core/api/cadastros';
import { AgendaApi, AulaAberta, Correcao, PresencaManual } from '../../core/api/frequencia';
import { Aluno } from '../../core/api/modelos';
import { agoraEmSaoPaulo, hojeEmSaoPaulo } from '../../core/tempo/hoje';

export type DadosDialogoPresenca = { modo: 'presenca' } | { modo: 'correcao'; data: string; hora: string; horarioId?: string | null };

/**
 * Presença manual (aluno, data, hora e aula) ou correção de um check-in (data, hora, aula e motivo).
 * As aulas oferecidas são as abertas naquele momento; com mais de uma, a escolha é obrigatória.
 */
@Component({
  selector: 'app-dialogo-presenca',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatAutocompleteModule,
    MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ dados.modo === 'presenca' ? 'Registrar presença' : 'Corrigir check-in' }}</h2>
    <mat-dialog-content [formGroup]="form" class="campos">
      @if (dados.modo === 'presenca') {
        <mat-form-field><mat-label>Aluno</mat-label>
          <input matInput [formControl]="buscaAluno" [matAutocomplete]="auto" placeholder="Digite o nome ou CPF" />
          <mat-autocomplete #auto="matAutocomplete" [displayWith]="nomeDoAluno" (optionSelected)="escolherAluno($event.option.value)">
            @for (a of sugestoes(); track a.id) { <mat-option [value]="a">{{ a.nome }}</mat-option> }
          </mat-autocomplete>
          <mat-hint>Só alunos ativos</mat-hint>
        </mat-form-field>
      }
      <mat-form-field><mat-label>Data</mat-label><input matInput type="date" formControlName="data" /></mat-form-field>
      <mat-form-field><mat-label>Hora</mat-label><input matInput type="time" formControlName="hora" /></mat-form-field>
      @if (aulas().length) {
        <mat-form-field><mat-label>Aula</mat-label>
          <mat-select formControlName="horarioId">
            @for (a of aulas(); track a.horarioId) {
              <mat-option [value]="a.horarioId">{{ a.inicio }} {{ a.tipo }} — {{ a.professorNome }}</mat-option>
            }
          </mat-select></mat-form-field>
      } @else {
        <p>Nenhuma aula aberta neste momento: o registro será livre.</p>
      }
      @if (dados.modo === 'correcao') {
        <mat-form-field><mat-label>Motivo</mat-label><textarea matInput formControlName="motivo" maxlength="300"></textarea>
          <mat-error>Informe o motivo</mat-error></mat-form-field>
      }
      @if (aviso()) { <p class="erro" role="alert">{{ aviso() }}</p> }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Voltar</button>
      <button mat-flat-button (click)="confirmar()">{{ dados.modo === 'presenca' ? 'Registrar' : 'Corrigir' }}</button>
    </mat-dialog-actions>
  `,
  styles: `.campos { display: flex; flex-direction: column; min-width: min(420px, 80vw); } .erro { color: #b3261e; }`,
})
export class DialogoPresenca {
  readonly dados = inject<DadosDialogoPresenca>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<DialogoPresenca, PresencaManual | Correcao>);
  private readonly agenda = inject(AgendaApi);
  private readonly alunos = inject(AlunosApi);

  readonly aulas = signal<AulaAberta[]>([]);
  readonly sugestoes = signal<Aluno[]>([]);
  readonly alunoId = signal<string | null>(null);
  readonly aviso = signal<string | null>(null);
  readonly buscaAluno = new FormControl<string | Aluno>('', { nonNullable: true });
  readonly nomeDoAluno = (a: Aluno | string | null): string => (typeof a === 'string' ? a : (a?.nome ?? ''));

  readonly form = inject(FormBuilder).nonNullable.group({
    data: [this.dados.modo === 'correcao' ? this.dados.data : hojeEmSaoPaulo(), Validators.required],
    hora: [this.dados.modo === 'correcao' ? this.dados.hora : agoraEmSaoPaulo(), Validators.required],
    horarioId: [(this.dados.modo === 'correcao' ? this.dados.horarioId : null) ?? null as string | null],
    motivo: [''],
  });

  constructor() {
    this.buscarAulas();
    this.form.controls.data.valueChanges.pipe(debounceTime(250), takeUntilDestroyed()).subscribe(() => this.buscarAulas());
    this.form.controls.hora.valueChanges.pipe(debounceTime(250), takeUntilDestroyed()).subscribe(() => this.buscarAulas());
    this.buscaAluno.valueChanges.pipe(
      debounceTime(250),
      filter((v): v is string => typeof v === 'string' && v.trim().length >= 2),
      switchMap((texto) => this.alunos.buscar(texto.trim(), 0, 10, 'ATIVO')),
      takeUntilDestroyed(),
    ).subscribe((p) => this.sugestoes.set(p.itens));
  }

  escolherAluno(a: Aluno): void {
    this.buscaAluno.setValue(a, { emitEvent: false });
    this.alunoId.set(a.id);
  }

  confirmar(): void {
    const v = this.form.getRawValue();
    if (!v.data || !v.hora) {
      this.aviso.set('Informe a data e a hora.');
      return;
    }
    if (this.aulas().length > 1 && !this.aulas().some((a) => a.horarioId === v.horarioId)) {
      this.aviso.set('Escolha a aula.');
      return;
    }
    const horarioId = this.aulas().length === 1 ? this.aulas()[0].horarioId : this.aulas().length ? v.horarioId : null;
    if (this.dados.modo === 'presenca') {
      const alunoId = this.alunoId();
      if (!alunoId) {
        this.aviso.set('Escolha um aluno da lista.');
        return;
      }
      this.ref.close({ alunoId, data: v.data, hora: v.hora, horarioId });
      return;
    }
    const motivo = v.motivo.trim();
    if (!motivo) {
      this.form.controls.motivo.setErrors({ required: true });
      this.form.controls.motivo.markAsTouched();
      this.aviso.set('Informe o motivo.');
      return;
    }
    this.ref.close({ data: v.data, hora: v.hora, horarioId, motivo });
  }

  private buscarAulas(): void {
    const { data, hora } = this.form.getRawValue();
    if (!data || !hora) {
      this.aulas.set([]);
      return;
    }
    this.agenda.aulasAbertas({ data, hora }).subscribe((lista) => {
      this.aulas.set(lista);
      if (!lista.some((a) => a.horarioId === this.form.controls.horarioId.value)) {
        this.form.controls.horarioId.setValue(lista.length === 1 ? lista[0].horarioId : null);
      }
    });
  }
}
