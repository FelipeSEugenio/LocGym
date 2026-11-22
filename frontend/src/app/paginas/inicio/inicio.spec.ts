import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { VINCULO_ADMIN } from '../../core/auth/testes-apoio';
import { ContextoService } from '../../core/contexto/contexto.service';
import { EstadoBackend, SaudeService } from '../../core/saude/saude.service';
import { Inicio } from './inicio';

function renderizar(estado: EstadoBackend, logado = false) {
  localStorage.clear();
  TestBed.configureTestingModule({
    imports: [Inicio],
    providers: [
      provideRouter([]),
      { provide: SaudeService, useValue: { verificar: () => of(estado) } },
      {
        provide: AuthService,
        useValue: {
          autenticado: () => logado,
          usuario: () => (logado ? { id: 'u1', nome: 'Ana', email: 'a@x.com', deveTrocarSenha: false } : null),
          vinculos: () => (logado ? [VINCULO_ADMIN] : []),
        },
      },
    ],
  });
  if (logado) {
    TestBed.inject(ContextoService).selecionar('a1', 'ADMIN');
  }
  const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  const fixture = TestBed.createComponent(Inicio);
  fixture.detectChanges();
  return { el: fixture.nativeElement as HTMLElement, navegar };
}

describe('Inicio', () => {
  it('exibeOnlineComEntrarECriarAcademia', () => {
    const { el } = renderizar('online');
    expect(el.textContent).toContain('Backend online');
    const links = Array.from(el.querySelectorAll('a')).map((a) => [a.textContent?.trim(), a.getAttribute('href')]);
    expect(links).toContainEqual(['Entrar', '/login']);
    expect(links).toContainEqual(['Criar minha academia', '/criar-academia']);
  });

  it('exibeIndisponivelQuandoHealthFalha', () => {
    const erros = vi.spyOn(console, 'error');
    const { el } = renderizar('indisponivel');
    expect(el.textContent).toContain('Backend indisponível');
    expect(erros).not.toHaveBeenCalled();
  });

  it('usuarioLogadoVaiDiretoParaSuaArea', () => {
    const { navegar } = renderizar('online', true);
    expect(navegar).toHaveBeenCalledWith('/admin');
  });
});
