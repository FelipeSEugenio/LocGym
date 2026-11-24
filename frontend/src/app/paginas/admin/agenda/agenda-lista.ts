import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { codigoDoErro, mensagemDeErro } from '../../../core/api/erros';
import { AgendaApi, Horario } from '../../../core/api/frequencia';
import { ROTULO_DIA_SEMANA } from '../rotulos';

/** Grade semanal agrupada por dia, de segunda a domingo. */
@Component({
  selector: 'app-agenda-lista',
  imports: [MatButtonModule, RouterLink],
  template: `
    <header class="topo">
      <h2>Agenda</h2>
      <a mat-flat-button routerLink="novo">Novo horário</a>
    </header>
    @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
    @for (dia of dias(); track dia.numero) {
      <section class="dia">
        <h3>{{ dia.rotulo }}</h3>
        <ul>
          @for (h of dia.horarios; track h.id) {
            <li [class.inativo]="h.status === 'INATIVO'">
              <a [routerLink]="h.id">{{ h.inicio }}–{{ h.fim }} · {{ h.tipo }} · {{ h.professorNome }}</a>
              @if (h.status === 'INATIVO') { <span class="chip">Inativo</span> }
              <button mat-button type="button" (click)="excluir(h)">Excluir</button>
            </li>
          }
        </ul>
      </section>
    } @empty {
      <p>Nenhum horário cadastrado.</p>
    }
  `,
  styleUrl: '../listas.scss',
  styles: `
    .dia ul { list-style: none; padding: 0; margin: 0 0 16px; }
    .dia li { display: flex; align-items: center; gap: 8px; }
    .inativo a { color: #777; }
  `,
})
export class AgendaLista {
  private readonly api = inject(AgendaApi);
  private readonly horarios = signal<Horario[]>([]);
  readonly erro = signal<string | null>(null);

  readonly dias = computed(() =>
    [1, 2, 3, 4, 5, 6, 7]
      .map((numero) => ({
        numero,
        rotulo: ROTULO_DIA_SEMANA[numero],
        horarios: this.horarios()
          .filter((h) => h.diaSemana === numero)
          .sort((a, b) => a.inicio.localeCompare(b.inicio) || a.tipo.localeCompare(b.tipo)),
      }))
      .filter((d) => d.horarios.length > 0),
  );

  constructor() {
    this.carregar();
  }

  excluir(h: Horario): void {
    if (!window.confirm(`Excluir o horário ${h.inicio}–${h.fim} ${h.tipo}?`)) {
      return;
    }
    this.api.excluir(h.id).subscribe({
      next: () => {
        this.erro.set(null);
        this.carregar();
      },
      error: (e) =>
        this.erro.set(codigoDoErro(e) === 'HORARIO_COM_AULAS' ? 'Este horário já teve aulas; inative-o' : mensagemDeErro(e)),
    });
  }

  private carregar(): void {
    this.api.listar().subscribe((l) => this.horarios.set(l));
  }
}
