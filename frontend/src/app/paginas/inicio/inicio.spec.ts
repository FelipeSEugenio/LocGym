import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { EstadoBackend, SaudeService } from '../../core/saude/saude.service';
import { Inicio } from './inicio';

function renderizar(estado: EstadoBackend): HTMLElement {
  TestBed.configureTestingModule({
    imports: [Inicio],
    providers: [
      provideRouter([]),
      { provide: SaudeService, useValue: { verificar: () => of(estado) } },
    ],
  });
  const fixture = TestBed.createComponent(Inicio);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('Inicio', () => {
  it('exibeOnline', () => {
    const el = renderizar('online');
    expect(el.textContent).toContain('Backend online');
    const links = Array.from(el.querySelectorAll('a')).map((a) => [
      a.textContent?.trim(),
      a.getAttribute('href'),
    ]);
    expect(links).toContainEqual(['Painel administrativo', '/admin']);
    expect(links).toContainEqual(['Portal do aluno', '/aluno']);
  });

  it('exibeIndisponivelQuandoHealthFalha', () => {
    const erros = vi.spyOn(console, 'error');
    const el = renderizar('indisponivel');
    expect(el.textContent).toContain('Backend indisponível');
    expect(erros).not.toHaveBeenCalled();
  });
});
