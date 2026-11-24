import { HttpErrorResponse } from '@angular/common/http';
import { Component, output } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LeitorQr } from '../../../componentes/leitor-qr/leitor-qr';
import { AgendaApi, AulaAberta, FrequenciaApi, ResultadoCheckin } from '../../../core/api/frequencia';
import { Recepcao } from './recepcao';

@Component({ selector: 'app-leitor-qr', template: '' })
class LeitorFalso {
  readonly lido = output<string>();
  readonly semCamera = output<void>();
}

const aula = (id: string, tipo: string): AulaAberta => ({ horarioId: id, tipo, professorNome: 'Ana', inicio: '19:00', fim: '20:00' });

const OK: ResultadoCheckin = {
  checkinId: 'c1', aluno: { id: 'a1', nome: 'Carla Souza' }, aula: aula('h1', 'Gi'), livre: false, pontualidade: 'PONTUAL', alerta: null,
};

function erro(codigo: string, extras: object = {}) {
  return new HttpErrorResponse({ status: 409, error: { codigo, detail: 'x', ...extras } });
}

describe('Recepcao', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function montar(aulas: AulaAberta[], checkin = vi.fn(() => of(OK))) {
    const agenda = { aulasAbertas: vi.fn(() => of(aulas)) };
    const frequencia = { checkinQr: checkin };
    TestBed.configureTestingModule({
      imports: [Recepcao],
      providers: [{ provide: AgendaApi, useValue: agenda }, { provide: FrequenciaApi, useValue: frequencia }],
    });
    TestBed.overrideComponent(Recepcao, { remove: { imports: [LeitorQr] }, add: { imports: [LeitorFalso] } });
    const fixture = TestBed.createComponent(Recepcao);
    fixture.detectChanges();
    return { fixture, comp: fixture.componentInstance, agenda, checkin };
  }

  it('umaAulaAbertaFicaSelecionadaMasOServidorDecide', () => {
    const { comp, checkin } = montar([aula('h1', 'Gi')]);
    expect(comp.selecionada()).toBe('h1');
    comp.ler('tok');
    expect(checkin).toHaveBeenCalledWith('tok', null);
  });

  it('segundaAulaAbertaNaoHerdaASelecaoAutomatica', () => {
    const { comp, agenda, checkin } = montar([aula('h1', 'Gi')]);
    agenda.aulasAbertas.mockReturnValue(of([aula('h1', 'Gi'), aula('h2', 'No-Gi')]));
    vi.advanceTimersByTime(60_000);
    expect(comp.selecionada()).toBeNull();
    comp.ler('tok');
    expect(checkin).toHaveBeenCalledWith('tok', null);
  });

  it('releituraDeCheckinRegistradoNaoViraErro', () => {
    let n = 0;
    const { comp, checkin } = montar([aula('h1', 'Gi')],
      vi.fn(() => (++n === 1 ? of(OK) : throwError(() => erro('CHECKIN_DUPLICADO')))));
    comp.ler('tok');
    vi.advanceTimersByTime(6_000);
    comp.ler('tok');
    expect(checkin).toHaveBeenCalledTimes(1);
    expect(comp.resultado()?.tipo).toBe('ok');
  });

  it('duasAbertasExigemEscolha', () => {
    const { fixture, comp, checkin } = montar([aula('h1', 'Gi'), aula('h2', 'Kids')], vi.fn(() => throwError(() => erro('AULA_AMBIGUA'))));
    expect(comp.selecionada()).toBeNull();
    comp.ler('tok');
    expect(checkin).toHaveBeenCalledWith('tok', null);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Escolha a aula');
  });

  it('escolhaEnviadaComoHorario', () => {
    const { comp, checkin } = montar([aula('h1', 'Gi'), aula('h2', 'Kids')]);
    comp.escolher('h2');
    comp.ler('tok');
    expect(checkin).toHaveBeenCalledWith('tok', 'h2');
  });

  it('resultadoVerde', () => {
    const { fixture, comp } = montar([aula('h1', 'Gi')]);
    comp.ler('tok');
    fixture.detectChanges();
    expect(comp.resultado()?.tipo).toBe('ok');
    expect(fixture.nativeElement.textContent).toContain('Carla Souza');
    expect(fixture.nativeElement.textContent).toContain('Pontual');
  });

  it('resultadoAmareloComAlerta', () => {
    const comAlerta = { ...OK, alerta: { vencimentoMaisAntigo: '2026-11-20', diasDeAtraso: 3 } };
    const { fixture, comp } = montar([aula('h1', 'Gi')], vi.fn(() => of(comAlerta)));
    comp.ler('tok');
    fixture.detectChanges();
    expect(comp.resultado()?.tipo).toBe('alerta');
    expect(fixture.nativeElement.textContent).toContain('Cobrança atrasada há 3 dias');
  });

  it('resultadoVermelhoComMensagemDoCodigo', () => {
    const { fixture, comp } = montar([aula('h1', 'Gi')], vi.fn(() => throwError(() => erro('MATRICULA_NAO_ATIVA', { status: 'VENCIDA' }))));
    comp.ler('tok');
    fixture.detectChanges();
    expect(comp.resultado()?.tipo).toBe('erro');
    expect(fixture.nativeElement.textContent).toContain('Matrícula vencida');
  });

  it('mesmoTokenEmMenosDe5sIgnorado', () => {
    const { comp, checkin } = montar([aula('h1', 'Gi')], vi.fn(() => throwError(() => erro('QR_EXPIRADO'))));
    comp.ler('tok');
    vi.advanceTimersByTime(4_000);
    comp.ler('tok');
    expect(checkin).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(2_000);
    comp.ler('tok');
    expect(checkin).toHaveBeenCalledTimes(2);
  });

  it('campoDeTokenEnvia', () => {
    const { comp, checkin } = montar([]);
    comp.tokenDigitado.setValue('  colado  ');
    comp.registrarDigitado();
    expect(checkin).toHaveBeenCalledWith('colado', null);
  });

  it('semCameraMostraAviso', () => {
    const { fixture, comp } = montar([]);
    comp.semCamera.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Câmera indisponível');
    expect(fixture.nativeElement.querySelector('input')).not.toBeNull();
  });

  it('recarregaAulasACadaMinuto', () => {
    const { agenda } = montar([]);
    vi.advanceTimersByTime(60_000);
    expect(agenda.aulasAbertas).toHaveBeenCalledTimes(2);
  });
});
