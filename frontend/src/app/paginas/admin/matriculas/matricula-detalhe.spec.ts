import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CobrancaResumo, FinanceiroApi, MatriculaDetalhe, StatusMatricula } from '../../../core/api/financeiro';
import { MatriculaDetalhePagina } from './matricula-detalhe';

function cobranca(id: string, vencimento: string, status: CobrancaResumo['status']): CobrancaResumo {
  return { id, matriculaId: 'm1', alunoId: 'a1', valor: 150, vencimento, periodoInicio: vencimento,
    periodoFim: vencimento, status, dataPagamento: null, forma: null, observacoes: null, motivoCancelamento: null };
}

function matricula(status: StatusMatricula, cobrancas: CobrancaResumo[] = []): MatriculaDetalhe {
  return { id: 'm1', aluno: { id: 'a1', nome: 'Ana' }, plano: { id: 'p1', nome: 'Mensal' }, valor: 150,
    periodicidade: 'MENSAL', dataInicio: '2026-11-01', vigenteAte: '2026-12-01', status, suspensaDesde: null,
    motivo: null, inadimplente: false, periodos: [], cobrancas };
}

describe('MatriculaDetalhePagina', () => {
  function montar(m: MatriculaDetalhe, resultadoDialogo: unknown = undefined) {
    const api = {
      matricula: vi.fn(() => of(m)),
      renovar: vi.fn(() => of(m)),
      suspender: vi.fn(() => of(m)),
      reativar: vi.fn(() => of(m)),
      cancelar: vi.fn(() => of(m)),
      abonar: vi.fn(() => of(m)),
      registrarPagamento: vi.fn(() => of({})),
    };
    const dialogo = { open: vi.fn(() => ({ afterClosed: () => of(resultadoDialogo) })) };
    TestBed.configureTestingModule({
      imports: [MatriculaDetalhePagina],
      providers: [
        provideRouter([]),
        { provide: FinanceiroApi, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'm1' }) } } },
      ],
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialogo });
    const fixture = TestBed.createComponent(MatriculaDetalhePagina);
    fixture.detectChanges();
    return { fixture, api, dialogo, el: fixture.nativeElement as HTMLElement };
  }

  function textosDosBotoes(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim() ?? '');
  }

  function botao(el: HTMLElement, texto: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === texto) as HTMLButtonElement;
  }

  it('acoesConformeStatus', () => {
    const ativa = textosDosBotoes(montar(matricula('ATIVA')).el);
    expect(ativa).toEqual(expect.arrayContaining(['Renovar', 'Suspender', 'Cancelar matrícula']));
    expect(ativa).not.toContain('Reativar');
    TestBed.resetTestingModule();
    const suspensa = textosDosBotoes(montar(matricula('SUSPENSA')).el);
    expect(suspensa).toEqual(expect.arrayContaining(['Reativar', 'Cancelar matrícula']));
    expect(suspensa).not.toContain('Suspender');
    TestBed.resetTestingModule();
    const cancelada = textosDosBotoes(montar(matricula('CANCELADA')).el);
    expect(cancelada).not.toContain('Cancelar matrícula');
    expect(cancelada).not.toContain('Renovar');
  });

  it('pagarSoNaMaisAntiga', () => {
    const { el } = montar(matricula('VENCIDA', [
      cobranca('c1', '2026-09-01', 'PAGO'),
      cobranca('c2', '2026-10-01', 'ATRASADO'),
      cobranca('c3', '2026-11-01', 'ATRASADO'),
    ]));
    const pagar = Array.from(el.querySelectorAll('button'))
      .filter((b) => b.textContent?.trim() === 'Registrar pagamento') as HTMLButtonElement[];
    expect(pagar.length).toBe(2);
    expect(pagar.map((b) => b.disabled)).toEqual([false, true]);
  });

  it('abonarAteAquiEmLote', () => {
    const { el, api } = montar(matricula('VENCIDA', [
      cobranca('c2', '2026-10-01', 'ATRASADO'),
      cobranca('c3', '2026-11-01', 'ATRASADO'),
    ]), 'Meses parados');
    const abonar = Array.from(el.querySelectorAll('button'))
      .filter((b) => b.textContent?.trim() === 'Abonar até aqui') as HTMLButtonElement[];
    expect(abonar.length).toBe(2);
    abonar[1].click();
    expect(api.abonar).toHaveBeenCalledWith('m1', 'c3', 'Meses parados');
  });

  it('suspenderPedeMotivo', () => {
    const { el, api, dialogo } = montar(matricula('ATIVA'), 'Viagem');
    botao(el, 'Suspender').click();
    expect(dialogo.open).toHaveBeenCalled();
    expect(api.suspender).toHaveBeenCalledWith('m1', 'Viagem');
  });

  it('dialogoCanceladoNaoFazNada', () => {
    const { el, api } = montar(matricula('ATIVA'), undefined);
    botao(el, 'Cancelar matrícula').click();
    expect(api.cancelar).not.toHaveBeenCalled();
  });

  it('registrarPagamentoRecarrega', () => {
    const { el, api } = montar(matricula('PENDENTE', [cobranca('c1', '2026-11-01', 'PENDENTE')]),
      { dataPagamento: '2026-11-23', forma: 'PIX', observacoes: null });
    botao(el, 'Registrar pagamento').click();
    expect(api.registrarPagamento).toHaveBeenCalledWith('c1', { dataPagamento: '2026-11-23', forma: 'PIX', observacoes: null });
    expect(api.matricula).toHaveBeenCalledTimes(2);
  });
});
