import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PlanosApi } from '../../../core/api/cadastros';
import { mensagemDeErro } from '../../../core/api/erros';
import { DadosPlano, Periodicidade } from '../../../core/api/modelos';
import { ROTULO_PERIODICIDADE } from '../rotulos';

@Component({
  selector: 'app-plano-form',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, RouterLink],
  template: `
    <h2>{{ id ? 'Editar plano' : 'Novo plano' }}</h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" class="formulario">
      <div class="grade">
        <mat-form-field><mat-label>Nome</mat-label><input matInput formControlName="nome" /></mat-form-field>
        <mat-form-field><mat-label>Valor (R$)</mat-label><input matInput type="number" step="0.01" min="0.01" formControlName="valor" />
          <mat-error>Informe um valor maior que zero</mat-error></mat-form-field>
        <mat-form-field><mat-label>Periodicidade</mat-label>
          <mat-select formControlName="periodicidade">
            @for (p of periodicidades; track p) { <mat-option [value]="p">{{ rotulos[p] }}</mat-option> }
          </mat-select></mat-form-field>
      </div>
      <mat-form-field class="formulario"><mat-label>Descrição</mat-label><textarea matInput formControlName="descricao"></textarea></mat-form-field>
      @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
      <div class="acoes">
        <button mat-flat-button type="submit">Salvar</button>
        <a mat-button routerLink="..">Voltar</a>
      </div>
    </form>
  `,
  styleUrl: '../listas.scss',
})
export class PlanoForm {
  private readonly api = inject(PlanosApi);
  private readonly router = inject(Router);
  private readonly avisos = inject(MatSnackBar);
  readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id');
  readonly periodicidades: Periodicidade[] = ['MENSAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL'];
  readonly rotulos = ROTULO_PERIODICIDADE;
  readonly erro = signal<string | null>(null);

  readonly form = inject(FormBuilder).group({
    nome: ['', [Validators.required, Validators.maxLength(100)]],
    descricao: [''],
    valor: [null as number | null, [Validators.required, Validators.min(0.01)]],
    periodicidade: ['MENSAL' as Periodicidade, Validators.required],
  });

  constructor() {
    if (this.id) {
      this.api.obter(this.id).subscribe((p) => this.form.patchValue(p));
    }
  }

  salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const dados: DadosPlano = {
      nome: v.nome ?? '',
      descricao: v.descricao || null,
      valor: Number(v.valor),
      periodicidade: v.periodicidade ?? 'MENSAL',
    };
    (this.id ? this.api.atualizar(this.id, dados) : this.api.criar(dados)).subscribe({
      next: () => {
        this.avisos.open('Plano salvo.', 'OK', { duration: 3000 });
        void this.router.navigateByUrl('/admin/planos');
      },
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
