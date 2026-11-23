import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina, Periodicidade } from './modelos';

export type StatusMatricula = 'PENDENTE' | 'ATIVA' | 'VENCIDA' | 'SUSPENSA' | 'CANCELADA';
export type StatusCobranca = 'PENDENTE' | 'PAGO' | 'ATRASADO' | 'CANCELADO';
export type FormaPagamento = 'DINHEIRO' | 'PIX' | 'CARTAO_CREDITO' | 'CARTAO_DEBITO' | 'TRANSFERENCIA';

export interface Referencia { id: string; nome: string }
export interface Periodo { inicio: string; fim: string }

export interface CobrancaResumo {
  id: string;
  matriculaId: string;
  alunoId: string;
  valor: number;
  vencimento: string;
  periodoInicio: string;
  periodoFim: string;
  status: StatusCobranca;
  dataPagamento: string | null;
  forma: FormaPagamento | null;
  observacoes: string | null;
  motivoCancelamento: string | null;
}

export interface Cobranca extends CobrancaResumo { alunoNome: string | null }

export interface MatriculaDetalhe {
  id: string;
  aluno: Referencia;
  plano: Referencia;
  valor: number;
  periodicidade: Periodicidade;
  dataInicio: string;
  vigenteAte: string;
  status: StatusMatricula;
  suspensaDesde: string | null;
  motivo: string | null;
  inadimplente: boolean;
  periodos: Periodo[];
  cobrancas: CobrancaResumo[];
}

export interface MatriculaListada {
  id: string;
  alunoId: string;
  alunoNome: string;
  planoId: string;
  planoNome: string;
  valor: number;
  periodicidade: Periodicidade;
  dataInicio: string;
  vigenteAte: string;
  status: StatusMatricula;
}

export interface Inadimplente {
  alunoId: string;
  nome: string;
  cpf: string;
  totalEmAtraso: number;
  cobrancasAtrasadas: number;
  vencimentoMaisAntigo: string;
  diasDeAtraso: number;
  matriculaId: string | null;
  statusMatricula: StatusMatricula | null;
}

export interface NovaMatricula { alunoId: string; planoId: string; dataInicio: string; valor: number | null }
export interface DadosPagamento { dataPagamento: string; forma: FormaPagamento; observacoes: string | null }

export interface FiltroMatriculas { status?: StatusMatricula | null; busca?: string; pagina: number; tamanho: number }
export interface FiltroCobrancas {
  status?: StatusCobranca | null;
  vencimentoDe?: string | null;
  vencimentoAte?: string | null;
  alunoId?: string | null;
  pagina: number;
  tamanho: number;
}

function parametros(valores: object): HttpParams {
  let p = new HttpParams();
  for (const [chave, valor] of Object.entries(valores)) {
    if (valor !== null && valor !== undefined && valor !== '') {
      p = p.set(chave, String(valor));
    }
  }
  return p;
}

/** Matrículas, cobranças e inadimplência (Fase 3). */
@Injectable({ providedIn: 'root' })
export class FinanceiroApi {
  private readonly http = inject(HttpClient);

  listarMatriculas(f: FiltroMatriculas): Observable<Pagina<MatriculaListada>> {
    return this.http.get<Pagina<MatriculaListada>>('/api/v1/matriculas', { params: parametros(f) });
  }
  criarMatricula(d: NovaMatricula): Observable<MatriculaDetalhe> {
    return this.http.post<MatriculaDetalhe>('/api/v1/matriculas', d);
  }
  matricula(id: string): Observable<MatriculaDetalhe> {
    return this.http.get<MatriculaDetalhe>(`/api/v1/matriculas/${id}`);
  }
  matriculaDoAluno(alunoId: string): Observable<MatriculaDetalhe> {
    return this.http.get<MatriculaDetalhe>(`/api/v1/alunos/${alunoId}/matricula`);
  }
  renovar(id: string): Observable<MatriculaDetalhe> {
    return this.http.post<MatriculaDetalhe>(`/api/v1/matriculas/${id}/renovar`, {});
  }
  suspender(id: string, motivo: string): Observable<MatriculaDetalhe> {
    return this.http.post<MatriculaDetalhe>(`/api/v1/matriculas/${id}/suspender`, { motivo });
  }
  reativar(id: string): Observable<MatriculaDetalhe> {
    return this.http.post<MatriculaDetalhe>(`/api/v1/matriculas/${id}/reativar`, {});
  }
  cancelar(id: string, motivo: string): Observable<MatriculaDetalhe> {
    return this.http.post<MatriculaDetalhe>(`/api/v1/matriculas/${id}/cancelar`, { motivo });
  }
  abonar(id: string, ateCobrancaId: string, motivo: string): Observable<MatriculaDetalhe> {
    return this.http.post<MatriculaDetalhe>(`/api/v1/matriculas/${id}/abonar`, { ateCobrancaId, motivo });
  }
  listarCobrancas(f: FiltroCobrancas): Observable<Pagina<Cobranca>> {
    return this.http.get<Pagina<Cobranca>>('/api/v1/cobrancas', { params: parametros(f) });
  }
  registrarPagamento(cobrancaId: string, d: DadosPagamento): Observable<Cobranca> {
    return this.http.post<Cobranca>(`/api/v1/cobrancas/${cobrancaId}/pagamento`, d);
  }
  abonarCobranca(cobrancaId: string, motivo: string): Observable<Cobranca> {
    return this.http.post<Cobranca>(`/api/v1/cobrancas/${cobrancaId}/cancelar`, { motivo });
  }
  inadimplentes(busca: string, pagina: number, tamanho: number): Observable<Pagina<Inadimplente>> {
    return this.http.get<Pagina<Inadimplente>>('/api/v1/inadimplencia', { params: parametros({ busca, pagina, tamanho }) });
  }
}
