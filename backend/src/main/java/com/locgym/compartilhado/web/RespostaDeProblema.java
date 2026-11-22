package com.locgym.compartilhado.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/** Escreve um Problem Details direto na resposta — para filtros, que rodam fora dos controllers. */
public final class RespostaDeProblema {

    private RespostaDeProblema() {}

    public static void escrever(HttpServletResponse resposta, JsonMapper json, HttpStatus status,
                                String titulo, String detalhe, String codigo) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        if (codigo != null) {
            problema.setProperty("codigo", codigo);
        }
        resposta.setStatus(status.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        resposta.getWriter().write(json.writeValueAsString(problema));
    }
}
