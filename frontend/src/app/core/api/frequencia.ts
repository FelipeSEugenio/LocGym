import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina } from './modelos';

export type StatusHorario = 'ATIVO' | 'INATIVO';
export type Origem = 'QR' | 'MANUAL';
export type Pontualidade = 'PONTUAL' | 'ATRASADO';
export type StatusCheckin = 'ATIVO' | 'CORRIGIDO' | 'EXCLUIDO';

/** Horário da grade semanal; {@code diaSemana} ISO (1 = segunda … 7 = domingo); horas "HH:mm". */
export interface Horario {
  id: string;
  professorId: string;
  professorNome: string | null;
  diaSemana: number;
  inicio: string;
  fim: string;
  tipo: string;
  status: StatusHorario;
}

export interface DadosHorario {
  professorId: string;
  diaSemana: number;
  inicio: string;
  fim: string;
  tipo: string;
  status?: StatusHorario;
}

export interface AulaAberta { horarioId: string; tipo: string; professorNome: string | null; inicio: string; fim: string }
export interface Momento { data: string; hora: string }

export interface Alerta { vencimentoMaisAntigo: string; diasDeAtraso: number }

export interface ResultadoCheckin {
  checkinId: string;
  aluno: { id: string; nome: string };
  aula: AulaAberta | null;
  livre: boolean;
  pontualidade: Pontualidade | null;
  alerta: Alerta | null;
}

export interface CheckinListado {
  id: string;
  alunoId: string;
  alunoNome: string | null;
  data: string;
  hora: string;
  origem: Origem;
  pontualidade: Pontualidade | null;
  status: StatusCheckin;
  motivo: string | null;
  aula: AulaAberta | null;
}

export interface PresencaManual { alunoId: string; data: string; hora: string; horarioId: string | null }
export interface Correcao { data: string; hora: string; horarioId: string | null; motivo: string }
export interface TokenQr { token: string; expiraEm: string }

export interface FiltroCheckins {
  alunoId?: string | null;
  horarioId?: string | null;
  de?: string | null;
  ate?: string | null;
  status?: StatusCheckin | null;
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

function semNulos<T extends object>(corpo: T): Partial<T> {
  return Object.fromEntries(Object.entries(corpo).filter(([, v]) => v !== null && v !== undefined)) as Partial<T>;
}

/** Grade de horários e aulas abertas (Fase 4). */
@Injectable({ providedIn: 'root' })
export class AgendaApi {
  private readonly http = inject(HttpClient);

  listar(filtro: { diaSemana?: number | null; status?: StatusHorario | null } = {}): Observable<Horario[]> {
    return this.http.get<Horario[]>('/api/v1/horarios', { params: parametros(filtro) });
  }
  obter(id: string): Observable<Horario> {
    return this.http.get<Horario>(`/api/v1/horarios/${id}`);
  }
  criar(dados: DadosHorario): Observable<Horario> {
    return this.http.post<Horario>('/api/v1/horarios', dados);
  }
  alterar(id: string, dados: DadosHorario): Observable<Horario> {
    return this.http.put<Horario>(`/api/v1/horarios/${id}`, dados);
  }
  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/horarios/${id}`);
  }
  /** Aulas abertas agora (São Paulo) ou no momento informado. */
  aulasAbertas(momento?: Momento): Observable<AulaAberta[]> {
    return this.http.get<AulaAberta[]>('/api/v1/recepcao/aulas-abertas', { params: parametros(momento ?? {}) });
  }
}

/** Check-in, presença manual, histórico e QR do aluno (Fase 4). */
@Injectable({ providedIn: 'root' })
export class FrequenciaApi {
  private readonly http = inject(HttpClient);

  checkinQr(token: string, horarioId?: string | null): Observable<ResultadoCheckin> {
    return this.http.post<ResultadoCheckin>('/api/v1/checkins/qr', semNulos({ token, horarioId }));
  }
  manual(dados: PresencaManual): Observable<ResultadoCheckin> {
    return this.http.post<ResultadoCheckin>('/api/v1/checkins', semNulos(dados));
  }
  listar(f: FiltroCheckins): Observable<Pagina<CheckinListado>> {
    return this.http.get<Pagina<CheckinListado>>('/api/v1/checkins', { params: parametros(f) });
  }
  corrigir(id: string, dados: Correcao): Observable<ResultadoCheckin> {
    return this.http.post<ResultadoCheckin>(`/api/v1/checkins/${id}/corrigir`, semNulos(dados));
  }
  excluir(id: string, motivo: string): Observable<void> {
    return this.http.post<void>(`/api/v1/checkins/${id}/excluir`, { motivo });
  }
  meuQr(): Observable<TokenQr> {
    return this.http.get<TokenQr>('/api/v1/eu/qr-checkin');
  }
}
