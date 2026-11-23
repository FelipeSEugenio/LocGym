import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { DadosDialogoMotivo, DialogoMotivo } from '../../../componentes/dialogo-motivo/dialogo-motivo';
import { DadosDialogoPagamento, DialogoPagamento } from '../../../componentes/dialogo-pagamento/dialogo-pagamento';
import { mensagemDeErro } from '../../../core/api/erros';
import { Cobranca, DadosPagamento, FinanceiroApi, StatusCobranca } from '../../../core/api/financeiro';
import { COR_STATUS, formatarData, formatarMoeda, ROTULO_COBRANCA, ROTULO_FORMA } from '../rotulos';

const TAMANHO = 20;

@Component({
  selector: 'app-cobrancas-lista',
  imports: [MatTableModule, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatInputModule, FormsModule, RouterLink],
  template: `
    <header class="topo"><h2>Cobranças</h2></header>
    <div class="filtros">
      <mat-button-toggle-group [value]="status()" (change)="filtrar($event.value)" aria-label="Filtrar por situação">
        @for (f of filtros; track f.rotulo) { <mat-button-toggle [value]="f.valor">{{ f.rotulo }}</mat-button-toggle> }
      </mat-button-toggle-group>
      <mat-form-field subscriptSizing="dynamic"><mat-label>Vencimento de</mat-label>
        <input matInput type="date" [ngModel]="vencimentoDe()" (ngModelChange)="vencimentoDe.set($event); irPara(0)" /></mat-form-field>
      <mat-form-field subscriptSizing="dynamic"><mat-label>até</mat-label>
        <input matInput type="date" [ngModel]="vencimentoAte()" (ngModelChange)="vencimentoAte.set($event); irPara(0)" /></mat-form-field>
    </div>
    @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
    <table mat-table [dataSource]="itens()">
      <ng-container matColumnDef="aluno"><th mat-header-cell *matHeaderCellDef>Aluno</th>
        <td mat-cell *matCellDef="let c"><a [routerLink]="['/admin/matriculas', c.matriculaId]">{{ c.alunoNome }}</a></td></ng-container>
      <ng-container matColumnDef="vencimento"><th mat-header-cell *matHeaderCellDef>Vencimento</th>
        <td mat-cell *matCellDef="let c">{{ data(c.vencimento) }}</td></ng-container>
      <ng-container matColumnDef="valor"><th mat-header-cell *matHeaderCellDef>Valor</th>
        <td mat-cell *matCellDef="let c">{{ moeda(c.valor) }}</td></ng-container>
      <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Situação</th>
        <td mat-cell *matCellDef="let c">
          <span class="chip" [class]="'chip ' + cor[c.status]">{{ rotulo[c.status] }}</span>
          @if (c.dataPagamento) { <small> em {{ data(c.dataPagamento) }} ({{ forma[c.forma] }})</small> }
        </td></ng-container>
      <ng-container matColumnDef="acoes"><th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let c">
          @if (c.status === 'PENDENTE' || c.status === 'ATRASADO') {
            <button mat-button (click)="pagar(c)">Registrar pagamento</button>
            <button mat-button (click)="abonar(c)">Abonar</button>
          }
        </td></ng-container>
      <tr mat-header-row *matHeaderRowDef="colunas"></tr>
      <tr mat-row *matRowDef="let linha; columns: colunas"></tr>
    </table>
    @if (!itens().length) { <p>Nenhuma cobrança encontrada.</p> }
    @if (total() > tamanho) {
      <div class="paginacao">
        <button mat-button [disabled]="pagina() === 0" (click)="irPara(pagina() - 1)">Anterior</button>
        <span>Página {{ pagina() + 1 }} de {{ paginas() }}</span>
        <button mat-button [disabled]="pagina() + 1 >= paginas()" (click)="irPara(pagina() + 1)">Próxima</button>
      </div>
    }
  `,
  styleUrl: '../listas.scss',
})
export class CobrancasLista {
  private readonly api = inject(FinanceiroApi);
  private readonly dialogo = inject(MatDialog);
  private readonly avisos = inject(MatSnackBar);
  readonly colunas = ['aluno', 'vencimento', 'valor', 'status', 'acoes'];
  readonly filtros: { rotulo: string; valor: StatusCobranca | null }[] = [
    { rotulo: 'Todas', valor: null }, { rotulo: 'Pendentes', valor: 'PENDENTE' }, { rotulo: 'Atrasadas', valor: 'ATRASADO' },
    { rotulo: 'Pagas', valor: 'PAGO' }, { rotulo: 'Abonadas/canceladas', valor: 'CANCELADO' },
  ];
  readonly rotulo = ROTULO_COBRANCA;
  readonly forma = ROTULO_FORMA;
  readonly cor = COR_STATUS;
  readonly tamanho = TAMANHO;

  readonly itens = signal<Cobranca[]>([]);
  readonly status = signal<StatusCobranca | null>(null);
  readonly vencimentoDe = signal<string>('');
  readonly vencimentoAte = signal<string>('');
  readonly pagina = signal(0);
  readonly total = signal(0);
  readonly erro = signal<string | null>(null);

  constructor() {
    this.irPara(0);
  }

  paginas(): number {
    return Math.max(1, Math.ceil(this.total() / TAMANHO));
  }

  filtrar(status: StatusCobranca | null): void {
    this.status.set(status);
    this.irPara(0);
  }

  irPara(pagina: number): void {
    this.pagina.set(pagina);
    this.api.listarCobrancas({ status: this.status(), vencimentoDe: this.vencimentoDe() || null,
      vencimentoAte: this.vencimentoAte() || null, pagina, tamanho: TAMANHO }).subscribe({
      next: (p) => {
        this.itens.set(p.itens);
        this.total.set(p.total);
      },
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }

  pagar(c: Cobranca): void {
    this.dialogo.open<DialogoPagamento, DadosDialogoPagamento, DadosPagamento>(DialogoPagamento,
      { data: { valor: c.valor, vencimento: c.vencimento } }).afterClosed().subscribe((dados) => {
      if (!dados) return;
      this.api.registrarPagamento(c.id, dados).subscribe({
        next: () => this.concluido('Pagamento registrado.'),
        error: (e) => this.erro.set(mensagemDeErro(e)),
      });
    });
  }

  abonar(c: Cobranca): void {
    const dados: DadosDialogoMotivo = { titulo: 'Abonar cobrança', confirmar: 'Abonar',
      texto: 'O período conta como concedido (estende a vigência), sem entrar como valor recebido.' };
    this.dialogo.open<DialogoMotivo, DadosDialogoMotivo, string>(DialogoMotivo, { data: dados }).afterClosed()
      .subscribe((motivo) => {
        if (!motivo) return;
        this.api.abonarCobranca(c.id, motivo).subscribe({
          next: () => this.concluido('Cobrança abonada.'),
          error: (e) => this.erro.set(mensagemDeErro(e)),
        });
      });
  }

  moeda(v: number): string {
    return formatarMoeda(v);
  }

  data(d: string | null): string {
    return formatarData(d);
  }

  private concluido(aviso: string): void {
    this.erro.set(null);
    this.avisos.open(aviso, 'OK', { duration: 3000 });
    this.irPara(this.pagina());
  }
}
