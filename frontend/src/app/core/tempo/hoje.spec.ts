import { hojeEmSaoPaulo, somarDias } from './hoje';

describe('hojeEmSaoPaulo', () => {
  afterEach(() => vi.useRealTimers());

  it('usaDataDeSaoPauloNaViradaUtc', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-24T01:30:00Z')); // 22h30 do dia 23 em São Paulo
    expect(hojeEmSaoPaulo()).toBe('2026-11-23');
  });

  it('somaDiasEmDataIso', () => {
    expect(somarDias('2026-11-23', 30)).toBe('2026-12-23');
    expect(somarDias('2026-03-01', -1)).toBe('2026-02-28');
  });
});
