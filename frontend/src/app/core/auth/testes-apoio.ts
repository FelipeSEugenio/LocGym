import { RespostaSessao, Vinculo } from './modelos';

export const VINCULO_ADMIN: Vinculo = {
  academiaId: 'a1',
  academiaNome: 'Academia Um',
  academiaStatus: 'ATIVA',
  perfil: 'ADMIN',
};

export const VINCULO_ALUNO: Vinculo = {
  academiaId: 'a2',
  academiaNome: 'Academia Dois',
  academiaStatus: 'ATIVA',
  perfil: 'ALUNO',
};

export function sessao(parcial: Partial<RespostaSessao> = {}): RespostaSessao {
  return {
    accessToken: 'token-1',
    expiraEm: '2030-01-01T00:00:00Z',
    usuario: { id: 'u1', nome: 'Ana', email: 'ana@x.com', deveTrocarSenha: false },
    vinculos: [VINCULO_ADMIN],
    ...parcial,
  };
}
