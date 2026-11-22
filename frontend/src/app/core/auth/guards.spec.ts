import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from './auth.service';
import { autenticadoGuard, perfilGuard, trocaDeSenhaGuard } from './guards';
import { sessao } from './testes-apoio';

describe('guards', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function logar(deveTrocarSenha = false) {
    const login = firstValueFrom(TestBed.inject(AuthService).login('ana@x.com', 'x'));
    http
      .expectOne('/api/v1/auth/login')
      .flush(sessao({ usuario: { id: 'u1', nome: 'Ana', email: 'ana@x.com', deveTrocarSenha } }));
    await login;
  }

  function rodar(guarda: (...a: never[]) => unknown): unknown {
    return TestBed.runInInjectionContext(() => (guarda as () => unknown)());
  }

  function url(resultado: unknown): string {
    return TestBed.inject(Router).serializeUrl(resultado as UrlTree);
  }

  it('naoAutenticadoVaiParaLogin', () => {
    expect(url(rodar(autenticadoGuard))).toBe('/login');
  });

  it('autenticadoPassa', async () => {
    await logar();
    expect(rodar(autenticadoGuard)).toBe(true);
  });

  it('deveTrocarSenhaVaiParaTroca', async () => {
    await logar(true);
    expect(url(rodar(trocaDeSenhaGuard))).toBe('/trocar-senha');
  });

  it('perfilCertoPassaEDiferenteVoltaAoInicio', async () => {
    await logar();
    expect(rodar(perfilGuard('ADMIN'))).toBe(true);
    expect(url(rodar(perfilGuard('ALUNO')))).toBe('/');
  });
});
