import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FinanceiroApi } from './financeiro';

describe('FinanceiroApi', () => {
  let api: FinanceiroApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(FinanceiroApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('criarMatriculaPostaOCorpo', () => {
    const dados = { alunoId: 'a1', planoId: 'p1', dataInicio: '2026-11-23', valor: 99.9 };
    api.criarMatricula(dados).subscribe();
    const req = http.expectOne('/api/v1/matriculas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dados);
    req.flush({});
  });

  it('listarMatriculasOmiteFiltrosVazios', () => {
    api.listarMatriculas({ status: 'VENCIDA', busca: '', pagina: 0, tamanho: 20 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/matriculas');
    expect(req.request.params.get('status')).toBe('VENCIDA');
    expect(req.request.params.has('busca')).toBe(false);
    req.flush({ itens: [], pagina: 0, tamanho: 20, total: 0 });
  });

  it('registrarPagamentoPostaNaCobranca', () => {
    api.registrarPagamento('c1', { dataPagamento: '2026-11-23', forma: 'PIX', observacoes: null }).subscribe();
    const req = http.expectOne('/api/v1/cobrancas/c1/pagamento');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.forma).toBe('PIX');
    req.flush({});
  });

  it('abonarEmLote', () => {
    api.abonar('m1', 'c2', 'Meses parados').subscribe();
    const req = http.expectOne('/api/v1/matriculas/m1/abonar');
    expect(req.request.body).toEqual({ ateCobrancaId: 'c2', motivo: 'Meses parados' });
    req.flush({});
  });
});
