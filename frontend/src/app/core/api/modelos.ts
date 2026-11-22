import { DadosAcademia } from '../auth/modelos';

export interface Pagina<T> {
  itens: T[];
  pagina: number;
  tamanho: number;
  total: number;
}

export type StatusAluno = 'ATIVO' | 'INATIVO' | 'CANCELADO';
export type StatusAtivo = 'ATIVO' | 'INATIVO';
export type Periodicidade = 'MENSAL' | 'TRIMESTRAL' | 'SEMESTRAL' | 'ANUAL';

export interface DadosAluno {
  nome: string;
  cpf: string;
  telefone: string;
  dataNascimento: string;
  endereco: string;
  cidade: string;
  estado: string;
  cep: string;
}

export interface Aluno extends DadosAluno {
  id: string;
  email: string;
  status: StatusAluno;
}

export interface NovoAluno extends DadosAluno {
  email: string;
  senhaInicial: string;
}

export interface DadosProfessor {
  nome: string;
  email: string | null;
  telefone: string | null;
  dataNascimento: string | null;
  especialidade: string | null;
}

export interface Professor extends DadosProfessor {
  id: string;
  status: StatusAtivo;
}

export interface DadosPlano {
  nome: string;
  descricao: string | null;
  valor: number;
  periodicidade: Periodicidade;
}

export interface Plano extends DadosPlano {
  id: string;
  status: StatusAtivo;
}

export interface Administrador {
  usuarioId: string;
  nome: string;
  email: string;
}

export interface Academia extends DadosAcademia {
  id: string;
  status: 'ATIVA' | 'INATIVA';
}
