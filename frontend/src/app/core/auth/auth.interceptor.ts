import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { ContextoService } from '../contexto/contexto.service';
import { AuthService } from './auth.service';

/**
 * Adiciona o token e o contexto (academia + perfil) às chamadas da API. Num 401, renova a sessão uma
 * única vez (compartilhada entre requisições simultâneas) e repete; se não der, volta ao login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const auth = inject(AuthService);
  const contexto = inject(ContextoService);
  const router = inject(Router);
  const rotaDeAuth = req.url.startsWith('/api/v1/auth/');

  const preparar = (r: HttpRequest<unknown>) => {
    const headers: Record<string, string> = {};
    const token = auth.accessToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const ctx = contexto.atual();
    if (ctx && !rotaDeAuth) {
      headers['X-Academia-Id'] = ctx.academiaId;
      headers['X-Perfil'] = ctx.perfil;
    }
    return r.clone({ setHeaders: headers });
  };

  return next(preparar(req)).pipe(
    catchError((erro: unknown) => {
      if (!(erro instanceof HttpErrorResponse)) {
        return throwError(() => erro);
      }
      if (erro.status === 401 && !rotaDeAuth) {
        return auth.renovar().pipe(
          switchMap((renovou) => {
            if (renovou) {
              return next(preparar(req));
            }
            auth.limparLocal();
            void router.navigate(['/login']);
            return throwError(() => erro);
          }),
        );
      }
      if (erro.status === 403 && erro.error?.codigo === 'TROCA_DE_SENHA_OBRIGATORIA') {
        void router.navigate(['/trocar-senha']);
      }
      return throwError(() => erro);
    }),
  );
};
