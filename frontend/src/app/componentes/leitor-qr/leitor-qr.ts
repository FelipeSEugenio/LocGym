import { afterNextRender, Component, DestroyRef, ElementRef, inject, output, viewChild } from '@angular/core';
import { BrowserQRCodeReader, IScannerControls } from '@zxing/browser';

/** Lê QR Codes continuamente pela câmera (traseira, quando houver). Emite o texto de cada leitura. */
@Component({
  selector: 'app-leitor-qr',
  template: `<video #video class="video" muted playsinline></video>`,
  styles: `.video { width: 100%; max-width: 480px; aspect-ratio: 4 / 3; background: #111; border-radius: 8px; }`,
})
export class LeitorQr {
  readonly lido = output<string>();
  readonly semCamera = output<void>();
  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');
  private controles?: IScannerControls;
  private destruido = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.destruido = true;
      this.controles?.stop();
    });
    afterNextRender(() => void this.iniciar());
  }

  private async iniciar(): Promise<void> {
    try {
      const controles = await new BrowserQRCodeReader().decodeFromConstraints(
        { video: { facingMode: 'environment' } },
        this.video().nativeElement,
        (resultado) => {
          if (resultado) this.lido.emit(resultado.getText());
        },
      );
      if (this.destruido) controles.stop();
      else this.controles = controles;
    } catch {
      this.semCamera.emit();
    }
  }
}
