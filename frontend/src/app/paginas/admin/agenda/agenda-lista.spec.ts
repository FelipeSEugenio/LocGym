import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AgendaApi, Horario } from '../../../core/api/frequencia';
import { AgendaLista } from './agenda-lista';

const h = (id: string, diaSemana: number, inicio: string, fim: string, tipo: string): Horario => ({
  id, professorId: 'p1', professorNome: 'Ana', diaSemana, inicio, fim, tipo, status: 'ATIVO',
});

describe('AgendaLista', () => {
  function montar(excluir = vi.fn(() => of(null))) {
    const api = {
      listar: vi.fn(() => of([h('a', 3, '07:00', '08:00', 'No-Gi'), h('b', 1, '19:00', '20:00', 'Gi'), h('c', 7, '10:00', '11:00', 'Kids')])),
      excluir,
    };
    TestBed.configureTestingModule({ imports: [AgendaLista], providers: [provideRouter([]), { provide: AgendaApi, useValue: api }] });
    const fixture = TestBed.createComponent(AgendaLista);
    fixture.detectChanges();
    return { fixture, comp: fixture.componentInstance, api };
  }

  it('agrupaPorDiaDeSegundaADomingo', () => {
    const { fixture, comp } = montar();
    expect(comp.dias().map((d) => d.rotulo)).toEqual(['Segunda', 'Quarta', 'Domingo']);
    expect(fixture.nativeElement.textContent).toContain('19:00–20:00 · Gi · Ana');
  });

  it('excluirComAulasMostraOrientacao', () => {
    const erro = new HttpErrorResponse({ status: 409, error: { codigo: 'HORARIO_COM_AULAS', detail: 'x' } });
    const { fixture, comp } = montar(vi.fn(() => throwError(() => erro)));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    comp.excluir(h('b', 1, '19:00', '20:00', 'Gi'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Este horário já teve aulas; inative-o');
  });
});
