import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { SaudeService } from '../../core/saude/saude.service';

@Component({
  selector: 'app-inicio',
  imports: [MatCardModule, MatButtonModule, MatIconModule, RouterLink],
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
})
export class Inicio {
  protected readonly estado = toSignal(inject(SaudeService).verificar(), { initialValue: null });
}
