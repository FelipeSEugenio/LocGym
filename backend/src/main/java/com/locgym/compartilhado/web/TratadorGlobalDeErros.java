package com.locgym.compartilhado.web;

import com.locgym.compartilhado.erros.AcessoNegadoException;
import com.locgym.compartilhado.erros.NaoAutorizadoException;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte erros da API em Problem Details (RFC 7807).
 * Erros de validação listam os campos inválidos; erros inesperados nunca expõem detalhes internos.
 */
@RestControllerAdvice
public class TratadorGlobalDeErros extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TratadorGlobalDeErros.class);

    public record ErroDeCampo(String campo, String mensagem) {}

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErroDeCampo> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErroDeCampo(e.getField(), e.getDefaultMessage()))
                .toList();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, "Requisição inválida");
        problema.setTitle("Dados inválidos");
        problema.setProperty("erros", erros);
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    @ExceptionHandler(RegraVioladaException.class)
    public ProblemDetail regraViolada(RegraVioladaException ex) {
        ProblemDetail problema = comCodigo(HttpStatus.CONFLICT, "Operação não permitida", ex.getMessage(), ex.codigo());
        ex.extras().forEach(problema::setProperty);
        return problema;
    }

    /** Duas operações alteraram o mesmo registro ao mesmo tempo (versão otimista). */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ProblemDetail conflitoDeAtualizacao(org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
        return comCodigo(HttpStatus.CONFLICT, "Conflito", "Registro alterado por outra operação, recarregue",
                "CONFLITO_DE_ATUALIZACAO");
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ProblemDetail acessoNegado(AcessoNegadoException ex) {
        return comCodigo(HttpStatus.FORBIDDEN, "Acesso negado", ex.getMessage(), ex.codigo());
    }

    @ExceptionHandler(NaoEncontradoException.class)
    public ProblemDetail naoEncontrado(NaoEncontradoException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problema.setTitle("Não encontrado");
        return problema;
    }

    @ExceptionHandler(NaoAutorizadoException.class)
    public ProblemDetail naoAutorizado(NaoAutorizadoException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problema.setTitle("Não autorizado");
        return problema;
    }

    @ExceptionHandler(ValorInvalidoException.class)
    public ProblemDetail valorInvalido(ValorInvalidoException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Requisição inválida");
        problema.setTitle("Dados inválidos");
        problema.setProperty("erros", List.of(new ErroDeCampo(ex.campo(), ex.getMessage())));
        return problema;
    }

    private static ProblemDetail comCodigo(HttpStatus status, String titulo, String detalhe, String codigo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        problema.setProperty("codigo", codigo);
        return problema;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail erroInesperado(Exception ex) {
        log.error("Erro inesperado ao processar requisição", ex);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
        problema.setTitle("Erro interno");
        return problema;
    }
}
