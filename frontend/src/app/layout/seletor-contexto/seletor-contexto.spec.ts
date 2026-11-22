import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { VINCULO_ADMIN, VINCULO_ALUNO } from '../../core/auth/testes-apoio';
import { ContextoService } from '../../core/contexto/contexto.service';
import { SeletorContexto } from './seletor-contexto';

describe('SeletorContexto', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [SeletorContexto],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { vinculos: () => [VINCULO_ADMIN, VINCULO_ALUNO], logout: () => of(undefined) } },
      ],
    });
  });

  it('listaOsVinculosComPerfil', () => {
    const fixture = TestBed.createComponent(SeletorContexto);
    fixture.detectChanges();
    const opcoes = fixture.componentInstance.opcoes();
    expect(opcoes.map((o) => o.rotulo)).toEqual(['Academia Um — Administrador', 'Academia Dois — Aluno']);
  });

  it('escolherAlunoNavegaParaOPortal', () => {
    const fixture = TestBed.createComponent(SeletorContexto);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    fixture.componentInstance.escolher(VINCULO_ALUNO);

    expect(TestBed.inject(ContextoService).atual()).toEqual({ academiaId: 'a2', perfil: 'ALUNO' });
    expect(navegar).toHaveBeenCalledWith('/aluno');
  });
});
