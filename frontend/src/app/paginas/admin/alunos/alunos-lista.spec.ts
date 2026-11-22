import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AlunosApi } from '../../../core/api/cadastros';
import { Aluno } from '../../../core/api/modelos';
import { AlunosLista } from './alunos-lista';

const ALUNO: Aluno = {
  id: 'al1', nome: 'Ana Souza', email: 'ana@x.com', cpf: '52998224725', telefone: '1', dataNascimento: '1990-01-01',
  endereco: 'R', cidade: 'C', estado: 'SP', cep: '01001000', status: 'ATIVO',
};

describe('AlunosLista', () => {
  afterEach(() => vi.useRealTimers());

  function montar() {
    const api = { buscar: vi.fn(() => of({ itens: [ALUNO], pagina: 0, tamanho: 20, total: 1 })) };
    TestBed.configureTestingModule({
      imports: [AlunosLista],
      providers: [provideRouter([]), { provide: AlunosApi, useValue: api }],
    });
    const fixture = TestBed.createComponent(AlunosLista);
    fixture.detectChanges();
    return { fixture, api, el: fixture.nativeElement as HTMLElement };
  }

  it('renderizaAsLinhas', () => {
    const { el } = montar();
    expect(el.textContent).toContain('Ana Souza');
    expect(el.textContent).toContain('529.982.247-25');
    expect(el.textContent).toContain('Ativo');
  });

  it('buscaComDebounce', () => {
    vi.useFakeTimers();
    const { el, api } = montar();
    const campo = el.querySelector('input[name=busca]') as HTMLInputElement;

    campo.value = 'an';
    campo.dispatchEvent(new Event('input'));
    campo.value = 'ana';
    campo.dispatchEvent(new Event('input'));
    expect(api.buscar).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(300);
    expect(api.buscar).toHaveBeenCalledTimes(2);
    expect(api.buscar).toHaveBeenLastCalledWith('ana', 0, 20, undefined);
  });
});
