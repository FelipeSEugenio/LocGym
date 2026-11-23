import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { mensagemDeErro } from '../../../core/api/erros';
import { FinanceiroApi, Inadimplente } from '../../../core/api/financeiro';
import { COR_STATUS, formatarCpf, formatarData, formatarMoeda, ROTULO_MATRICULA } from '../rotulos';

const TAMANHO = 20;

@Component({
  selector: 'app-inadimplencia',
  imports: [MatTableModule, MatButtonModule, MatFormFieldModule, MatInputModule, FormsModule, RouterLink],
  template: `
    <header class="topo"><h2>Inadimplência</h2></header>
    <p>Alunos com ao menos uma cobrança atrasada, do atraso mais antigo para o mais recente.</p>
    <mat-form-field class="busca" subscriptSizing="dynamic"><mat-label>Buscar aluno</mat-label>
      <input matInput (ngModelChange)="digitou$.next($event)" [ngModel]="busca()" /></mat-form-field>
    @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
    <table mat-table [dataSource]="itens()">
      <ng-container matColumnDef="aluno"><th mat-header-cell *matHeaderCellDef>Aluno</th>
        <td mat-cell *matCellDef="let i">
          @if (i.matriculaId) { <a [routerLink]="['/admin/matriculas', i.matriculaId]">{{ i.nome }}</a> } @else { {{ i.nome }} }
          <br /><small>{{ cpf(i.cpf) }}</small>
        </td></ng-container>
      <ng-container matColumnDef="total"><th mat-header-cell *matHeaderCellDef>Em atraso</th>
        <td mat-cell *matCellDef="let i">{{ moeda(i.totalEmAtraso) }} <small>({{ i.cobrancasAtrasadas }} cobrança(s))</small></td></ng-container>
      <ng-container matColumnDef="desde"><th mat-header-cell *matHeaderCellDef>Vencida desde</th>
        <td mat-cell *matCellDef="let i">{{ data(i.vencimentoMaisAntigo) }}</td></ng-container>
      <ng-container matColumnDef="dias"><th mat-header-cell *matHeaderCellDef>Atraso</th>
        <td mat-cell *matCellDef="let i">{{ i.diasDeAtraso }} dias</td></ng-container>
      <ng-container matColumnDef="matricula"><th mat-header-cell *matHeaderCellDef>Matrícula</th>
        <td mat-cell *matCellDef="let i">
          @if (i.statusMatricula) { <span class="chip" [class]="'chip ' + cor[i.statusMatricula]">{{ rotulo[i.statusMatricula] }}</span> }
        </td></ng-container>
      <tr mat-header-row *matHeaderRowDef="colunas"></tr>
      <tr mat-row *matRowDef="let linha; columns: colunas"></tr>
    </table>
    @if (!itens().length) { <p>Nenhum aluno inadimplente.</p> }
    @if (total() > tamanho) {
      <div class="paginacao">
        <button mat-button [disabled]="pagina() === 0" (click)="irPara(pagina() - 1)">Anterior</button>
        <span>Página {{ pagina() + 1 }}</span>
        <button mat-button [disabled]="(pagina() + 1) * tamanho >= total()" (click)="irPara(pagina() + 1)">Próxima</button>
      </div>
    }
  `,
  styleUrl: '../listas.scss',
})
export class InadimplenciaPagina {
  private readonly api = inject(FinanceiroApi);
  readonly colunas = ['aluno', 'total', 'desde', 'dias', 'matricula'];
  readonly rotulo = ROTULO_MATRICULA;
  readonly cor = COR_STATUS;
  readonly tamanho = TAMANHO;
  readonly itens = signal<Inadimplente[]>([]);
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

  irPara(pagina: number): void {
    this.pagina.set(pagina);
    this.api.inadimplentes(this.busca(), pagina, TAMANHO).subscribe({
      next: (p) => {
        this.itens.set(p.itens);
        this.total.set(p.total);
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

  cpf(c: string): string {
    return formatarCpf(c);
  }
}
