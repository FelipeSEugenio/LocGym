import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdministradoresApi } from '../../../core/api/cadastros';
import { Administrador } from '../../../core/api/modelos';
import { Administradores } from './administradores';

describe('Administradores', () => {
  function montar(lista: Administrador[]) {
    const api = { listar: vi.fn(() => of(lista)), convidar: vi.fn(), remover: vi.fn(() => of(undefined)) };
    TestBed.configureTestingModule({ imports: [Administradores], providers: [{ provide: AdministradoresApi, useValue: api }] });
    const fixture = TestBed.createComponent(Administradores);
    fixture.detectChanges();
    return { el: fixture.nativeElement as HTMLElement, api };
  }

  const removerBotoes = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('button')).filter((b) => b.textContent?.includes('Remover')) as HTMLButtonElement[];

  it('removerDesabilitadoComUmSoAdmin', () => {
    const { el } = montar([{ usuarioId: 'u1', nome: 'Dono', email: 'd@x.com' }]);
    expect(removerBotoes(el)).toHaveLength(1);
    expect(removerBotoes(el)[0].disabled).toBe(true);
  });

  it('removerHabilitadoComMaisDeUm', () => {
    const { el } = montar([
      { usuarioId: 'u1', nome: 'Dono', email: 'd@x.com' },
      { usuarioId: 'u2', nome: 'Sócio', email: 's@x.com' },
    ]);
    expect(removerBotoes(el).every((b) => !b.disabled)).toBe(true);
  });
});
