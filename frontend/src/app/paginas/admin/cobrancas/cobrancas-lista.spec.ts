import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Cobranca, FinanceiroApi } from '../../../core/api/financeiro';
import { CobrancasLista } from './cobrancas-lista';

const COBRANCA: Cobranca = {
  id: 'c1', matriculaId: 'm1', alunoId: 'a1', alunoNome: 'Ana', valor: 150, vencimento: '2026-11-01',
  periodoInicio: '2026-11-01', periodoFim: '2026-12-01', status: 'ATRASADO', dataPagamento: null, forma: null,
  observacoes: null, motivoCancelamento: null,
};

describe('CobrancasLista', () => {
  function montar(registrar = vi.fn(() => of({ ...COBRANCA, status: 'PAGO' as const }))) {
    const api = {
      listarCobrancas: vi.fn(() => of({ itens: [COBRANCA], pagina: 0, tamanho: 20, total: 1 })),
      registrarPagamento: registrar,
      abonarCobranca: vi.fn(() => of(COBRANCA)),
    };
    const dialogo = { open: vi.fn(() => ({ afterClosed: () => of({ dataPagamento: '2026-11-23', forma: 'PIX', observacoes: null }) })) };
    TestBed.configureTestingModule({
      imports: [CobrancasLista],
      providers: [provideRouter([]), { provide: FinanceiroApi, useValue: api }],
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialogo });
    const fixture = TestBed.createComponent(CobrancasLista);
    fixture.detectChanges();
    return { fixture, api, comp: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  function botao(el: HTMLElement, texto: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === texto) as HTMLButtonElement;
  }

  it('mostraCobranca', () => {
    const { el } = montar();
    expect(el.textContent).toContain('Ana');
    expect(el.textContent).toContain('01/11/2026');
    expect(el.textContent).toContain('Atrasada');
    expect(el.textContent).toMatch(/R\$\s*150,00/);
  });

  it('filtrosEnviadosAApi', () => {
    const { el, api, comp, fixture } = montar();
    botao(el, 'Atrasadas').click();
    comp.vencimentoDe.set('2026-11-01');
    comp.vencimentoAte.set('2026-11-30');
    comp.irPara(0);
    fixture.detectChanges();
    expect(api.listarCobrancas).toHaveBeenLastCalledWith(expect.objectContaining({
      status: 'ATRASADO', vencimentoDe: '2026-11-01', vencimentoAte: '2026-11-30', pagina: 0,
    }));
  });

  it('registrarPagamentoRecarrega', () => {
    const { el, api } = montar();
    botao(el, 'Registrar pagamento').click();
    expect(api.registrarPagamento).toHaveBeenCalledWith('c1', { dataPagamento: '2026-11-23', forma: 'PIX', observacoes: null });
    expect(api.listarCobrancas).toHaveBeenCalledTimes(2);
  });

  it('erro409QuitarMaisAntiga', () => {
    const erro = new HttpErrorResponse({ status: 409,
      error: { detail: 'Quite primeiro a cobrança de 01/10/2026', codigo: 'QUITAR_MAIS_ANTIGA_PRIMEIRO' } });
    const { el, fixture } = montar(vi.fn(() => throwError(() => erro)));
    botao(el, 'Registrar pagamento').click();
    fixture.detectChanges();
    expect(el.textContent).toContain('Quite primeiro a cobrança de 01/10/2026');
  });
});
