import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { FrequenciaApi, TokenQr } from '../../../core/api/frequencia';
import { MeuQr } from './meu-qr';

describe('MeuQr', () => {
  const agora = new Date('2026-11-23T22:00:00Z');

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(agora);
  });

  afterEach(() => vi.useRealTimers());

  function token(n: number): TokenQr {
    return { token: `token-${n}`, expiraEm: new Date(Date.now() + 60_000).toISOString() };
  }

  async function montar(meuQr: () => ReturnType<FrequenciaApi['meuQr']>) {
    const api = { meuQr: vi.fn(meuQr) };
    TestBed.configureTestingModule({ imports: [MeuQr], providers: [{ provide: FrequenciaApi, useValue: api }] });
    const fixture = TestBed.createComponent(MeuQr);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    return { fixture, comp: fixture.componentInstance, api };
  }

  it('pedeTokenAoAbrirEDesenha', async () => {
    let n = 0;
    const { fixture, api } = await montar(() => of(token(++n)));
    expect(api.meuQr).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('.qr svg')).not.toBeNull();
  });

  it('renovaACada30sEContaRegressiva', async () => {
    let n = 0;
    const { fixture, comp, api } = await montar(() => of(token(++n)));
    expect(comp.restante()).toBe(60);
    await vi.advanceTimersByTimeAsync(10_000);
    expect(comp.restante()).toBe(50);
    await vi.advanceTimersByTimeAsync(20_000);
    fixture.detectChanges();
    expect(api.meuQr).toHaveBeenCalledTimes(2);
    expect(comp.token()?.token).toBe('token-2');
  });

  it('pausaComAbaOcultaERenovaAoVoltar', async () => {
    let n = 0;
    let oculto = false;
    vi.spyOn(document, 'hidden', 'get').mockImplementation(() => oculto);
    const { api } = await montar(() => of(token(++n)));
    oculto = true;
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(90_000);
    expect(api.meuQr).toHaveBeenCalledTimes(1);
    oculto = false;
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(0);
    expect(api.meuQr).toHaveBeenCalledTimes(2);
  });

  it('falhaNaRenovacaoAvisaESomeQuandoExpira', async () => {
    let n = 0;
    const { fixture, comp } = await montar(() => (++n === 1 ? of(token(n)) : throwError(() => new Error('rede'))));
    await vi.advanceTimersByTimeAsync(30_000);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível atualizar o QR Code');
    expect(comp.token()).not.toBeNull();
    await vi.advanceTimersByTimeAsync(31_000);
    fixture.detectChanges();
    expect(comp.token()).toBeNull();
    expect(fixture.nativeElement.querySelector('.qr svg')).toBeNull();
  });
});
