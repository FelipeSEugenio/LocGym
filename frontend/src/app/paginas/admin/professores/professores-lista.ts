import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { ProfessoresApi } from '../../../core/api/cadastros';
import { Pagina, Professor } from '../../../core/api/modelos';
import { ROTULO_STATUS } from '../rotulos';

@Component({
  selector: 'app-professores-lista',
  imports: [MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  template: `
    <header class="topo">
      <h2>Professores</h2>
      <a mat-flat-button routerLink="novo">Novo professor</a>
    </header>
    <mat-form-field class="busca">
      <mat-label>Buscar por nome ou e-mail</mat-label>
      <input matInput name="busca" (input)="buscar($any($event.target).value)" />
    </mat-form-field>
    <table mat-table [dataSource]="resultado().itens">
      <ng-container matColumnDef="nome"><th mat-header-cell *matHeaderCellDef>Nome</th>
        <td mat-cell *matCellDef="let p"><a [routerLink]="p.id">{{ p.nome }}</a></td></ng-container>
      <ng-container matColumnDef="especialidade"><th mat-header-cell *matHeaderCellDef>Especialidade</th>
        <td mat-cell *matCellDef="let p">{{ p.especialidade }}</td></ng-container>
      <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let p">{{ status[p.status] }}</td></ng-container>
      <tr mat-header-row *matHeaderRowDef="colunas"></tr>
      <tr mat-row *matRowDef="let linha; columns: colunas"></tr>
    </table>
    @if (!resultado().total) { <p>Nenhum professor encontrado.</p> }
    <mat-paginator [length]="resultado().total" [pageSize]="tamanho" [pageIndex]="pagina"
                   [pageSizeOptions]="[10, 20, 50]" (page)="mudarPagina($event)" />
  `,
  styleUrl: '../listas.scss',
})
export class ProfessoresLista {
  private readonly api = inject(ProfessoresApi);
  private readonly busca$ = new Subject<string>();

  readonly colunas = ['nome', 'especialidade', 'status'];
  readonly status = ROTULO_STATUS;
  readonly resultado = signal<Pagina<Professor>>({ itens: [], pagina: 0, tamanho: 20, total: 0 });
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

  buscar(valor: string): void {
    this.busca$.next(valor.trim());
  }

  mudarPagina(e: PageEvent): void {
    this.pagina = e.pageIndex;
    this.tamanho = e.pageSize;
    this.carregar();
  }

  private carregar(): void {
    this.api.buscar(this.busca, this.pagina, this.tamanho).subscribe((p) => this.resultado.set(p));
  }
}
