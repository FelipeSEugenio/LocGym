import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AcademiaApi } from '../../../core/api/cadastros';
import { AuthService } from '../../../core/auth/auth.service';
import { AcademiaPagina } from './academia';

const ACADEMIA = {
  id: 'ac1', nome: 'Academia', cnpj: '11222333000181', endereco: 'Rua', cidade: 'SP', estado: 'SP', cep: '01001000',
  telefone: '11999990000', email: 'a@a.com', status: 'ATIVA' as const, diasAntecedenciaCobranca: 7, diasCarencia: 10,
  toleranciaPontualidade: 10,
};

describe('AcademiaPagina', () => {
  function montar() {
    const api = { ativa: vi.fn(() => of(ACADEMIA)), atualizar: vi.fn(() => of(ACADEMIA)), alterarStatus: vi.fn() };
    TestBed.configureTestingModule({
      imports: [AcademiaPagina],
      providers: [{ provide: AcademiaApi, useValue: api }, { provide: AuthService, useValue: { atualizarVinculos: () => of(null) } }],
    });
    const fixture = TestBed.createComponent(AcademiaPagina);
    fixture.detectChanges();
    return { comp: fixture.componentInstance, api };
  }

  it('enviaParametrosDeCobranca', () => {
    const { comp, api } = montar();
    expect(comp.parametros.getRawValue()).toEqual({ diasAntecedenciaCobranca: 7, diasCarencia: 10 });
    comp.parametros.setValue({ diasAntecedenciaCobranca: 3, diasCarencia: 0 });
    comp.salvar();
    expect(api.atualizar).toHaveBeenCalledWith(expect.objectContaining({ diasAntecedenciaCobranca: 3, diasCarencia: 0 }));
  });

  it('parametrosForaDoLimiteBloqueiamEnvio', () => {
    const { comp, api } = montar();
    comp.parametros.setValue({ diasAntecedenciaCobranca: 31, diasCarencia: 10 });
    comp.salvar();
    expect(api.atualizar).not.toHaveBeenCalled();
  });

  it('enviaToleranciaDePontualidade', () => {
    const { comp, api } = montar();
    expect(comp.frequencia.getRawValue()).toEqual({ toleranciaPontualidade: 10 });
    comp.frequencia.setValue({ toleranciaPontualidade: 0 });
    comp.salvar();
    expect(api.atualizar).toHaveBeenCalledWith(expect.objectContaining({ toleranciaPontualidade: 0 }));
  });

  it('toleranciaForaDoLimiteBloqueiaEnvio', () => {
    const { comp, api } = montar();
    comp.frequencia.setValue({ toleranciaPontualidade: 61 });
    comp.salvar();
    expect(api.atualizar).not.toHaveBeenCalled();
  });
});
