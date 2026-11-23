import { Component, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { DadosPagamento, FormaPagamento } from '../../core/api/financeiro';
import { hojeEmSaoPaulo } from '../../core/tempo/hoje';
import { formatarData, formatarMoeda, ROTULO_FORMA } from '../../paginas/admin/rotulos';

export interface DadosDialogoPagamento { valor: number; vencimento: string }

function naoFutura(controle: AbstractControl<string>): ValidationErrors | null {
  return controle.value && controle.value > hojeEmSaoPaulo() ? { futura: true } : null;
}

/** Registro manual de pagamento (valor integral). Fecha com os dados, ou {@code undefined} se cancelado. */
@Component({
  selector: 'app-dialogo-pagamento',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Registrar pagamento</h2>
    <mat-dialog-content [formGroup]="form">
      <p>Cobrança de {{ moeda(dados.valor) }} com vencimento em {{ data(dados.vencimento) }}.</p>
      <div class="campos">
        <mat-form-field><mat-label>Data do pagamento</mat-label>
          <input matInput type="date" formControlName="dataPagamento" [max]="hoje" />
          <mat-error>A data não pode ser futura</mat-error></mat-form-field>
        <mat-form-field><mat-label>Forma de pagamento</mat-label>
          <mat-select formControlName="forma">
            @for (f of formas; track f) { <mat-option [value]="f">{{ rotulos[f] }}</mat-option> }
          </mat-select>
          <mat-error>Informe a forma de pagamento</mat-error></mat-form-field>
      </div>
      <mat-form-field class="largo"><mat-label>Observações</mat-label>
        <textarea matInput formControlName="observacoes" maxlength="500"></textarea></mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Voltar</button>
      <button mat-flat-button (click)="confirmar()">Registrar</button>
    </mat-dialog-actions>
  `,
  styles: `.campos { display: flex; gap: 16px; flex-wrap: wrap; } .largo { width: 100%; }`,
})
export class DialogoPagamento {
  readonly dados = inject<DadosDialogoPagamento>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<DialogoPagamento, DadosPagamento>);
  readonly hoje = hojeEmSaoPaulo();
  readonly formas: FormaPagamento[] = ['PIX', 'DINHEIRO', 'CARTAO_DEBITO', 'CARTAO_CREDITO', 'TRANSFERENCIA'];
  readonly rotulos = ROTULO_FORMA;

  readonly form = inject(FormBuilder).nonNullable.group({
    dataPagamento: [this.hoje, [Validators.required, naoFutura]],
    forma: ['' as FormaPagamento | '', Validators.required],
    observacoes: ['', Validators.maxLength(500)],
  });

  moeda(v: number): string {
    return formatarMoeda(v);
  }

  data(d: string): string {
    return formatarData(d);
  }

  confirmar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.ref.close({ dataPagamento: v.dataPagamento, forma: v.forma as FormaPagamento,
      observacoes: v.observacoes.trim() || null });
  }
}
