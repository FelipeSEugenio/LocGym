import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { AlunosApi } from '../../core/api/cadastros';
import { AgendaApi, AulaAberta } from '../../core/api/frequencia';
import { DialogoPresenca } from './dialogo-presenca';

const aula = (id: string): AulaAberta => ({ horarioId: id, tipo: 'Gi', professorNome: 'Ana', inicio: '19:00', fim: '20:00' });

describe('DialogoPresenca', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-23T22:05:00Z')); // 19:05 em São Paulo
  });
  afterEach(() => vi.useRealTimers());

  function montar(aulas: AulaAberta[], dados: object = { modo: 'presenca' }) {
    const agenda = { aulasAbertas: vi.fn(() => of(aulas)) };
    const ref = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [DialogoPresenca],
      providers: [{ provide: AgendaApi, useValue: agenda }, { provide: AlunosApi, useValue: { buscar: vi.fn(() => of({ itens: [] })) } },
        { provide: MAT_DIALOG_DATA, useValue: dados }, { provide: MatDialogRef, useValue: ref }],
    });
    const fixture = TestBed.createComponent(DialogoPresenca);
    fixture.detectChanges();
    return { comp: fixture.componentInstance, agenda, ref };
  }

  it('padroesHojeEAgoraEmSaoPaulo', () => {
    const { comp, agenda } = montar([]);
    expect(comp.form.getRawValue().data).toBe('2026-11-23');
    expect(comp.form.getRawValue().hora).toBe('19:05');
    expect(agenda.aulasAbertas).toHaveBeenCalledWith({ data: '2026-11-23', hora: '19:05' });
  });

  it('mudarHoraBuscaAulasDeNovo', () => {
    const { comp, agenda } = montar([]);
    comp.form.controls.hora.setValue('20:10');
    vi.advanceTimersByTime(300);
    expect(agenda.aulasAbertas).toHaveBeenLastCalledWith({ data: '2026-11-23', hora: '20:10' });
  });

  it('variasAbertasExigemEscolha', () => {
    const { comp, ref } = montar([aula('h1'), aula('h2')]);
    comp.alunoId.set('a1');
    comp.confirmar();
    expect(ref.close).not.toHaveBeenCalled();
    comp.form.controls.horarioId.setValue('h2');
    comp.confirmar();
    expect(ref.close).toHaveBeenCalledWith({ alunoId: 'a1', data: '2026-11-23', hora: '19:05', horarioId: 'h2' });
  });

  it('umaAbertaEscolhidaAutomaticamente', () => {
    const { comp, ref } = montar([aula('h1')]);
    comp.alunoId.set('a1');
    comp.confirmar();
    expect(ref.close).toHaveBeenCalledWith(expect.objectContaining({ horarioId: 'h1' }));
  });

  it('correcaoExigeMotivo', () => {
    const { comp, ref } = montar([], { modo: 'correcao', data: '2026-11-23', hora: '19:05' });
    comp.confirmar();
    expect(ref.close).not.toHaveBeenCalled();
    comp.form.controls.motivo.setValue('chegou depois');
    comp.confirmar();
    expect(ref.close).toHaveBeenCalledWith({ data: '2026-11-23', hora: '19:05', horarioId: null, motivo: 'chegou depois' });
  });
});
