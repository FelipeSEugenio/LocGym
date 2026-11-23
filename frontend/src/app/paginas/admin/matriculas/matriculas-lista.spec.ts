import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { FinanceiroApi } from '../../../core/api/financeiro';
import { MatriculasLista } from './matriculas-lista';

describe('MatriculasLista', () => {
  function montar() {
    const api = {
      listarMatriculas: vi.fn(() => of({
        itens: [{ id: 'm1', alunoId: 'a1', alunoNome: 'Ana', planoId: 'p1', planoNome: 'Mensal', valor: 150,
          periodicidade: 'MENSAL', dataInicio: '2026-11-01', vigenteAte: '2026-12-01', status: 'ATIVA' }],
        pagina: 0, tamanho: 20, total: 1,
      })),
    };
    TestBed.configureTestingModule({
      imports: [MatriculasLista],
      providers: [provideRouter([]), { provide: FinanceiroApi, useValue: api }],
    });
    const fixture = TestBed.createComponent(MatriculasLista);
    fixture.detectChanges();
    return { fixture, api, el: fixture.nativeElement as HTMLElement };
  }

  it('mostraMatriculas', () => {
    const { el } = montar();
    expect(el.textContent).toContain('Ana');
    expect(el.textContent).toContain('Mensal');
    expect(el.textContent).toContain('01/12/2026');
    expect(el.textContent).toContain('Ativa');
  });

  it('filtraPorStatus', () => {
    const { el, api, fixture } = montar();
    const botao = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Vencidas') as HTMLButtonElement;
    botao.click();
    fixture.detectChanges();
    expect(api.listarMatriculas).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'VENCIDA', pagina: 0 }));
  });
});
