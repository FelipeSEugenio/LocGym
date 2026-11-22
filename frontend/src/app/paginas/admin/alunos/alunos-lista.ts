import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { AlunosApi } from '../../../core/api/cadastros';
import { Aluno, Pagina } from '../../../core/api/modelos';
import { formatarCpf, ROTULO_STATUS } from '../rotulos';

@Component({
  selector: 'app-alunos-lista',
  imports: [MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  template: `
    <header class="topo">
      <h2>Alunos</h2>
      <a mat-flat-button routerLink="novo">Novo aluno</a>
    </header>
    <mat-form-field class="busca">
      <mat-label>Buscar por nome, CPF ou e-mail</mat-label>
      <input matInput name="busca" (input)="buscar($any($event.target).value)" />
    </mat-form-field>
    <table mat-table [dataSource]="resultado().itens">
      <ng-container matColumnDef="nome"><th mat-header-cell *matHeaderCellDef>Nome</th>
        <td mat-cell *matCellDef="let a"><a [routerLink]="a.id">{{ a.nome }}</a></td></ng-container>
      <ng-container matColumnDef="cpf"><th mat-header-cell *matHeaderCellDef>CPF</th>
        <td mat-cell *matCellDef="let a">{{ cpf(a.cpf) }}</td></ng-container>
      <ng-container matColumnDef="email"><th mat-header-cell *matHeaderCellDef>E-mail</th>
        <td mat-cell *matCellDef="let a">{{ a.email }}</td></ng-container>
      <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let a">{{ status[a.status] }}</td></ng-container>
      <tr mat-header-row *matHeaderRowDef="colunas"></tr>
      <tr mat-row *matRowDef="let linha; columns: colunas"></tr>
    </table>
    @if (!resultado().total) { <p>Nenhum aluno encontrado.</p> }
    <mat-paginator [length]="resultado().total" [pageSize]="tamanho" [pageIndex]="pagina"
                   [pageSizeOptions]="[10, 20, 50]" (page)="mudarPagina($event)" />
  `,
  styleUrl: '../listas.scss',
})
export class AlunosLista {
  private readonly api = inject(AlunosApi);
  private readonly busca$ = new Subject<string>();

  readonly colunas = ['nome', 'cpf', 'email', 'status'];
  readonly status = ROTULO_STATUS;
  readonly resultado = signal<Pagina<Aluno>>({ itens: [], pagina: 0, tamanho: 20, total: 0 });
  busca = '';
  pagina = 0;
  tamanho = 20;

  constructor() {
    this.busca$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((b) => {
        this.busca = b;
        this.pagina = 0;
        this.carregar();
      });
    this.carregar();
  }

  cpf(valor: string): string {
    return formatarCpf(valor);
  }

  buscar(valor: string): void {
    this.busca$.next(valor.trim());
  }

  mudarPagina(e: PageEvent): void {
    this.pagina = e.pageIndex;
    this.tamanho = e.pageSize;
    this.carregar();
  }

  private carregar(): void {
    this.api.buscar(this.busca, this.pagina, this.tamanho, undefined).subscribe((p) => this.resultado.set(p));
  }
}
