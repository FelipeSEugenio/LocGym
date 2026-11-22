package com.locgym.identidade.web;

import com.locgym.identidade.CriadorDeAcademia.NovaAcademia;
import com.locgym.identidade.aplicacao.ServicoAutenticacao;
import com.locgym.identidade.aplicacao.ServicoCadastroAdministrador;
import com.locgym.identidade.aplicacao.ServicoSessao;
import com.locgym.identidade.aplicacao.Sessao;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    record LoginRequisicao(@NotBlank String email, @NotBlank String senha) {}

    record AcademiaRequisicao(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @Pattern(regexp = "\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}", message = "CNPJ inválido") String cnpj,
            @NotBlank @Size(max = 200) String endereco,
            @NotBlank @Size(max = 100) String cidade,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "UF inválida") String estado,
            @NotBlank @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido") String cep,
            @NotBlank @Size(max = 20) String telefone,
            @NotBlank @Size(max = 254) String email) {

        NovaAcademia paraNovaAcademia() {
            return new NovaAcademia(nome.strip(), cnpj.replaceAll("\\D", ""), endereco.strip(), cidade.strip(),
                    estado.toUpperCase(Locale.ROOT), cep.replaceAll("\\D", ""), telefone.strip(),
                    email.strip().toLowerCase(Locale.ROOT));
        }
    }

    record CadastroAdministradorRequisicao(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres") String senha,
            @NotNull @Valid AcademiaRequisicao academia) {}

    record TrocaDeSenhaRequisicao(
            @NotBlank String senhaAtual,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres") String novaSenha) {}

    static final String COOKIE = "locgym_refresh";

    private final ServicoAutenticacao autenticacao;
    private final ServicoCadastroAdministrador cadastro;
    private final ServicoSessao sessoes;

    AuthController(ServicoAutenticacao autenticacao, ServicoCadastroAdministrador cadastro, ServicoSessao sessoes) {
        this.autenticacao = autenticacao;
        this.cadastro = cadastro;
        this.sessoes = sessoes;
    }

    @PostMapping("/login")
    ResponseEntity<SessaoResposta> login(@Valid @RequestBody LoginRequisicao req) {
        return comNovaSessao(HttpStatus.OK, autenticacao.login(req.email(), req.senha()));
    }

    @PostMapping("/cadastro-administrador")
    ResponseEntity<SessaoResposta> cadastrarAdministrador(@Valid @RequestBody CadastroAdministradorRequisicao req) {
        return comNovaSessao(HttpStatus.CREATED, cadastro.cadastrar(req.nome(), req.email(), req.senha(),
                req.academia().paraNovaAcademia()));
    }

    @PostMapping("/refresh")
    ResponseEntity<SessaoResposta> renovar(@CookieValue(name = COOKIE, required = false) String cookie) {
        var renovacao = sessoes.renovar(cookie);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(renovacao.novoValor(), ServicoSessao.VALIDADE).toString())
                .body(SessaoResposta.de(autenticacao.sessaoPara(renovacao.usuarioId())));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String cookie) {
        sessoes.encerrar(cookie);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", java.time.Duration.ZERO).toString())
                .build();
    }

    /** Troca a senha, derruba as outras sessões e devolve uma sessão nova (com novo cookie). */
    @PostMapping("/trocar-senha")
    ResponseEntity<SessaoResposta> trocarSenha(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody TrocaDeSenhaRequisicao req) {
        return comNovaSessao(HttpStatus.OK, autenticacao.trocarSenha(UUID.fromString(jwt.getSubject()),
                req.senhaAtual(), req.novaSenha()));
    }

    private ResponseEntity<SessaoResposta> comNovaSessao(HttpStatus status, Sessao sessao) {
        String refresh = sessoes.iniciar(sessao.usuario().id());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(refresh, ServicoSessao.VALIDADE).toString())
                .body(SessaoResposta.de(sessao));
    }

    /** Cookie do refresh: inacessível ao JavaScript, só HTTPS (localhost é exceção nos navegadores), só /api/v1/auth. */
    private static ResponseCookie cookie(String valor, java.time.Duration validade) {
        return ResponseCookie.from(COOKIE, valor)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(validade)
                .build();
    }
}
