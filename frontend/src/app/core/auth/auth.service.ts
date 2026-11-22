import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import {
  catchError,
  finalize,
  firstValueFrom,
  from,
  map,
  Observable,
  of,
  shareReplay,
  tap,
} from 'rxjs';
import { ContextoService } from '../contexto/contexto.service';
import { CadastroAdministrador, RespostaSessao, UsuarioSessao, Vinculo } from './modelos';

const API = '/api/v1/auth';

/**
 * Sessão do usuário. O access token fica só em memória; o refresh token fica num cookie HttpOnly que o
 * JavaScript não lê — por isso a sessão é retomada com uma chamada de refresh ao carregar a página.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly contexto = inject(ContextoService);

  private token: string | null = null;
  private renovacaoEmAndamento: Observable<boolean> | null = null;

  readonly usuario = signal<UsuarioSessao | null>(null);
  readonly vinculos = signal<Vinculo[]>([]);
  readonly autenticado = computed(() => this.usuario() !== null);

  accessToken(): string | null {
    return this.token;
  }

  login(email: string, senha: string): Observable<void> {
    return this.http.post<RespostaSessao>(`${API}/login`, { email, senha }).pipe(map((r) => this.aplicar(r)));
  }

  cadastrarAdministrador(dados: CadastroAdministrador): Observable<void> {
    return this.http
      .post<RespostaSessao>(`${API}/cadastro-administrador`, dados)
      .pipe(map((r) => this.aplicar(r)));
  }

  trocarSenha(senhaAtual: string, novaSenha: string): Observable<void> {
    return this.http
      .post<RespostaSessao>(`${API}/trocar-senha`, { senhaAtual, novaSenha })
      .pipe(map((r) => this.aplicar(r)));
  }

  /**
   * Renova a sessão pelo cookie. Chamadas simultâneas compartilham a mesma renovação; entre abas, o
   * refresh é serializado com Web Locks (o backend ainda tolera reuso por 10 s).
   */
  renovar(): Observable<boolean> {
    if (!this.renovacaoEmAndamento) {
      const chamada = () =>
        this.http.post<RespostaSessao>(`${API}/refresh`, {}).pipe(
          map((r) => {
            this.aplicar(r);
            return true;
          }),
          catchError(() => {
            this.limparLocal();
            return of(false);
          }),
        );
      const travas = typeof navigator !== 'undefined' ? navigator.locks : undefined;
      const execucao = travas
        ? from(travas.request('locgym-refresh', () => firstValueFrom(chamada())))
        : chamada();
      this.renovacaoEmAndamento = execucao.pipe(
        finalize(() => (this.renovacaoEmAndamento = null)),
        shareReplay(1),
      );
    }
    return this.renovacaoEmAndamento;
  }

  /** Tenta retomar a sessão ao carregar a aplicação; sem sessão, segue deslogado. */
  async iniciar(): Promise<void> {
    await firstValueFrom(this.renovar());
  }

  atualizarVinculos(): Observable<Vinculo[]> {
    return this.http.get<Vinculo[]>('/api/v1/eu/vinculos').pipe(
      tap((v) => {
        this.vinculos.set(v);
        this.contexto.sincronizar(v);
      }),
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${API}/logout`, {}).pipe(
      catchError(() => of(undefined)),
      map(() => this.limparLocal()),
    );
  }

  limparLocal(): void {
    this.token = null;
    this.usuario.set(null);
    this.vinculos.set([]);
    this.contexto.limpar();
  }

  private aplicar(r: RespostaSessao): void {
    this.token = r.accessToken;
    this.usuario.set(r.usuario);
    this.vinculos.set(r.vinculos);
    this.contexto.sincronizar(r.vinculos);
  }
}
