import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { AlunosApi } from '../../../core/api/cadastros';
import { formatarCpf, ROTULO_STATUS } from '../../admin/rotulos';

@Component({
  selector: 'app-meus-dados',
  imports: [MatCardModule, DatePipe],
  template: `
    @if (aluno(); as a) {
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title>{{ a.nome }}</mat-card-title>
          <mat-card-subtitle>Status na academia: {{ status[a.status] }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <dl>
            <dt>E-mail</dt><dd>{{ a.email }}</dd>
            <dt>CPF</dt><dd>{{ cpf(a.cpf) }}</dd>
            <dt>Telefone</dt><dd>{{ a.telefone }}</dd>
            <dt>Nascimento</dt><dd>{{ a.dataNascimento | date: 'dd/MM/yyyy' }}</dd>
            <dt>Endereço</dt><dd>{{ a.endereco }}, {{ a.cidade }}/{{ a.estado }}</dd>
          </dl>
          <p class="nota">Para alterar seus dados, fale com a recepção da academia.</p>
        </mat-card-content>
      </mat-card>
    } @else {
      <p>Carregando seus dados…</p>
    }
  `,
  styles: `
    dl { display: grid; grid-template-columns: max-content 1fr; gap: 8px 16px; }
    dt { font-weight: 500; }
    dd { margin: 0; }
    .nota { opacity: 0.7; }
  `,
})
export class MeusDados {
  readonly aluno = toSignal(inject(AlunosApi).meusDados());
  readonly status = ROTULO_STATUS;

  cpf(valor: string): string {
    return formatarCpf(valor);
  }
}
