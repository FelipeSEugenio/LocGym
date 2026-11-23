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
