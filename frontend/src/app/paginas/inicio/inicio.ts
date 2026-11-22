import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { rotaInicial } from '../../core/auth/navegacao';
import { ContextoService } from '../../core/contexto/contexto.service';
import { SaudeService } from '../../core/saude/saude.service';

@Component({
  selector: 'app-inicio',
  imports: [MatCardModule, MatButtonModule, MatIconModule, RouterLink],
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
})
export class Inicio {
  protected readonly estado = toSignal(inject(SaudeService).verificar(), { initialValue: null });

  constructor() {
    const auth = inject(AuthService);
    if (auth.autenticado()) {
      void inject(Router).navigateByUrl(rotaInicial(auth.usuario(), auth.vinculos(), inject(ContextoService).atual()));
    }
  }
}
