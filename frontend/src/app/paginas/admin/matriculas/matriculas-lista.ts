import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { mensagemDeErro } from '../../../core/api/erros';
import { FinanceiroApi, MatriculaListada, StatusMatricula } from '../../../core/api/financeiro';
import { COR_STATUS, formatarData, formatarMoeda, ROTULO_MATRICULA, ROTULO_PERIODICIDADE } from '../rotulos';

const TAMANHO = 20;

@Component({
  selector: 'app-matriculas-lista',
  imports: [MatTableModule, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatInputModule, FormsModule, RouterLink],
  template: `
    <header class="topo">
      <h2>Matrículas</h2>
      <a mat-flat-button routerLink="nova">Nova matrícula</a>
    </header>
    <div class="filtros">
      <mat-button-toggle-group [value]="status()" (change)="filtrar($event.value)" aria-label="Filtrar por status">
        @for (f of filtros; track f.rotulo) { <mat-button-toggle [value]="f.valor">{{ f.rotulo }}</mat-button-toggle> }
      </mat-button-toggle-group>
      <mat-form-field class="busca" subscriptSizing="dynamic"><mat-label>Buscar aluno</mat-label>
        <input matInput [ngModel]="busca()" (ngModelChange)="digitou$.next($event)" /></mat-form-field>
    </div>
    @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
    <table mat-table [dataSource]="itens()">
      <ng-container matColumnDef="aluno"><th mat-header-cell *matHeaderCellDef>Aluno</th>
        <td mat-cell *matCellDef="let m"><a [routerLink]="m.id">{{ m.alunoNome }}</a></td></ng-container>
      <ng-container matColumnDef="plano"><th mat-header-cell *matHeaderCellDef>Plano</th>
        <td mat-cell *matCellDef="let m">{{ m.planoNome }} ({{ periodicidade[m.periodicidade] }})</td></ng-container>
      <ng-container matColumnDef="valor"><th mat-header-cell *matHeaderCellDef>Valor</th>
        <td mat-cell *matCellDef="let m">{{ moeda(m.valor) }}</td></ng-container>
      <ng-container matColumnDef="vigente"><th mat-header-cell *matHeaderCellDef>Vigente até</th>
        <td mat-cell *matCellDef="let m">{{ data(m.vigenteAte) }}</td></ng-container>
      <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let m"><span class="chip" [class]="'chip ' + cor[m.status]">{{ rotulo[m.status] }}</span></td></ng-container>
      <tr mat-header-row *matHeaderRowDef="colunas"></tr>
      <tr mat-row *matRowDef="let linha; columns: colunas"></tr>
    </table>
    @if (!itens().length) { <p>Nenhuma matrícula encontrada.</p> }
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
export class MatriculasLista {
  private readonly api = inject(FinanceiroApi);
  readonly colunas = ['aluno', 'plano', 'valor', 'vigente', 'status'];
  readonly filtros: { rotulo: string; valor: StatusMatricula | null }[] = [
    { rotulo: 'Todas', valor: null }, { rotulo: 'Pendentes', valor: 'PENDENTE' }, { rotulo: 'Ativas', valor: 'ATIVA' },
    { rotulo: 'Vencidas', valor: 'VENCIDA' }, { rotulo: 'Suspensas', valor: 'SUSPENSA' }, { rotulo: 'Canceladas', valor: 'CANCELADA' },
  ];
  readonly rotulo = ROTULO_MATRICULA;
  readonly periodicidade = ROTULO_PERIODICIDADE;
  readonly cor = COR_STATUS;
  readonly tamanho = TAMANHO;

  readonly itens = signal<MatriculaListada[]>([]);
  readonly status = signal<StatusMatricula | null>(null);
  readonly busca = signal('');
  readonly pagina = signal(0);
  readonly total = signal(0);
  readonly erro = signal<string | null>(null);
  readonly digitou$ = new Subject<string>();

  constructor() {
    this.digitou$.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((b) => {
      this.busca.set(b);
      this.irPara(0);
    });
    this.irPara(0);
  }

  paginas(): number {
    return Math.max(1, Math.ceil(this.total() / TAMANHO));
  }

  filtrar(status: StatusMatricula | null): void {
    this.status.set(status);
    this.irPara(0);
  }

  irPara(pagina: number): void {
    this.pagina.set(pagina);
    this.api.listarMatriculas({ status: this.status(), busca: this.busca(), pagina, tamanho: TAMANHO }).subscribe({
      next: (p) => {
        this.itens.set(p.itens);
        this.total.set(p.total);
        this.erro.set(null);
      },
      error: (e) => this.erro.set(mensagemDeErro(e)),
    });
  }

  moeda(v: number): string {
    return formatarMoeda(v);
  }

  data(d: string): string {
    return formatarData(d);
  }
}
