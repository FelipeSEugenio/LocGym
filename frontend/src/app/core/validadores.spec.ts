import { FormControl, FormGroup } from '@angular/forms';
import { confirmaSenha, cpfValido, senhaMinima } from './validadores';

describe('validadores', () => {
  it('cpfValidoComESemMascara', () => {
    expect(cpfValido()(new FormControl('52998224725'))).toBeNull();
    expect(cpfValido()(new FormControl('529.982.247-25'))).toBeNull();
  });

  it('cpfInvalido', () => {
    expect(cpfValido()(new FormControl('52998224724'))).toEqual({ cpf: true });
    expect(cpfValido()(new FormControl('111.111.111-11'))).toEqual({ cpf: true });
    expect(cpfValido()(new FormControl('1234567890'))).toEqual({ cpf: true });
  });

  it('cpfVazioFicaParaORequired', () => {
    expect(cpfValido()(new FormControl(''))).toBeNull();
  });

  it('senhaCurta', () => {
    expect(senhaMinima(8)(new FormControl('1234567'))).toEqual({ senhaCurta: { minimo: 8 } });
    expect(senhaMinima(8)(new FormControl('12345678'))).toBeNull();
  });

  it('confirmacaoDiferente', () => {
    const grupo = new FormGroup({ senha: new FormControl('senha-forte-1'), confirmacao: new FormControl('outra') });
    expect(confirmaSenha('senha', 'confirmacao')(grupo)).toEqual({ confirmacaoDiferente: true });
    grupo.controls.confirmacao.setValue('senha-forte-1');
    expect(confirmaSenha('senha', 'confirmacao')(grupo)).toBeNull();
  });
});
