import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { PlanosApi } from '../../../core/api/cadastros';
import { mensagemDeErro } from '../../../core/api/erros';
import { Plano } from '../../../core/api/modelos';
import { formatarMoeda, ROTULO_PERIODICIDADE, ROTULO_STATUS } from '../rotulos';

@Component({
  selector: 'app-planos-lista',
  imports: [MatTableModule, MatButtonModule, RouterLink],
  template: `
    <header class="topo">
      <h2>Planos</h2>
      <a mat-flat-button routerLink="novo">Novo plano</a>
    </header>
    @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
    <table mat-table [dataSource]="planos()">
      <ng-container matColumnDef="nome"><th mat-header-cell *matHeaderCellDef>Nome</th>
        <td mat-cell *matCellDef="let p"><a [routerLink]="p.id">{{ p.nome }}</a></td></ng-container>
      <ng-container matColumnDef="valor"><th mat-header-cell *matHeaderCellDef>Valor</th>
        <td mat-cell *matCellDef="let p">{{ moeda(p.valor) }}</td></ng-container>
      <ng-container matColumnDef="periodicidade"><th mat-header-cell *matHeaderCellDef>Periodicidade</th>
        <td mat-cell *matCellDef="let p">{{ periodicidade[p.periodicidade] }}</td></ng-container>
      <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let p">{{ status[p.status] }}</td></ng-container>
      <ng-container matColumnDef="acoes"><th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let p">
          <button mat-button (click)="alternar(p)">{{ p.status === 'ATIVO' ? 'Inativar' : 'Reativar' }}</button>
        </td></ng-container>
      <tr mat-header-row *matHeaderRowDef="colunas"></tr>
      <tr mat-row *matRowDef="let linha; columns: colunas"></tr>
    </table>
    @if (!planos().length) { <p>Nenhum plano cadastrado.</p> }
  `,
  styleUrl: '../listas.scss',
})
export class PlanosLista {
  private readonly api = inject(PlanosApi);
  readonly colunas = ['nome', 'valor', 'periodicidade', 'status', 'acoes'];
  readonly status = ROTULO_STATUS;
  readonly periodicidade = ROTULO_PERIODICIDADE;
  readonly planos = signal<Plano[]>([]);
  readonly erro = signal<string | null>(null);

  moeda(valor: number): string {
    return formatarMoeda(valor);
  }

  constructor() {
    this.api.listar().subscribe((p) => this.planos.set(p));
  }

  alternar(plano: Plano): void {
    this.api.alterarStatus(plano.id, plano.status === 'ATIVO' ? 'INATIVO' : 'ATIVO').subscribe({
      next: (atualizado) => this.planos.update((lista) => lista.map((p) => (p.id === atualizado.id ? atualizado : p))),
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }
}
