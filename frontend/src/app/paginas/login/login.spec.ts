import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { VINCULO_ADMIN } from '../../core/auth/testes-apoio';
import { ContextoService } from '../../core/contexto/contexto.service';
import { Login } from './login';

describe('Login', () => {
  function montar(resultado: 'ok' | 'erro') {
    const auth = {
      login: vi.fn(() =>
        resultado === 'ok'
          ? of(undefined)
          : throwError(() => new HttpErrorResponse({ status: 401, error: { detail: 'E-mail ou senha inválidos' } })),
      ),
      usuario: () => ({ id: 'u1', nome: 'Ana', email: 'ana@x.com', deveTrocarSenha: false }),
      vinculos: () => [VINCULO_ADMIN],
    };
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
    TestBed.inject(ContextoService).selecionar('a1', 'ADMIN');
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    return { fixture, auth, navegar, el: fixture.nativeElement as HTMLElement };
  }

  function preencherEEnviar(el: HTMLElement, fixture: { detectChanges(): void }) {
    const [email, senha] = Array.from(el.querySelectorAll('input'));
    email.value = 'ana@x.com';
    email.dispatchEvent(new Event('input'));
    senha.value = 'senha-forte-123';
    senha.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  beforeEach(() => localStorage.clear());

  it('credenciaisInvalidasMostramMensagem', () => {
    const { el, fixture, navegar } = montar('erro');
    preencherEEnviar(el, fixture);
    expect(el.textContent).toContain('E-mail ou senha inválidos');
    expect(navegar).not.toHaveBeenCalled();
  });

  it('sucessoComVinculoAdminVaiParaOPainel', () => {
    const { el, fixture, auth, navegar } = montar('ok');
    preencherEEnviar(el, fixture);
    expect(auth.login).toHaveBeenCalledWith('ana@x.com', 'senha-forte-123');
    expect(navegar).toHaveBeenCalledWith('/admin');
  });
});
