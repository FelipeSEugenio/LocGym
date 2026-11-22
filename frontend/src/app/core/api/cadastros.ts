import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DadosAcademia } from '../auth/modelos';
import {
  Academia,
  Administrador,
  Aluno,
  DadosAluno,
  DadosPlano,
  DadosProfessor,
  NovoAluno,
  Pagina,
  Plano,
  Professor,
  StatusAluno,
  StatusAtivo,
} from './modelos';

function parametros(valores: Record<string, string | number | null | undefined>): HttpParams {
  let p = new HttpParams();
  for (const [chave, valor] of Object.entries(valores)) {
    if (valor !== null && valor !== undefined && valor !== '') {
      p = p.set(chave, String(valor));
    }
  }
  return p;
}

@Injectable({ providedIn: 'root' })
export class AlunosApi {
  private readonly http = inject(HttpClient);
  buscar(busca: string, pagina: number, tamanho: number, status?: StatusAluno): Observable<Pagina<Aluno>> {
    return this.http.get<Pagina<Aluno>>('/api/v1/alunos', { params: parametros({ busca, pagina, tamanho, status }) });
  }
  obter(id: string): Observable<Aluno> {
    return this.http.get<Aluno>(`/api/v1/alunos/${id}`);
  }
  criar(dados: NovoAluno): Observable<Aluno> {
    return this.http.post<Aluno>('/api/v1/alunos', dados);
  }
  atualizar(id: string, dados: DadosAluno): Observable<Aluno> {
    return this.http.put<Aluno>(`/api/v1/alunos/${id}`, dados);
  }
  alterarStatus(id: string, status: StatusAluno): Observable<Aluno> {
    return this.http.patch<Aluno>(`/api/v1/alunos/${id}/status`, { status });
  }
  meusDados(): Observable<Aluno> {
    return this.http.get<Aluno>('/api/v1/eu/aluno');
  }
}

@Injectable({ providedIn: 'root' })
export class ProfessoresApi {
  private readonly http = inject(HttpClient);
  buscar(busca: string, pagina: number, tamanho: number): Observable<Pagina<Professor>> {
    return this.http.get<Pagina<Professor>>('/api/v1/professores', { params: parametros({ busca, pagina, tamanho }) });
  }
  obter(id: string): Observable<Professor> {
    return this.http.get<Professor>(`/api/v1/professores/${id}`);
  }
  criar(dados: DadosProfessor): Observable<Professor> {
    return this.http.post<Professor>('/api/v1/professores', dados);
  }
  atualizar(id: string, dados: DadosProfessor): Observable<Professor> {
    return this.http.put<Professor>(`/api/v1/professores/${id}`, dados);
  }
  alterarStatus(id: string, status: StatusAtivo): Observable<Professor> {
    return this.http.patch<Professor>(`/api/v1/professores/${id}/status`, { status });
  }
}

@Injectable({ providedIn: 'root' })
export class PlanosApi {
  private readonly http = inject(HttpClient);
  listar(): Observable<Plano[]> {
    return this.http.get<Plano[]>('/api/v1/planos');
  }
  obter(id: string): Observable<Plano> {
    return this.http.get<Plano>(`/api/v1/planos/${id}`);
  }
  criar(dados: DadosPlano): Observable<Plano> {
    return this.http.post<Plano>('/api/v1/planos', dados);
  }
  atualizar(id: string, dados: DadosPlano): Observable<Plano> {
    return this.http.put<Plano>(`/api/v1/planos/${id}`, dados);
  }
  alterarStatus(id: string, status: StatusAtivo): Observable<Plano> {
    return this.http.patch<Plano>(`/api/v1/planos/${id}/status`, { status });
  }
}

@Injectable({ providedIn: 'root' })
export class AdministradoresApi {
  private readonly http = inject(HttpClient);
  listar(): Observable<Administrador[]> {
    return this.http.get<Administrador[]>('/api/v1/administradores');
  }
  convidar(nome: string, email: string, senhaInicial: string): Observable<Administrador> {
    return this.http.post<Administrador>('/api/v1/administradores', { nome, email, senhaInicial });
  }
  remover(usuarioId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/administradores/${usuarioId}`);
  }
}

@Injectable({ providedIn: 'root' })
export class AcademiaApi {
  private readonly http = inject(HttpClient);
  ativa(): Observable<Academia> {
    return this.http.get<Academia>('/api/v1/academia');
  }
  atualizar(dados: DadosAcademia): Observable<Academia> {
    return this.http.put<Academia>('/api/v1/academia', dados);
  }
  alterarStatus(status: 'ATIVA' | 'INATIVA'): Observable<Academia> {
    return this.http.patch<Academia>('/api/v1/academia/status', { status });
  }
  criar(dados: DadosAcademia): Observable<Academia> {
    return this.http.post<Academia>('/api/v1/academias', dados);
  }
}
