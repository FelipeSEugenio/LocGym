import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import QRCode from 'qrcode';
import { FrequenciaApi, TokenQr } from '../../../core/api/frequencia';

const RENOVAR_A_CADA_MS = 30_000;
/** Validade do token (60 s) menos uma folga para a rede. Contada no relógio do celular, a partir da chegada. */
const VALIDADE_LOCAL_MS = 58_000;

/** QR Code de check-in: renovado a cada 30 s; pausa com a aba oculta. */
@Component({
  selector: 'app-meu-qr',
  template: `
    <h2>Meu QR Code</h2>
    <p>Mostre este código na recepção para registrar sua presença.</p>
    @if (token()) {
      <div class="qr" [innerHTML]="svg()"></div>
      <p class="contagem">Atualiza em {{ restante() > 30 ? restante() - 30 : restante() }} s</p>
    } @else if (!falhou()) {
      <p>Carregando…</p>
    }
    @if (falhou()) { <p class="erro" role="alert">Não foi possível atualizar o QR Code. Verifique sua conexão.</p> }
  `,
  styles: `
    .qr { width: min(320px, 90vw); margin: 16px auto; background: #fff; padding: 8px; }
    .qr :is(svg) { width: 100%; height: auto; display: block; }
    .contagem { text-align: center; color: #555; }
    .erro { color: #b3261e; }
  `,
})
export class MeuQr {
  private readonly api = inject(FrequenciaApi);
  private readonly sanitizer = inject(DomSanitizer);

  readonly token = signal<TokenQr | null>(null);
  readonly svg = signal<SafeHtml>('');
  readonly restante = signal(0);
  readonly falhou = signal(false);

  private renovacao?: ReturnType<typeof setTimeout>;
  /** Fim da validade no relógio deste aparelho — não usa o expiraEm do servidor, que pode estar em outro relógio. */
  private expiraLocal = 0;
  private readonly relogio = setInterval(() => this.atualizarContagem(), 1000);
  private readonly aoMudarVisibilidade = () => (document.hidden ? this.pausar() : this.carregar());

  constructor() {
    document.addEventListener('visibilitychange', this.aoMudarVisibilidade);
    inject(DestroyRef).onDestroy(() => {
      this.pausar();
      clearInterval(this.relogio);
      document.removeEventListener('visibilitychange', this.aoMudarVisibilidade);
    });
    this.carregar();
  }

  private carregar(): void {
    this.pausar();
    this.api.meuQr().subscribe({
      next: (t) => {
        this.falhou.set(false);
        this.expiraLocal = Date.now() + VALIDADE_LOCAL_MS;
        this.token.set(t);
        this.atualizarContagem();
        // SVG gerado localmente pela biblioteca a partir do token (sem conteúdo de terceiros).
        void QRCode.toString(t.token, { type: 'svg', margin: 1, errorCorrectionLevel: 'M' }).then((s) =>
          this.svg.set(this.sanitizer.bypassSecurityTrustHtml(s)),
        );
        this.agendar();
      },
      error: () => {
        this.falhou.set(true);
        this.agendar();
      },
    });
  }

  private agendar(): void {
    if (!document.hidden) {
      this.renovacao = setTimeout(() => this.carregar(), RENOVAR_A_CADA_MS);
    }
  }

  private pausar(): void {
    clearTimeout(this.renovacao);
  }

  private atualizarContagem(): void {
    const t = this.token();
    if (!t) {
      return;
    }
    const segundos = Math.max(0, Math.round((this.expiraLocal - Date.now()) / 1000));
    this.restante.set(segundos);
    if (segundos === 0) {
      this.token.set(null);
      this.svg.set('');
    }
  }
}
