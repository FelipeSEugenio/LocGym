const DATA_SP = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' });

/** Data de negócio de hoje (AAAA-MM-DD) em São Paulo — a mesma regra do backend. */
export function hojeEmSaoPaulo(): string {
  return DATA_SP.format(new Date());
}

/** Soma dias a uma data AAAA-MM-DD, sem fuso (aritmética de calendário). */
export function somarDias(iso: string, dias: number): string {
  const [a, m, d] = iso.split('-').map(Number);
  const data = new Date(Date.UTC(a, m - 1, d + dias));
  return data.toISOString().slice(0, 10);
}

const HORA_SP = new Intl.DateTimeFormat('en-GB', { timeZone: 'America/Sao_Paulo', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });

/** Hora de negócio agora (HH:mm) em São Paulo. */
export function agoraEmSaoPaulo(): string {
  return HORA_SP.format(new Date());
}
