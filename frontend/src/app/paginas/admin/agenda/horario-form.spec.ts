import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { ProfessoresApi } from '../../../core/api/cadastros';
import { AgendaApi } from '../../../core/api/frequencia';
import { HorarioForm } from './horario-form';

describe('HorarioForm', () => {
  function montar() {
    const api = { criar: vi.fn(() => of({})), alterar: vi.fn(() => of({})), obter: vi.fn() };
    const professores = {
      buscar: vi.fn(() => of({ itens: [
        { id: 'p1', nome: 'Ana', status: 'ATIVO' }, { id: 'p2', nome: 'Bruno', status: 'INATIVO' },
      ], pagina: 0, tamanho: 100, total: 2 })),
    };
    TestBed.configureTestingModule({
      imports: [HorarioForm],
      providers: [provideRouter([]), { provide: AgendaApi, useValue: api }, { provide: ProfessoresApi, useValue: professores },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map() } } }],
    });
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(HorarioForm);
    fixture.detectChanges();
    return { comp: fixture.componentInstance, api };
  }

  it('soListaProfessoresAtivos', () => {
    const { comp } = montar();
    expect(comp.professores().map((p) => p.nome)).toEqual(['Ana']);
  });

  it('fimAntesDoInicioBloqueiaEnvio', () => {
    const { comp, api } = montar();
    comp.form.setValue({ professorId: 'p1', diaSemana: 1, inicio: '20:00', fim: '19:00', tipo: 'Gi', status: 'ATIVO' });
    comp.salvar();
    expect(api.criar).not.toHaveBeenCalled();
  });

  it('criaHorario', () => {
    const { comp, api } = montar();
    comp.form.setValue({ professorId: 'p1', diaSemana: 1, inicio: '19:00', fim: '20:00', tipo: ' Gi ', status: 'ATIVO' });
    comp.salvar();
    expect(api.criar).toHaveBeenCalledWith({ professorId: 'p1', diaSemana: 1, inicio: '19:00', fim: '20:00', tipo: 'Gi', status: 'ATIVO' });
  });
});
