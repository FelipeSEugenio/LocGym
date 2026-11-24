import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { LeitorQr } from '../../../componentes/leitor-qr/leitor-qr';
import { AgendaApi, AulaAberta, FrequenciaApi, ResultadoCheckin } from '../../../core/api/frequencia';
import { mensagemDeCheckin } from '../frequencia/mensagens';
import { ROTULO_PONTUALIDADE } from '../rotulos';

interface Resultado { tipo: 'ok' | 'alerta' | 'erro'; titulo: string; detalhe: string; aviso?: string }

const IGNORAR_REPETIDO_MS = 5_000;
/** Um QR que já registrou check-in não é reenviado enquanto o token vale (evita "recusado" sobre um sucesso). */
const LEMBRAR_SUCESSO_MS = 60_000;
const RECARREGAR_AULAS_MS = 60_000;

/** Recepção: lê o QR do aluno e registra o check-in na aula escolhida (ou na única aberta). */
@Component({
  selector: 'app-recepcao',
  imports: [ReactiveFormsModule, MatRadioModule, MatFormFieldModule, MatInputModule, MatButtonModule, LeitorQr],
  template: `
    <h2>Recepção</h2>
    <section class="aulas">
      @if (aulas().length === 0) {
        <p>Nenhuma aula aberta agora: os check-ins serão <strong>livres</strong>.</p>
      } @else {
        <p>{{ aulas().length > 1 ? 'Escolha a aula:' : 'Aula aberta:' }}</p>
        <mat-radio-group [value]="selecionada()" (change)="escolher($event.value)" class="opcoes">
          @for (a of aulas(); track a.horarioId) {
            <mat-radio-button [value]="a.horarioId">{{ a.inicio }} {{ a.tipo }} — {{ a.professorNome }}</mat-radio-button>
          }
        </mat-radio-group>
      }
    </section>
    <div class="leitura">
      @if (semCamera()) {
        <p class="aviso" role="status">Câmera indisponível (sem permissão ou sem dispositivo). Cole o código abaixo.</p>
      } @else {
        <app-leitor-qr (lido)="ler($event)" (semCamera)="semCamera.set(true)" />
      }
      @if (resultado(); as r) {
        <div class="resultado" [class]="r.tipo" role="status" aria-live="polite">
          <strong>{{ r.titulo }}</strong>
          <span>{{ r.detalhe }}</span>
          @if (r.aviso) { <span class="alerta">⚠ {{ r.aviso }}</span> }
        </div>
      }
    </div>
    <form class="colar" (ngSubmit)="registrarDigitado()">
      <mat-form-field class="campo"><mat-label>Colar código do QR</mat-label>
        <input matInput [formControl]="tokenDigitado" autocomplete="off" /></mat-form-field>
      <button mat-flat-button type="submit">Registrar</button>
    </form>
  `,
  styles: `
    .opcoes { display: flex; flex-direction: column; gap: 4px; margin-bottom: 16px; }
    .leitura { display: flex; gap: 24px; flex-wrap: wrap; align-items: flex-start; }
    app-leitor-qr { flex: 1 1 320px; max-width: 480px; }
    .resultado { flex: 1 1 260px; display: flex; flex-direction: column; gap: 6px; padding: 16px 20px; border-radius: 8px;
      font-size: 1.2rem; border-left: 8px solid; }
    .resultado strong { font-size: 1.6rem; }
    .ok { background: #e7f5ea; border-color: #1e7b34; color: #0d3d18; }
    .alerta { background: #fff4e0; border-color: #b26a00; color: #4a2c00; }
    .resultado.alerta .alerta { background: none; }
    .erro { background: #fdecea; border-color: #b3261e; color: #5c0f0a; }
    .aviso { background: #fff4e0; padding: 8px 12px; border-radius: 4px; }
    .colar { display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap; margin-top: 16px; }
    .campo { width: min(480px, 100%); }
  `,
})
export class Recepcao {
  private readonly agenda = inject(AgendaApi);
  private readonly frequencia = inject(FrequenciaApi);

  readonly aulas = signal<AulaAberta[]>([]);
  readonly selecionada = signal<string | null>(null);
  /** Só uma escolha do operador vai para o servidor; a seleção automática (aula única) deixa o servidor decidir. */
  private readonly escolhaDoOperador = signal(false);
  readonly resultado = signal<Resultado | null>(null);
  readonly semCamera = signal(false);
  readonly tokenDigitado = new FormControl('', { nonNullable: true });

  private ultimaLeitura: { token: string; em: number } | null = null;
  private readonly registrados = new Map<string, number>();

  constructor() {
    this.carregarAulas();
    const intervalo = setInterval(() => this.carregarAulas(), RECARREGAR_AULAS_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(intervalo));
  }

  ler(token: string): void {
    const agora = Date.now();
    for (const [t, em] of this.registrados) {
      if (agora - em >= LEMBRAR_SUCESSO_MS) this.registrados.delete(t);
    }
    if (this.registrados.has(token)
      || (this.ultimaLeitura?.token === token && agora - this.ultimaLeitura.em < IGNORAR_REPETIDO_MS)) {
      return;
    }
    this.ultimaLeitura = { token, em: agora };
    this.frequencia.checkinQr(token, this.escolhaDoOperador() ? this.selecionada() : null).subscribe({
      next: (r) => {
        this.registrados.set(token, Date.now());
        this.resultado.set(this.sucesso(r));
      },
      error: (e) => this.resultado.set({ tipo: 'erro', titulo: 'Check-in recusado', detalhe: mensagemDeCheckin(e) }),
    });
  }

  escolher(horarioId: string): void {
    this.selecionada.set(horarioId);
    this.escolhaDoOperador.set(true);
  }

  registrarDigitado(): void {
    const token = this.tokenDigitado.value.trim();
    if (token) {
      this.ler(token);
      this.tokenDigitado.setValue('');
    }
  }

  private sucesso(r: ResultadoCheckin): Resultado {
    const detalhe = r.aula
      ? `${r.aula.inicio} ${r.aula.tipo} — ${ROTULO_PONTUALIDADE[r.pontualidade ?? ''] ?? ''}`
      : 'Check-in livre (sem aula aberta)';
    if (r.alerta) {
      const dias = r.alerta.diasDeAtraso;
      return { tipo: 'alerta', titulo: r.aluno.nome, detalhe, aviso: `Cobrança atrasada há ${dias} ${dias === 1 ? 'dia' : 'dias'}` };
    }
    return { tipo: 'ok', titulo: r.aluno.nome, detalhe };
  }

  private carregarAulas(): void {
    this.agenda.aulasAbertas().subscribe((lista) => {
      this.aulas.set(lista);
      const atual = this.selecionada();
      if (this.escolhaDoOperador() && lista.some((a) => a.horarioId === atual)) {
        return; // a escolha do operador persiste enquanto a aula estiver aberta
      }
      this.escolhaDoOperador.set(false);
      this.selecionada.set(lista.length === 1 ? lista[0].horarioId : null);
    });
  }
}
