import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { DialogoMotivo, DadosDialogoMotivo } from '../../../componentes/dialogo-motivo/dialogo-motivo';
import { DialogoPagamento, DadosDialogoPagamento } from '../../../componentes/dialogo-pagamento/dialogo-pagamento';
import { mensagemDeErro } from '../../../core/api/erros';
import { CobrancaResumo, DadosPagamento, FinanceiroApi, MatriculaDetalhe } from '../../../core/api/financeiro';
import { hojeEmSaoPaulo } from '../../../core/tempo/hoje';
import { COR_STATUS, formatarData, formatarMoeda, ROTULO_COBRANCA, ROTULO_FORMA, ROTULO_MATRICULA, ROTULO_PERIODICIDADE } from '../rotulos';

@Component({
  selector: 'app-matricula-detalhe',
  imports: [MatCardModule, MatButtonModule, MatTableModule, RouterLink],
  template: `
    @if (m(); as m) {
      <header class="topo">
        <h2>Matrícula de <a [routerLink]="['/admin/alunos', m.aluno.id]">{{ m.aluno.nome }}</a></h2>
        <span class="chip" [class]="'chip ' + cor[m.status]">{{ rotuloMatricula[m.status] }}</span>
      </header>
      <mat-card appearance="outlined" class="resumo">
        <mat-card-content>
          <p><strong>Plano:</strong> {{ m.plano.nome }} ({{ periodicidade[m.periodicidade] }}) — {{ moeda(m.valor) }}</p>
          @if (m.dataInicio > hoje) { <p><strong>Começa em</strong> {{ data(m.dataInicio) }}</p> }
          <p><strong>Vigente até:</strong> {{ m.status === 'PENDENTE' ? 'aguardando o primeiro pagamento' : data(m.vigenteAte) }}</p>
          @if (m.suspensaDesde) { <p><strong>Suspensa desde</strong> {{ data(m.suspensaDesde) }}</p> }
          @if (m.motivo) { <p><strong>Motivo:</strong> {{ m.motivo }}</p> }
          @if (m.inadimplente) { <p class="erro"><strong>Aluno inadimplente</strong> — há cobrança atrasada.</p> }
        </mat-card-content>
        <mat-card-actions class="acoes">
          @if (m.status === 'ATIVA') {
            <button mat-stroked-button (click)="renovar()">Renovar</button>
            <button mat-stroked-button (click)="suspender()">Suspender</button>
          }
          @if (m.status === 'SUSPENSA') { <button mat-stroked-button (click)="reativar()">Reativar</button> }
          @if (m.status !== 'CANCELADA') { <button mat-button class="perigo" (click)="cancelar()">Cancelar matrícula</button> }
        </mat-card-actions>
      </mat-card>
      @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }

      <h3>Cobranças</h3>
      <table mat-table [dataSource]="m.cobrancas">
        <ng-container matColumnDef="vencimento"><th mat-header-cell *matHeaderCellDef>Vencimento</th>
          <td mat-cell *matCellDef="let c">{{ data(c.vencimento) }}</td></ng-container>
        <ng-container matColumnDef="periodo"><th mat-header-cell *matHeaderCellDef>Período</th>
          <td mat-cell *matCellDef="let c">{{ data(c.periodoInicio) }} a {{ data(c.periodoFim) }}</td></ng-container>
        <ng-container matColumnDef="valor"><th mat-header-cell *matHeaderCellDef>Valor</th>
          <td mat-cell *matCellDef="let c">{{ moeda(c.valor) }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Situação</th>
          <td mat-cell *matCellDef="let c">
            <span class="chip" [class]="'chip ' + cor[c.status]">{{ rotuloCobranca[c.status] }}</span>
            @if (c.dataPagamento) { <small> em {{ data(c.dataPagamento) }} ({{ forma[c.forma] }})</small> }
            @if (c.motivoCancelamento) { <small> — {{ c.motivoCancelamento }}</small> }
          </td></ng-container>
        <ng-container matColumnDef="acoes"><th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let c">
            @if (emAberto(c)) {
              <button mat-button [disabled]="c.id !== maisAntigaEmAberto()?.id" (click)="pagar(c)"
                      [attr.title]="c.id !== maisAntigaEmAberto()?.id ? 'Quite primeiro a cobrança mais antiga' : null">Registrar pagamento</button>
              <button mat-button (click)="abonarAte(c)">Abonar até aqui</button>
            }
          </td></ng-container>
        <tr mat-header-row *matHeaderRowDef="colunas"></tr>
        <tr mat-row *matRowDef="let linha; columns: colunas"></tr>
      </table>

      <h3>Períodos de vigência</h3>
      @if (m.periodos.length) {
        <ul>@for (p of m.periodos; track p.inicio) { <li>{{ data(p.inicio) }} a {{ data(p.fim) }}</li> }</ul>
      } @else { <p>Nenhum período pago ainda.</p> }
    }
    <a mat-button routerLink="/admin/matriculas">Voltar</a>
  `,
  styleUrl: '../listas.scss',
  styles: `.resumo { margin: 12px 0; max-width: 760px; } .perigo { color: #b3261e; } h2 a { color: inherit; }`,
})
export class MatriculaDetalhePagina {
  private readonly api = inject(FinanceiroApi);
  private readonly dialogo = inject(MatDialog);
  private readonly avisos = inject(MatSnackBar);
  private readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';

  readonly colunas = ['vencimento', 'periodo', 'valor', 'status', 'acoes'];
  readonly rotuloMatricula = ROTULO_MATRICULA;
  readonly rotuloCobranca = ROTULO_COBRANCA;
  readonly periodicidade = ROTULO_PERIODICIDADE;
  readonly forma = ROTULO_FORMA;
  readonly cor = COR_STATUS;
  readonly hoje = hojeEmSaoPaulo();

  readonly m = signal<MatriculaDetalhe | null>(null);
  readonly erro = signal<string | null>(null);
  readonly maisAntigaEmAberto = computed(() => this.m()?.cobrancas.find((c) => this.emAberto(c)) ?? null);

  constructor() {
    this.carregar();
  }

  emAberto(c: CobrancaResumo): boolean {
    return c.status === 'PENDENTE' || c.status === 'ATRASADO';
  }

  renovar(): void {
    this.executar(this.api.renovar(this.id), 'Cobrança do próximo período gerada.');
  }

  suspender(): void {
    this.comMotivo({ titulo: 'Suspender matrícula', confirmar: 'Suspender',
      texto: 'Durante a suspensão não há cobranças nem atrasos; na reativação o vencimento anda os dias suspensos.' },
    (motivo) => this.executar(this.api.suspender(this.id, motivo), 'Matrícula suspensa.'));
  }

  reativar(): void {
    this.executar(this.api.reativar(this.id), 'Matrícula reativada.');
  }

  cancelar(): void {
    this.comMotivo({ titulo: 'Cancelar matrícula', confirmar: 'Cancelar matrícula',
      texto: 'Cobranças pendentes serão canceladas; as atrasadas continuam devidas. Não é possível desfazer.' },
    (motivo) => this.executar(this.api.cancelar(this.id, motivo), 'Matrícula cancelada.'));
  }

  abonarAte(c: CobrancaResumo): void {
    this.comMotivo({ titulo: 'Abonar cobranças', confirmar: 'Abonar',
      texto: `Abona todas as cobranças em aberto até ${formatarData(c.vencimento)} (inclusive). Os períodos contam como concedidos.` },
    (motivo) => this.executar(this.api.abonar(this.id, c.id, motivo), 'Cobranças abonadas.'));
  }

  pagar(c: CobrancaResumo): void {
    const dados: DadosDialogoPagamento = { valor: c.valor, vencimento: c.vencimento };
    this.dialogo.open<DialogoPagamento, DadosDialogoPagamento, DadosPagamento>(DialogoPagamento, { data: dados })
      .afterClosed().subscribe((pagamento) => {
        if (!pagamento) return;
        this.api.registrarPagamento(c.id, pagamento).subscribe({
          next: () => {
            this.avisos.open('Pagamento registrado.', 'OK', { duration: 3000 });
            this.carregar();
          },
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

  private comMotivo(dados: DadosDialogoMotivo, acao: (motivo: string) => void): void {
    this.dialogo.open<DialogoMotivo, DadosDialogoMotivo, string>(DialogoMotivo, { data: dados })
      .afterClosed().subscribe((motivo) => {
        if (motivo) acao(motivo);
      });
  }

  private executar(chamada: Observable<MatriculaDetalhe>, aviso: string): void {
    chamada.subscribe({
      next: (m) => {
        this.m.set(m);
        this.erro.set(null);
        this.avisos.open(aviso, 'OK', { duration: 3000 });
      },
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }

  private carregar(): void {
    this.api.matricula(this.id).subscribe({
      next: (m) => this.m.set(m),
      error: (e) => this.erro.set(mensagemDeErro(e, 'Matrícula não encontrada.')),
    });
  }
}
