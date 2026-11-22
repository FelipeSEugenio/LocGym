import { TestBed } from '@angular/core/testing';
import { VINCULO_ADMIN, VINCULO_ALUNO } from '../auth/testes-apoio';
import { ContextoService } from './contexto.service';

describe('ContextoService', () => {
  beforeEach(() => localStorage.clear());

  function criar(): ContextoService {
    TestBed.resetTestingModule();
    return TestBed.inject(ContextoService);
  }

  it('persisteERestaura', () => {
    criar().selecionar('a1', 'ADMIN');
    expect(criar().atual()).toEqual({ academiaId: 'a1', perfil: 'ADMIN' });
  });

  it('descartaContextoInvalido', () => {
    const servico = criar();
    servico.selecionar('sumiu', 'ADMIN');
    servico.sincronizar([VINCULO_ADMIN, VINCULO_ALUNO]);
    expect(servico.atual()).toBeNull();
    expect(localStorage.getItem('locgym.contexto')).toBeNull();
  });

  it('vinculoUnicoESelecionado', () => {
    const servico = criar();
    servico.sincronizar([VINCULO_ALUNO]);
    expect(servico.atual()).toEqual({ academiaId: 'a2', perfil: 'ALUNO' });
  });

  it('mantemContextoValido', () => {
    const servico = criar();
    servico.selecionar('a2', 'ALUNO');
    servico.sincronizar([VINCULO_ADMIN, VINCULO_ALUNO]);
    expect(servico.atual()).toEqual({ academiaId: 'a2', perfil: 'ALUNO' });
  });
});
