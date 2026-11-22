import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { PlanosApi } from '../../../core/api/cadastros';
import { Plano } from '../../../core/api/modelos';
import { PlanosLista } from './planos-lista';

const PLANOS: Plano[] = [
  { id: 'p1', nome: 'Mensal', descricao: null, valor: 150, periodicidade: 'MENSAL', status: 'ATIVO' },
  { id: 'p2', nome: 'Anual', descricao: null, valor: 1500, periodicidade: 'ANUAL', status: 'INATIVO' },
];

describe('PlanosLista', () => {
  function montar() {
    const api = {
      listar: vi.fn(() => of(PLANOS)),
      alterarStatus: vi.fn((id: string, status: 'ATIVO' | 'INATIVO') => of({ ...PLANOS[0], status })),
    };
    TestBed.configureTestingModule({
      imports: [PlanosLista],
      providers: [provideRouter([]), { provide: PlanosApi, useValue: api }],
    });
    const fixture = TestBed.createComponent(PlanosLista);
    fixture.detectChanges();
    return { fixture, api, el: fixture.nativeElement as HTMLElement };
  }

  it('listaComStatusEPeriodicidade', () => {
    const { el } = montar();
    expect(el.textContent).toContain('Mensal');
    expect(el.textContent).toContain('Anual');
    expect(el.textContent).toContain('Inativo');
    expect(el.textContent).toMatch(/R\$\s*150,00/);
  });

  it('inativarEReativar', () => {
    const { el, api } = montar();
    const botoes = Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(botoes).toContain('Inativar');
    expect(botoes).toContain('Reativar');

    (Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Inativar') as HTMLButtonElement).click();
    expect(api.alterarStatus).toHaveBeenCalledWith('p1', 'INATIVO');
  });
});
