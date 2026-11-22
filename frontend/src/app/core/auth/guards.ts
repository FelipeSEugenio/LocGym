import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ContextoService } from '../contexto/contexto.service';
import { AuthService } from './auth.service';
import { Perfil } from './modelos';

export const autenticadoGuard: CanActivateFn = () =>
  inject(AuthService).autenticado() || inject(Router).createUrlTree(['/login']);

/** Enquanto a senha inicial não for trocada, só a tela de troca é acessível. */
export const trocaDeSenhaGuard: CanActivateFn = () =>
  !inject(AuthService).usuario()?.deveTrocarSenha || inject(Router).createUrlTree(['/trocar-senha']);

export function perfilGuard(perfil: Perfil): CanActivateFn {
  return () => inject(ContextoService).atual()?.perfil === perfil || inject(Router).createUrlTree(['/']);
}
