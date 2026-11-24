import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ProfessoresApi } from '../../../core/api/cadastros';
import { mensagemDeErro } from '../../../core/api/erros';
import { AgendaApi, DadosHorario, StatusHorario } from '../../../core/api/frequencia';
import { ROTULO_DIA_SEMANA } from '../rotulos';

function fimDepoisDoInicio(grupo: AbstractControl): ValidationErrors | null {
  const { inicio, fim } = grupo.value as { inicio: string; fim: string };
  return inicio && fim && fim <= inicio ? { fimAntesDoInicio: true } : null;
}

@Component({
  selector: 'app-horario-form',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, RouterLink],
  template: `
    <h2>{{ id ? 'Editar horário' : 'Novo horário' }}</h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" class="formulario">
      <div class="grade">
        <mat-form-field><mat-label>Dia da semana</mat-label>
          <mat-select formControlName="diaSemana">
            @for (d of dias; track d.numero) { <mat-option [value]="d.numero">{{ d.rotulo }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field><mat-label>Início</mat-label><input matInput type="time" formControlName="inicio" /></mat-form-field>
        <mat-form-field><mat-label>Fim</mat-label><input matInput type="time" formControlName="fim" /></mat-form-field>
        <mat-form-field><mat-label>Tipo de aula</mat-label><input matInput formControlName="tipo" placeholder="Gi, No-Gi, Kids…" /></mat-form-field>
        <mat-form-field><mat-label>Professor</mat-label>
          <mat-select formControlName="professorId">
            @for (p of professores(); track p.id) { <mat-option [value]="p.id">{{ p.nome }}</mat-option> }
          </mat-select></mat-form-field>
        @if (id) {
          <mat-form-field><mat-label>Status</mat-label>
            <mat-select formControlName="status">
              <mat-option value="ATIVO">Ativo</mat-option><mat-option value="INATIVO">Inativo</mat-option>
            </mat-select></mat-form-field>
        }
      </div>
      @if (form.hasError('fimAntesDoInicio')) { <p class="erro" role="alert">O fim deve ser depois do início, no mesmo dia.</p> }
      @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
      <div class="acoes">
        <button mat-flat-button type="submit">Salvar</button>
        <a mat-button routerLink="/admin/agenda">Voltar</a>
      </div>
    </form>
  `,
  styleUrl: '../listas.scss',
})
export class HorarioForm {
  private readonly api = inject(AgendaApi);
  private readonly professoresApi = inject(ProfessoresApi);
  private readonly router = inject(Router);
  private readonly avisos = inject(MatSnackBar);
  readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id');
  readonly dias = [1, 2, 3, 4, 5, 6, 7].map((numero) => ({ numero, rotulo: ROTULO_DIA_SEMANA[numero] }));
  readonly professores = signal<{ id: string; nome: string }[]>([]);
  readonly erro = signal<string | null>(null);

  readonly form = inject(FormBuilder).nonNullable.group(
    {
      professorId: ['', Validators.required],
      diaSemana: [1, Validators.required],
      inicio: ['', Validators.required],
      fim: ['', Validators.required],
      tipo: ['', [Validators.required, Validators.maxLength(60)]],
      status: ['ATIVO' as StatusHorario],
    },
    { validators: fimDepoisDoInicio },
  );

  constructor() {
    this.professoresApi.buscar('', 0, 100).subscribe((p) => {
      const ativos = p.itens.filter((x) => x.status === 'ATIVO').map((x) => ({ id: x.id, nome: x.nome }));
      this.professores.update((atual) => [...ativos, ...atual.filter((a) => !ativos.some((x) => x.id === a.id))]);
    });
    if (this.id) {
      this.api.obter(this.id).subscribe((h) => {
        this.form.patchValue(h);
        // O professor atual continua selecionável mesmo que tenha sido inativado.
        this.professores.update((lista) =>
          lista.some((p) => p.id === h.professorId) ? lista : [...lista, { id: h.professorId, nome: h.professorNome ?? '—' }],
        );
      });
    }
  }

  salvar(): void {
    if (this.form.invalid || !this.form.getRawValue().tipo.trim()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const dados: DadosHorario = { professorId: v.professorId, diaSemana: Number(v.diaSemana), inicio: v.inicio, fim: v.fim,
      tipo: v.tipo.trim(), status: v.status };
    (this.id ? this.api.alterar(this.id, dados) : this.api.criar(dados)).subscribe({
      next: () => {
        this.avisos.open('Horário salvo.', 'OK', { duration: 3000 });
        void this.router.navigateByUrl('/admin/agenda');
      },
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
