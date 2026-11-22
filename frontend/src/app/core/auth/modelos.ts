export type Perfil = 'ADMIN' | 'ALUNO';

export interface Vinculo {
  academiaId: string;
  academiaNome: string;
  academiaStatus: 'ATIVA' | 'INATIVA';
  perfil: Perfil;
}

export interface UsuarioSessao {
  id: string;
  nome: string;
  email: string;
  deveTrocarSenha: boolean;
}

/** Corpo devolvido por login, cadastro, refresh e troca de senha. */
export interface RespostaSessao {
  accessToken: string;
  expiraEm: string;
  usuario: UsuarioSessao;
  vinculos: Vinculo[];
}

export interface DadosAcademia {
  nome: string;
  cnpj: string;
  endereco: string;
  cidade: string;
  estado: string;
  cep: string;
  telefone: string;
  email: string;
}

export interface CadastroAdministrador {
  nome: string;
  email: string;
  senha: string;
  academia: DadosAcademia;
}

/** Problem Details devolvido pela API em erros. */
export interface Problema {
  title?: string;
  detail?: string;
  codigo?: string;
  erros?: { campo: string; mensagem: string }[];
}
