import { HttpErrorResponse } from '@angular/common/http';
import { mensagemDeErro } from '../../../core/api/erros';
import { formatarData, ROTULO_MATRICULA } from '../rotulos';

/** Mensagem curta para a recepção a partir do código do erro do check-in. */
export function mensagemDeCheckin(erro: unknown): string {
  const corpo = erro instanceof HttpErrorResponse ? (erro.error as Record<string, string> | null) : null;
  switch (corpo?.['codigo']) {
    case 'ALUNO_INATIVO': return 'Aluno inativo';
    case 'SEM_MATRICULA': return 'Aluno sem matrícula';
    case 'MATRICULA_NAO_ATIVA': return `Matrícula ${(ROTULO_MATRICULA[corpo['status']] ?? corpo['status'] ?? '').toLowerCase()}`;
    case 'MATRICULA_NAO_INICIADA': return `Matrícula começa em ${formatarData(corpo['dataInicio'])}`;
    case 'CHECKIN_DUPLICADO': return 'Check-in já registrado nesta aula';
    case 'AULA_AMBIGUA': return 'Escolha a aula';
    case 'AULA_NAO_ABERTA': return 'Esta aula não está aberta agora';
    case 'QR_EXPIRADO': return 'QR Code expirado — peça para atualizar';
    case 'QR_INVALIDO': return 'QR Code inválido';
    case 'QR_DE_OUTRA_ACADEMIA': return 'QR Code de outra academia';
    case 'ACADEMIA_INATIVA': return 'Academia inativa';
    default: return mensagemDeErro(erro);
  }
}
