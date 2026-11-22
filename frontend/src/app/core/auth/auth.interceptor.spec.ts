import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ContextoService } from '../contexto/contexto.service';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { sessao } from './testes-apoio';

describe('authInterceptor', () => {
  let http: HttpTestingController;
  let cliente: HttpClient;
  let auth: AuthService;
  let navegar: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    localStorage.clear();
    navegar = vi.fn().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate: navegar } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    cliente = TestBed.inject(HttpClient);
    auth = TestBed.inject(AuthService);
    const login = firstValueFrom(auth.login('ana@x.com', 'x'));
    http.expectOne('/api/v1/auth/login').flush(sessao());
    await login;
  });

  afterEach(() => http.verify());

  it('adicionaHeaders', () => {
    cliente.get('/api/v1/alunos').subscribe();
    const req = http.expectOne('/api/v1/alunos');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-1');
    expect(req.request.headers.get('X-Academia-Id')).toBe('a1');
    expect(req.request.headers.get('X-Perfil')).toBe('ADMIN');
    req.flush([]);
  });

  it('naoAdicionaContextoEmAuth', () => {
    cliente.post('/api/v1/auth/trocar-senha', {}).subscribe();
    const req = http.expectOne('/api/v1/auth/trocar-senha');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-1');
    expect(req.request.headers.has('X-Academia-Id')).toBe(false);
    req.flush(sessao());
  });

  it('renovaUmaVezERepete', async () => {
    const resposta = firstValueFrom(cliente.get<string[]>('/api/v1/alunos'));
    http.expectOne('/api/v1/alunos').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/auth/refresh').flush(sessao({ accessToken: 'token-2' }));
    const repetida = http.expectOne('/api/v1/alunos');
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer token-2');
    repetida.flush(['ok']);
    expect(await resposta).toEqual(['ok']);
  });

  it('duasRequisicoes401CompartilhamUmRefresh', async () => {
    const a = firstValueFrom(cliente.get('/api/v1/alunos'));
    const b = firstValueFrom(cliente.get('/api/v1/planos'));
    http.expectOne('/api/v1/alunos').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/planos').flush({}, { status: 401, statusText: 'Unauthorized' });

    http.expectOne('/api/v1/auth/refresh').flush(sessao({ accessToken: 'token-2' }));

    http.expectOne('/api/v1/alunos').flush(['a']);
    http.expectOne('/api/v1/planos').flush(['b']);
    expect(await a).toEqual(['a']);
    expect(await b).toEqual(['b']);
  });

  it('refreshFalhaVaiParaLogin', async () => {
    const resposta = firstValueFrom(cliente.get('/api/v1/alunos'));
    http.expectOne('/api/v1/alunos').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });

    await expect(resposta).rejects.toBeTruthy();
    expect(auth.autenticado()).toBe(false);
    expect(navegar).toHaveBeenCalledWith(['/login']);
  });

  it('trocaDeSenhaRedireciona', async () => {
    const resposta = firstValueFrom(cliente.get('/api/v1/alunos'));
    http
      .expectOne('/api/v1/alunos')
      .flush({ codigo: 'TROCA_DE_SENHA_OBRIGATORIA' }, { status: 403, statusText: 'Forbidden' });
    await expect(resposta).rejects.toBeTruthy();
    expect(navegar).toHaveBeenCalledWith(['/trocar-senha']);
  });

  it('semContextoNaoEnviaHeadersDeAcademia', () => {
    TestBed.inject(ContextoService).limpar();
    cliente.get('/api/v1/eu/vinculos').subscribe();
    const req = http.expectOne('/api/v1/eu/vinculos');
    expect(req.request.headers.has('X-Academia-Id')).toBe(false);
    req.flush([]);
  });
});
