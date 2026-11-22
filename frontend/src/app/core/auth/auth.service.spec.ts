import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ContextoService } from '../contexto/contexto.service';
import { AuthService } from './auth.service';
import { sessao, VINCULO_ADMIN } from './testes-apoio';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loginGuardaTokenEVinculos', async () => {
    const feito = firstValueFrom(auth.login('ana@x.com', 'senha-forte-123'));
    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.body).toEqual({ email: 'ana@x.com', senha: 'senha-forte-123' });
    req.flush(sessao());
    await feito;

    expect(auth.autenticado()).toBe(true);
    expect(auth.accessToken()).toBe('token-1');
    expect(auth.vinculos()).toEqual([VINCULO_ADMIN]);
    expect(TestBed.inject(ContextoService).atual()).toEqual({ academiaId: 'a1', perfil: 'ADMIN' });
  });

  it('logoutLimpaEstado', async () => {
    const login = firstValueFrom(auth.login('ana@x.com', 'x'));
    http.expectOne('/api/v1/auth/login').flush(sessao());
    await login;

    const saida = firstValueFrom(auth.logout());
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await saida;

    expect(auth.autenticado()).toBe(false);
    expect(auth.accessToken()).toBeNull();
    expect(TestBed.inject(ContextoService).atual()).toBeNull();
  });

  it('logoutLimpaMesmoSeOServidorFalhar', async () => {
    const saida = firstValueFrom(auth.logout());
    http.expectOne('/api/v1/auth/logout').error(new ProgressEvent('error'));
    await saida;
    expect(auth.autenticado()).toBe(false);
  });

  it('iniciarRenovaSessao', async () => {
    const inicio = auth.iniciar();
    http.expectOne('/api/v1/auth/refresh').flush(sessao({ accessToken: 'renovado' }));
    await inicio;
    expect(auth.autenticado()).toBe(true);
    expect(auth.accessToken()).toBe('renovado');
  });

  it('iniciarSemSessao', async () => {
    const inicio = auth.iniciar();
    http.expectOne('/api/v1/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });
    await inicio;
    expect(auth.autenticado()).toBe(false);
  });

  it('trocarSenhaAtualizaUsuario', async () => {
    const login = firstValueFrom(auth.login('ana@x.com', 'x'));
    http.expectOne('/api/v1/auth/login').flush(
      sessao({ usuario: { id: 'u1', nome: 'Ana', email: 'ana@x.com', deveTrocarSenha: true } }),
    );
    await login;
    expect(auth.usuario()?.deveTrocarSenha).toBe(true);

    const troca = firstValueFrom(auth.trocarSenha('inicial-123', 'nova-senha-1'));
    http.expectOne('/api/v1/auth/trocar-senha').flush(sessao({ accessToken: 'novo' }));
    await troca;
    expect(auth.usuario()?.deveTrocarSenha).toBe(false);
    expect(auth.accessToken()).toBe('novo');
  });

  it('renovarComServidorForaMantemSessao', async () => {
    const login = firstValueFrom(auth.login('ana@x.com', 'x'));
    http.expectOne('/api/v1/auth/login').flush(sessao());
    await login;

    const renovacao = firstValueFrom(auth.renovar());
    http.expectOne('/api/v1/auth/refresh').flush({}, { status: 503, statusText: 'Service Unavailable' });

    await expect(renovacao).rejects.toBeTruthy();
    expect(auth.autenticado()).toBe(true);
    expect(TestBed.inject(ContextoService).atual()).toEqual({ academiaId: 'a1', perfil: 'ADMIN' });
  });

  it('iniciarComServidorForaNaoQuebra', async () => {
    const inicio = auth.iniciar();
    http.expectOne('/api/v1/auth/refresh').error(new ProgressEvent('error'));
    await inicio;
    expect(auth.autenticado()).toBe(false);
  });
});
