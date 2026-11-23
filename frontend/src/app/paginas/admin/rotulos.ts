export const ROTULO_STATUS: Record<string, string> = {
  ATIVO: 'Ativo',
  INATIVO: 'Inativo',
  CANCELADO: 'Cancelado',
  ATIVA: 'Ativa',
  INATIVA: 'Inativa',
};

export const ROTULO_PERIODICIDADE: Record<string, string> = {
  MENSAL: 'Mensal',
  TRIMESTRAL: 'Trimestral',
  SEMESTRAL: 'Semestral',
  ANUAL: 'Anual',
};

export function formatarCpf(cpf: string): string {
  return cpf.length === 11 ? `${cpf.slice(0, 3)}.${cpf.slice(3, 6)}.${cpf.slice(6, 9)}-${cpf.slice(9)}` : cpf;
}

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export function formatarMoeda(valor: number): string {
  return MOEDA.format(valor);
}

export const ROTULO_MATRICULA: Record<string, string> = {
  PENDENTE: 'Pendente',
  ATIVA: 'Ativa',
  VENCIDA: 'Vencida',
  SUSPENSA: 'Suspensa',
  CANCELADA: 'Cancelada',
};

export const ROTULO_COBRANCA: Record<string, string> = {
  PENDENTE: 'Pendente',
  PAGO: 'Paga',
  ATRASADO: 'Atrasada',
  CANCELADO: 'Abonada/cancelada',
};

export const ROTULO_FORMA: Record<string, string> = {
  DINHEIRO: 'Dinheiro',
  PIX: 'PIX',
  CARTAO_CREDITO: 'Cartão de crédito',
  CARTAO_DEBITO: 'Cartão de débito',
  TRANSFERENCIA: 'Transferência',
};

/** Classe CSS do chip de status (cores em listas.scss). */
export const COR_STATUS: Record<string, string> = {
  ATIVA: 'ok', PAGO: 'ok',
  PENDENTE: 'atencao',
  VENCIDA: 'alerta', ATRASADO: 'alerta',
  SUSPENSA: 'neutro', CANCELADA: 'neutro', CANCELADO: 'neutro',
};

/** AAAA-MM-DD → dd/MM/aaaa (sem passar por Date, para não sofrer com fuso). */
export function formatarData(iso: string | null | undefined): string {
  if (!iso) return '—';
  const [a, m, d] = iso.split('-');
  return `${d}/${m}/${a}`;
}
