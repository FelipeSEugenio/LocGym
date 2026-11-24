import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DadosDialogoMotivo, DialogoMotivo } from '../../../componentes/dialogo-motivo/dialogo-motivo';
import { DadosDialogoPresenca, DialogoPresenca } from '../../../componentes/dialogo-presenca/dialogo-presenca';
import { CheckinListado, Correcao, FrequenciaApi, PresencaManual, StatusCheckin } from '../../../core/api/frequencia';
import { Pagina } from '../../../core/api/modelos';
import { formatarData, ROTULO_ORIGEM, ROTULO_PONTUALIDADE, ROTULO_STATUS_CHECKIN } from '../rotulos';
import { mensagemDeCheckin } from './mensagens';

/** Histórico de frequência: filtros, presença manual, correção e exclusão. */
@Component({
  selector: 'app-frequencia-lista',
  imports: [FormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, RouterLink],
  template: `
    <header class="topo">
      <h2>Frequência</h2>
      <button mat-flat-button type="button" (click)="registrarPresenca()">Registrar presença</button>
    </header>
    <div class="filtros">
      <mat-form-field><mat-label>De</mat-label><input matInput type="date" [(ngModel)]="de" (change)="filtrar()" /></mat-form-field>
      <mat-form-field><mat-label>Até</mat-label><input matInput type="date" [(ngModel)]="ate" (change)="filtrar()" /></mat-form-field>
      <mat-form-field><mat-label>Situação</mat-label>
        <mat-select [(ngModel)]="status" (selectionChange)="filtrar()">
          @for (s of situacoes; track s) { <mat-option [value]="s">{{ rotuloStatus[s] }}</mat-option> }
        </mat-select></mat-form-field>
      @if (alunoId) { <a mat-button routerLink="/admin/frequencia">Ver todos os alunos</a> }
    </div>
    <table mat-table [dataSource]="resultado().itens">
      <ng-container matColumnDef="quando"><th mat-header-cell *matHeaderCellDef>Data</th>
        <td mat-cell *matCellDef="let c">{{ data(c.data) }} {{ c.hora }}</td></ng-container>
      <ng-container matColumnDef="aluno"><th mat-header-cell *matHeaderCellDef>Aluno</th>
        <td mat-cell *matCellDef="let c"><a [routerLink]="['/admin/alunos', c.alunoId]">{{ c.alunoNome }}</a></td></ng-container>
      <ng-container matColumnDef="aula"><th mat-header-cell *matHeaderCellDef>Aula</th>
        <td mat-cell *matCellDef="let c">{{ c.aula ? c.aula.inicio + ' ' + c.aula.tipo + ' — ' + (c.aula.professorNome ?? '') : 'Livre' }}</td></ng-container>
      <ng-container matColumnDef="origem"><th mat-header-cell *matHeaderCellDef>Origem</th>
        <td mat-cell *matCellDef="let c">{{ rotuloOrigem[c.origem] }}</td></ng-container>
      <ng-container matColumnDef="pontualidade"><th mat-header-cell *matHeaderCellDef>Pontualidade</th>
        <td mat-cell *matCellDef="let c">@if (c.pontualidade) { <span class="chip">{{ rotuloPontualidade[c.pontualidade] }}</span> }</td></ng-container>
      <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Situação</th>
        <td mat-cell *matCellDef="let c">{{ rotuloStatus[c.status] }}@if (c.motivo) { <small> — {{ c.motivo }}</small> }</td></ng-container>
      <ng-container matColumnDef="acoes"><th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let c">
          @if (c.status === 'ATIVO') {
            <button mat-button type="button" (click)="corrigir(c)">Corrigir</button>
            <button mat-button type="button" (click)="excluir(c)">Excluir</button>
          }
        </td></ng-container>
      <tr mat-header-row *matHeaderRowDef="colunas"></tr>
      <tr mat-row *matRowDef="let linha; columns: colunas"></tr>
    </table>
    @if (!resultado().total) { <p>Nenhum registro no período.</p> }
    <mat-paginator [length]="resultado().total" [pageSize]="tamanho" [pageIndex]="pagina"
                   [pageSizeOptions]="[20, 50, 100]" (page)="mudarPagina($event)" />
  `,
  styleUrl: '../listas.scss',
})
export class FrequenciaLista {
  private readonly api = inject(FrequenciaApi);
  private readonly dialogo = inject(MatDialog);
  private readonly avisos = inject(MatSnackBar);

  readonly colunas = ['quando', 'aluno', 'aula', 'origem', 'pontualidade', 'status', 'acoes'];
  readonly situacoes: StatusCheckin[] = ['ATIVO', 'CORRIGIDO', 'EXCLUIDO'];
  readonly rotuloOrigem = ROTULO_ORIGEM;
  readonly rotuloPontualidade = ROTULO_PONTUALIDADE;
  readonly rotuloStatus = ROTULO_STATUS_CHECKIN;
  readonly resultado = signal<Pagina<CheckinListado>>({ itens: [], pagina: 0, tamanho: 20, total: 0 });
  readonly alunoId = inject(ActivatedRoute).snapshot.queryParamMap.get('alunoId');
  de = '';
  ate = '';
  status: StatusCheckin = 'ATIVO';
  pagina = 0;
  tamanho = 20;

  constructor() {
    this.carregar();
  }

  data(iso: string): string {
    return formatarData(iso);
  }

  filtrar(): void {
    this.pagina = 0;
    this.carregar();
  }

  mudarPagina(e: PageEvent): void {
    this.pagina = e.pageIndex;
    this.tamanho = e.pageSize;
    this.carregar();
  }

  registrarPresenca(): void {
    this.dialogo.open<DialogoPresenca, DadosDialogoPresenca, PresencaManual>(DialogoPresenca, { data: { modo: 'presenca' } })
      .afterClosed().subscribe((dados) => {
        if (!dados) return;
        this.api.manual(dados).subscribe({ next: () => this.concluido('Presença registrada.'), error: (e) => this.falhou(e) });
      });
  }

  corrigir(c: CheckinListado): void {
    this.dialogo.open<DialogoPresenca, DadosDialogoPresenca, Correcao>(DialogoPresenca,
      { data: { modo: 'correcao', data: c.data, hora: c.hora, horarioId: c.aula?.horarioId ?? null } })
      .afterClosed().subscribe((dados) => {
        if (!dados) return;
        this.api.corrigir(c.id, dados).subscribe({ next: () => this.concluido('Check-in corrigido.'), error: (e) => this.falhou(e) });
      });
  }

  excluir(c: CheckinListado): void {
    const dados: DadosDialogoMotivo = { titulo: 'Excluir check-in', confirmar: 'Excluir',
      texto: `${c.alunoNome ?? ''} em ${formatarData(c.data)} às ${c.hora}. O registro fica no histórico como excluído.` };
    this.dialogo.open<DialogoMotivo, DadosDialogoMotivo, string>(DialogoMotivo, { data: dados }).afterClosed()
      .subscribe((motivo) => {
        if (!motivo) return;
        this.api.excluir(c.id, motivo).subscribe({ next: () => this.concluido('Check-in excluído.'), error: (e) => this.falhou(e) });
      });
  }

  private concluido(mensagem: string): void {
    this.avisos.open(mensagem, 'OK', { duration: 3000 });
    this.carregar();
  }

  private falhou(e: unknown): void {
    this.avisos.open(mensagemDeCheckin(e), 'OK', { duration: 5000 });
  }

  private carregar(): void {
    this.api.listar({ alunoId: this.alunoId, de: this.de || null, ate: this.ate || null, status: this.status,
      pagina: this.pagina, tamanho: this.tamanho }).subscribe((p) => this.resultado.set(p));
  }
}
