import { Contexto } from '../contexto/contexto.service';
import { UsuarioSessao, Vinculo } from './modelos';

/** Para onde levar o usuário depois de entrar (ou ao abrir a página inicial já logado). */
export function rotaInicial(usuario: UsuarioSessao | null, vinculos: Vinculo[], contexto: Contexto | null): string {
  if (!usuario) {
    return '/';
  }
  if (usuario.deveTrocarSenha) {
    return '/trocar-senha';
  }
  if (contexto) {
    return contexto.perfil === 'ADMIN' ? '/admin' : '/aluno';
  }
  return vinculos.length === 0 ? '/sem-academia' : '/selecionar-academia';
}
