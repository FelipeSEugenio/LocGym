import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { FinanceiroApi } from '../../../core/api/financeiro';
import { AlunosApi } from '../../../core/api/cadastros';
import { AlunoForm } from './aluno-form';

const ALUNO = {
  id: 'al1', nome: 'Ana', email: 'ana@x.com', cpf: '52998224725', telefone: '11999', dataNascimento: '1990-01-01',
  endereco: 'Rua A', cidade: 'SP', estado: 'SP', cep: '01001000', status: 'ATIVO',
};

describe('AlunoForm', () => {
  function montar(id: string | null, matricula: Observable<unknown> = throwError(() => new HttpErrorResponse({ status: 404 }))) {
    const api = { obter: vi.fn(() => of(ALUNO)), criar: vi.fn(() => of(ALUNO)), atualizar: vi.fn(() => of(ALUNO)) };
    const financeiro = { matriculaDoAluno: vi.fn(() => matricula) };
    TestBed.configureTestingModule({
      imports: [AlunoForm],
      providers: [
        provideRouter([]),
        { provide: AlunosApi, useValue: api },
        { provide: FinanceiroApi, useValue: financeiro },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } } },
      ],
    });
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(AlunoForm);
    fixture.detectChanges();
    return { fixture, api, componente: fixture.componentInstance };
  }

  it('cpfInvalidoBloqueiaEnvio', () => {
    const { componente, api } = montar(null);
    componente.form.patchValue({
      nome: 'Novo', email: 'novo@x.com', cpf: '52998224724', telefone: '11999', dataNascimento: '1990-01-01',
      endereco: 'Rua', cidade: 'SP', estado: 'SP', cep: '01001000', senhaInicial: 'inicial-123',
    });
    componente.salvar();
    expect(componente.form.controls.cpf.hasError('cpf')).toBe(true);
    expect(api.criar).not.toHaveBeenCalled();
  });

  it('emailDesabilitadoNaEdicao', () => {
    const { componente, api } = montar('al1');
    expect(api.obter).toHaveBeenCalledWith('al1');
    expect(componente.form.controls.email.disabled).toBe(true);
    expect(componente.form.controls.email.value).toBe('ana@x.com');
    expect(componente.form.controls.senhaInicial.disabled).toBe(true);
  });

  it('edicaoNaoEnviaEmail', () => {
    const { componente, api } = montar('al1');
    componente.salvar();
    expect(api.atualizar).toHaveBeenCalledWith('al1', expect.not.objectContaining({ email: expect.anything() }));
  });

  it('cartaoMatriculaAtual', () => {
    const { fixture } = montar('a1', of({ id: 'm1', status: 'ATIVA', vigenteAte: '2026-12-01', inadimplente: true,
      plano: { id: 'p1', nome: 'Mensal' } }));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Ativa');
    expect(el.textContent).toContain('01/12/2026');
    expect(el.textContent).toContain('Inadimplente');
    expect(el.querySelector('a[href="/admin/matriculas/m1"]')).not.toBeNull();
  });

  it('semMatriculaOfereceMatricular', () => {
    const { fixture } = montar('a1');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Sem matrícula');
    expect(el.querySelector('a[href="/admin/matriculas/nova?alunoId=a1"]')?.textContent).toContain('Matricular');
  });
});
