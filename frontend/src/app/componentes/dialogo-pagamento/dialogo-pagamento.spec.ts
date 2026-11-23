import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DialogoPagamento } from './dialogo-pagamento';

describe('DialogoPagamento', () => {
  afterEach(() => vi.useRealTimers());

  function montar() {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-23T15:00:00Z'));
    const ref = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [DialogoPagamento],
      providers: [
        { provide: MatDialogRef, useValue: ref },
        { provide: MAT_DIALOG_DATA, useValue: { valor: 150, vencimento: '2026-11-20' } },
      ],
    });
    const fixture = TestBed.createComponent(DialogoPagamento);
    fixture.detectChanges();
    return { comp: fixture.componentInstance, ref, el: fixture.nativeElement as HTMLElement };
  }

  it('padraoHoje', () => {
    const { comp, el } = montar();
    expect(comp.form.controls.dataPagamento.value).toBe('2026-11-23');
    expect(el.textContent).toMatch(/R\$\s*150,00/);
  });

  it('dataFuturaInvalida', () => {
    const { comp, ref } = montar();
    comp.form.patchValue({ dataPagamento: '2026-11-24', forma: 'PIX' });
    comp.confirmar();
    expect(comp.form.invalid).toBe(true);
    expect(ref.close).not.toHaveBeenCalled();
  });

  it('confirmaComOsDados', () => {
    const { comp, ref } = montar();
    comp.form.patchValue({ forma: 'DINHEIRO', observacoes: '  balcão ' });
    comp.confirmar();
    expect(ref.close).toHaveBeenCalledWith({ dataPagamento: '2026-11-23', forma: 'DINHEIRO', observacoes: 'balcão' });
  });
});
