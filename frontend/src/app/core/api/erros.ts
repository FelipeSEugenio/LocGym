import { HttpErrorResponse } from '@angular/common/http';
import { Problema } from '../auth/modelos';

/** Mensagem amigável a partir do Problem Details da API. */
export function mensagemDeErro(erro: unknown, padrao = 'Não foi possível concluir a operação.'): string {
  if (erro instanceof HttpErrorResponse) {
    const problema = erro.error as Problema | null;
    if (problema?.erros?.length) {
      return problema.erros.map((e) => e.mensagem).join(' ');
    }
    if (problema?.detail) {
      return problema.detail;
    }
    if (erro.status === 0) {
      return 'Sem conexão com o servidor.';
    }
  }
  return padrao;
}

export function codigoDoErro(erro: unknown): string | undefined {
  return erro instanceof HttpErrorResponse ? (erro.error as Problema | null)?.codigo : undefined;
}
