import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';

export type EstadoBackend = 'online' | 'indisponivel';

interface RespostaHealth {
  status: string;
}

/** Consulta o health check do backend. Nunca propaga erro: falhas viram 'indisponivel'. */
@Injectable({ providedIn: 'root' })
export class SaudeService {
  private readonly http = inject(HttpClient);

  verificar(): Observable<EstadoBackend> {
    return this.http.get<RespostaHealth>('/actuator/health').pipe(
      map((r): EstadoBackend => (r.status === 'UP' ? 'online' : 'indisponivel')),
      catchError(() => of<EstadoBackend>('indisponivel')),
    );
  }
}
