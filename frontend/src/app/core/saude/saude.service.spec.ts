import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EstadoBackend, SaudeService } from './saude.service';

describe('SaudeService', () => {
  let servico: SaudeService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servico = TestBed.inject(SaudeService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function verificar(): EstadoBackend[] {
    const emitidos: EstadoBackend[] = [];
    servico.verificar().subscribe({
      next: (e) => emitidos.push(e),
      error: () => emitidos.push('ERRO PROPAGADO' as EstadoBackend),
    });
    return emitidos;
  }

  it('retornaOnlineQuandoUp', () => {
    const emitidos = verificar();
    http.expectOne('/actuator/health').flush({ status: 'UP' });
    expect(emitidos).toEqual(['online']);
  });

  it('retornaIndisponivelQuandoDown', () => {
    const emitidos = verificar();
    http
      .expectOne('/actuator/health')
      .flush({ status: 'DOWN' }, { status: 503, statusText: 'Service Unavailable' });
    expect(emitidos).toEqual(['indisponivel']);
  });

  it('retornaIndisponivelQuandoRedeFalha', () => {
    const emitidos = verificar();
    http.expectOne('/actuator/health').error(new ProgressEvent('error'));
    expect(emitidos).toEqual(['indisponivel']);
  });
});
