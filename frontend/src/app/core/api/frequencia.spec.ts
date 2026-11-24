import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AgendaApi, FrequenciaApi } from './frequencia';

describe('AgendaApi e FrequenciaApi', () => {
  let agenda: AgendaApi;
  let frequencia: FrequenciaApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    agenda = TestBed.inject(AgendaApi);
    frequencia = TestBed.inject(FrequenciaApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('checkinQrEnviaTokenEAulaEscolhida', () => {
    frequencia.checkinQr('tok', 'h1').subscribe();
    const req = http.expectOne('/api/v1/checkins/qr');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'tok', horarioId: 'h1' });
    req.flush({});
  });

  it('checkinQrSemAulaNaoEnviaHorario', () => {
    frequencia.checkinQr('tok').subscribe();
    const req = http.expectOne('/api/v1/checkins/qr');
    expect(req.request.body).toEqual({ token: 'tok' });
    req.flush({});
  });

  it('aulasAbertasNoMomentoInformado', () => {
    agenda.aulasAbertas({ data: '2026-11-23', hora: '19:05' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/recepcao/aulas-abertas');
    expect(req.request.params.get('data')).toBe('2026-11-23');
    expect(req.request.params.get('hora')).toBe('19:05');
    req.flush([]);
  });

  it('aulasAbertasAgoraSemParametros', () => {
    agenda.aulasAbertas().subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/recepcao/aulas-abertas');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([]);
  });

  it('listarCheckinsOmiteFiltrosVazios', () => {
    frequencia.listar({ alunoId: 'a1', de: '2026-11-01', ate: '', status: null, pagina: 0, tamanho: 20 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/checkins');
    expect(req.request.params.get('alunoId')).toBe('a1');
    expect(req.request.params.get('de')).toBe('2026-11-01');
    expect(req.request.params.has('ate')).toBe(false);
    expect(req.request.params.has('status')).toBe(false);
    req.flush({ itens: [], pagina: 0, tamanho: 20, total: 0 });
  });

  it('excluirECorrigir', () => {
    frequencia.excluir('c1', 'erro').subscribe();
    const ex = http.expectOne('/api/v1/checkins/c1/excluir');
    expect(ex.request.body).toEqual({ motivo: 'erro' });
    ex.flush(null);
    frequencia.corrigir('c1', { data: '2026-11-23', hora: '19:20', horarioId: null, motivo: 'x' }).subscribe();
    const co = http.expectOne('/api/v1/checkins/c1/corrigir');
    expect(co.request.body).toEqual({ data: '2026-11-23', hora: '19:20', motivo: 'x' });
    co.flush({});
  });

  it('meuQr', () => {
    frequencia.meuQr().subscribe();
    http.expectOne('/api/v1/eu/qr-checkin').flush({ token: 't', expiraEm: '2026-11-23T22:00:60Z' });
  });

  it('horariosCrud', () => {
    agenda.listar().subscribe();
    http.expectOne('/api/v1/horarios').flush([]);
    agenda.excluir('h1').subscribe();
    const del = http.expectOne('/api/v1/horarios/h1');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });
});
