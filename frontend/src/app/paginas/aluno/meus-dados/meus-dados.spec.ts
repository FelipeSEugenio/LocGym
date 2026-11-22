import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AlunosApi } from '../../../core/api/cadastros';
import { MeusDados } from './meus-dados';

describe('MeusDados', () => {
  it('mostraOsDadosDoAluno', () => {
    TestBed.configureTestingModule({
      imports: [MeusDados],
      providers: [{
        provide: AlunosApi,
        useValue: { meusDados: () => of({
          id: 'al1', nome: 'Bia Ramos', email: 'bia@x.com', cpf: '52998224725', telefone: '11999',
          dataNascimento: '1998-07-15', endereco: 'Rua A', cidade: 'Rio', estado: 'RJ', cep: '20040002', status: 'INATIVO',
        }) },
      }],
    });
    const fixture = TestBed.createComponent(MeusDados);
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent;

    expect(texto).toContain('Bia Ramos');
    expect(texto).toContain('bia@x.com');
    expect(texto).toContain('529.982.247-25');
    expect(texto).toContain('Inativo');
  });
});
