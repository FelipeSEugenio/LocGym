import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { VINCULO_ALUNO } from '../../core/auth/testes-apoio';
import { ContextoService } from '../../core/contexto/contexto.service';
import { TrocarSenha } from './trocar-senha';

describe('TrocarSenha', () => {
  function montar() {
    localStorage.clear();
    const auth = {
      trocarSenha: vi.fn(() => of(undefined)),
      usuario: () => ({ id: 'u1', nome: 'Ana', email: 'a@x.com', deveTrocarSenha: false }),
      vinculos: () => [VINCULO_ALUNO],
    };
    TestBed.configureTestingModule({
      imports: [TrocarSenha],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
    TestBed.inject(ContextoService).selecionar('a2', 'ALUNO'); // o AuthService real sincroniza o contexto
    const fixture = TestBed.createComponent(TrocarSenha);
    fixture.detectChanges();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    return { fixture, auth, navegar, el: fixture.nativeElement as HTMLElement };
  }

  function preencher(el: HTMLElement, atual: string, nova: string, confirmacao: string) {
    const [a, n, c] = Array.from(el.querySelectorAll('input'));
    for (const [campo, valor] of [[a, atual], [n, nova], [c, confirmacao]] as const) {
      campo.value = valor;
      campo.dispatchEvent(new Event('input'));
    }
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
  }

  it('enviaENavega', () => {
    const { el, auth, navegar } = montar();
    preencher(el, 'inicial-123', 'nova-senha-1', 'nova-senha-1');
    expect(auth.trocarSenha).toHaveBeenCalledWith('inicial-123', 'nova-senha-1');
    expect(navegar).toHaveBeenCalledWith('/aluno');
  });

  it('senhaCurtaBloqueiaEnvio', () => {
    const { el, auth } = montar();
    preencher(el, 'inicial-123', 'curta', 'curta');
    expect(auth.trocarSenha).not.toHaveBeenCalled();
  });

  it('confirmacaoDiferenteBloqueiaEnvio', () => {
    const { el, auth } = montar();
    preencher(el, 'inicial-123', 'nova-senha-1', 'nova-senha-2');
    expect(auth.trocarSenha).not.toHaveBeenCalled();
  });
});
