import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CheckinListado, FrequenciaApi } from '../../../core/api/frequencia';
import { FrequenciaLista } from './frequencia-lista';

const ITEM: CheckinListado = {
  id: 'c1', alunoId: 'a1', alunoNome: 'Carla', data: '2026-11-23', hora: '19:05', origem: 'QR', pontualidade: 'PONTUAL',
  status: 'ATIVO', motivo: null, aula: { horarioId: 'h1', tipo: 'Gi', professorNome: 'Ana', inicio: '19:00', fim: '20:00' },
};

describe('FrequenciaLista', () => {
  function montar(resultadoDialogo: unknown) {
    const api = {
      listar: vi.fn(() => of({ itens: [ITEM], pagina: 0, tamanho: 20, total: 1 })),
      excluir: vi.fn(() => of(undefined)),
      corrigir: vi.fn(() => of({})),
      manual: vi.fn(() => of({})),
    };
    const dialogo = { open: vi.fn(() => ({ afterClosed: () => of(resultadoDialogo) })) };
    TestBed.configureTestingModule({
      imports: [FrequenciaLista],
      providers: [provideRouter([]), { provide: FrequenciaApi, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ alunoId: 'a1' }) } } }],
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialogo });
    const fixture = TestBed.createComponent(FrequenciaLista);
    fixture.detectChanges();
    return { fixture, comp: fixture.componentInstance, api, dialogo };
  }

  it('filtraPeloAlunoDaUrl', () => {
    const { fixture, api } = montar(undefined);
    expect(api.listar).toHaveBeenCalledWith(expect.objectContaining({ alunoId: 'a1' }));
    expect(fixture.nativeElement.textContent).toContain('Carla');
    expect(fixture.nativeElement.textContent).toContain('19:00 Gi');
  });

  it('excluirPedeMotivo', () => {
    const { comp, api, dialogo } = montar('lançado errado');
    comp.excluir(ITEM);
    expect(dialogo.open).toHaveBeenCalled();
    expect(api.excluir).toHaveBeenCalledWith('c1', 'lançado errado');
  });

  it('corrigirEnviaODialogo', () => {
    const correcao = { data: '2026-11-23', hora: '19:20', horarioId: null, motivo: 'x' };
    const { comp, api } = montar(correcao);
    comp.corrigir(ITEM);
    expect(api.corrigir).toHaveBeenCalledWith('c1', correcao);
  });

  it('registrarPresenca', () => {
    const presenca = { alunoId: 'a1', data: '2026-11-23', hora: '19:05', horarioId: null };
    const { comp, api } = montar(presenca);
    comp.registrarPresenca();
    expect(api.manual).toHaveBeenCalledWith(presenca);
  });
});
