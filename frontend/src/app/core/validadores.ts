import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Mesma regra do backend: 11 dígitos, sem sequência repetida, dígitos verificadores corretos. */
export function cpfValido(): ValidatorFn {
  return (c: AbstractControl): ValidationErrors | null => {
    const bruto = (c.value ?? '').toString();
    if (!bruto) {
      return null;
    }
    const d = bruto.replace(/[.\-\s]/g, '');
    if (!/^\d{11}$/.test(d) || /^(\d)\1{10}$/.test(d)) {
      return { cpf: true };
    }
    const dv = (posicao: number) => {
      let soma = 0;
      for (let i = 0; i < posicao; i++) {
        soma += Number(d[i]) * (posicao + 1 - i);
      }
      return ((soma * 10) % 11) % 10;
    };
    return dv(9) === Number(d[9]) && dv(10) === Number(d[10]) ? null : { cpf: true };
  };
}

export function senhaMinima(minimo: number): ValidatorFn {
  return (c: AbstractControl): ValidationErrors | null =>
    (c.value ?? '').length >= minimo ? null : { senhaCurta: { minimo } };
}

/** Validador de grupo: os dois campos de senha precisam ser iguais. */
export function confirmaSenha(campoSenha: string, campoConfirmacao: string): ValidatorFn {
  return (g: AbstractControl): ValidationErrors | null =>
    g.get(campoSenha)?.value === g.get(campoConfirmacao)?.value ? null : { confirmacaoDiferente: true };
}
