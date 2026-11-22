import { Injectable, signal } from '@angular/core';
import { Perfil, Vinculo } from '../auth/modelos';

export interface Contexto {
  academiaId: string;
  perfil: Perfil;
}

const CHAVE = 'locgym.contexto';

/** Academia e perfil ativos. Persistido no navegador para sobreviver a recarregamentos. */
@Injectable({ providedIn: 'root' })
export class ContextoService {
  readonly atual = signal<Contexto | null>(ContextoService.ler());

  selecionar(academiaId: string, perfil: Perfil): void {
    const contexto = { academiaId, perfil };
    this.atual.set(contexto);
    localStorage.setItem(CHAVE, JSON.stringify(contexto));
  }

  limpar(): void {
    this.atual.set(null);
    localStorage.removeItem(CHAVE);
  }

  /** Descarta um contexto que não existe mais e escolhe automaticamente quando há um único vínculo. */
  sincronizar(vinculos: Vinculo[]): void {
    const atual = this.atual();
    const valido =
      atual && vinculos.some((v) => v.academiaId === atual.academiaId && v.perfil === atual.perfil);
    if (!valido) {
      this.limpar();
    }
    if (!this.atual() && vinculos.length === 1) {
      this.selecionar(vinculos[0].academiaId, vinculos[0].perfil);
    }
  }

  private static ler(): Contexto | null {
    try {
      const bruto = localStorage.getItem(CHAVE);
      return bruto ? (JSON.parse(bruto) as Contexto) : null;
    } catch {
      return null;
    }
  }
}
