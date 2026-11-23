import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { FinanceiroApi } from '../../../core/api/financeiro';
import { InadimplenciaPagina } from './inadimplencia';

describe('InadimplenciaPagina', () => {
  function montar() {
    const api = {
      inadimplentes: vi.fn(() => of({
        itens: [{ alunoId: 'a1', nome: 'Carla', cpf: '52998224725', totalEmAtraso: 300, cobrancasAtrasadas: 2,
          vencimentoMaisAntigo: '2026-10-31', diasDeAtraso: 30, matriculaId: 'm1', statusMatricula: 'VENCIDA' }],
        pagina: 0, tamanho: 20, total: 1,
      })),
    };
    TestBed.configureTestingModule({
      imports: [InadimplenciaPagina],
      providers: [provideRouter([]), { provide: FinanceiroApi, useValue: api }],
    });
    const fixture = TestBed.createComponent(InadimplenciaPagina);
    fixture.detectChanges();
    return { el: fixture.nativeElement as HTMLElement, api };
  }

  it('mostraTotalEDias', () => {
    const { el } = montar();
    expect(el.textContent).toContain('Carla');
    expect(el.textContent).toContain('529.982.247-25');
    expect(el.textContent).toMatch(/R\$\s*300,00/);
    expect(el.textContent).toContain('30 dias');
    expect(el.textContent).toContain('31/10/2026');
    expect(el.textContent).toContain('Vencida');
  });

  it('linkParaMatricula', () => {
    const { el } = montar();
    const link = el.querySelector('a[href="/admin/matriculas/m1"]');
    expect(link?.textContent).toContain('Carla');
  });
});
