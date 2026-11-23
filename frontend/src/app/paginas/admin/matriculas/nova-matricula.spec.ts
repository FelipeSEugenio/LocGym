import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AlunosApi, PlanosApi } from '../../../core/api/cadastros';
import { FinanceiroApi } from '../../../core/api/financeiro';
import { NovaMatricula } from './nova-matricula';

describe('NovaMatricula', () => {
  afterEach(() => vi.useRealTimers());

  function montar(criar = vi.fn(() => of({ id: 'm1' }))) {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-23T15:00:00Z'));
    const financeiro = { criarMatricula: criar };
    const planos = { listar: vi.fn(() => of([
      { id: 'p1', nome: 'Mensal', descricao: null, valor: 150, periodicidade: 'MENSAL', status: 'ATIVO' },
      { id: 'p2', nome: 'Antigo', descricao: null, valor: 90, periodicidade: 'MENSAL', status: 'INATIVO' },
    ])) };
    const alunos = { buscar: vi.fn(() => of({ itens: [], pagina: 0, tamanho: 10, total: 0 })), obter: vi.fn() };
    TestBed.configureTestingModule({
      imports: [NovaMatricula],
      providers: [
        provideRouter([]),
        { provide: FinanceiroApi, useValue: financeiro },
        { provide: PlanosApi, useValue: planos },
        { provide: AlunosApi, useValue: alunos },
      ],
    });
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(NovaMatricula);
    fixture.detectChanges();
    return { fixture, comp: fixture.componentInstance, financeiro, navegar, el: fixture.nativeElement as HTMLElement };
  }

  it('soPlanosAtivos', () => {
    const { comp } = montar();
    expect(comp.planos().map((p) => p.id)).toEqual(['p1']);
  });

  it('valorPreenchidoComValorDoPlano', () => {
    const { comp } = montar();
    comp.form.controls.planoId.setValue('p1');
    expect(comp.form.controls.valor.value).toBe(150);
  });

  it('dataInicioForaDaJanelaInvalida', () => {
    const { comp, fixture, el } = montar();
    expect(comp.form.controls.dataInicio.value).toBe('2026-11-23');
    comp.form.controls.dataInicio.setValue('2026-12-24');
    comp.form.controls.dataInicio.markAsTouched();
    fixture.detectChanges();
    expect(comp.form.controls.dataInicio.invalid).toBe(true);
    expect(el.textContent).toContain('Até 30 dias antes ou depois de hoje');
    comp.form.controls.dataInicio.setValue('2026-12-23');
    expect(comp.form.controls.dataInicio.valid).toBe(true);
  });

  it('enviaEVaiParaODetalhe', () => {
    const { comp, financeiro, navegar } = montar();
    comp.form.patchValue({ alunoId: 'a1', planoId: 'p1' });
    comp.form.controls.valor.setValue(120);
    comp.salvar();
    expect(financeiro.criarMatricula).toHaveBeenCalledWith({ alunoId: 'a1', planoId: 'p1', dataInicio: '2026-11-23', valor: 120 });
    expect(navegar).toHaveBeenCalledWith('/admin/matriculas/m1');
  });

  it('conflito409MostraMensagem', () => {
    const erro = new HttpErrorResponse({ status: 409, error: { detail: 'Este aluno já possui matrícula', codigo: 'MATRICULA_JA_EXISTE' } });
    const { comp, fixture, el } = montar(vi.fn(() => throwError(() => erro)));
    comp.form.patchValue({ alunoId: 'a1', planoId: 'p1' });
    comp.salvar();
    fixture.detectChanges();
    expect(el.textContent).toContain('Este aluno já possui matrícula');
  });
});
