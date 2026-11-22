package com.locgym.identidade.web;

import com.locgym.identidade.CriadorDeAcademia.NovaAcademia;
import com.locgym.identidade.aplicacao.ServicoAutenticacao;
import com.locgym.identidade.aplicacao.ServicoCadastroAdministrador;
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
import org.springframework.web.bind.annotation.ResponseStatus;
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

    private final ServicoAutenticacao autenticacao;
    private final ServicoCadastroAdministrador cadastro;

    AuthController(ServicoAutenticacao autenticacao, ServicoCadastroAdministrador cadastro) {
        this.autenticacao = autenticacao;
        this.cadastro = cadastro;
    }

    @PostMapping("/login")
    SessaoResposta login(@Valid @RequestBody LoginRequisicao req) {
        return SessaoResposta.de(autenticacao.login(req.email(), req.senha()));
    }

    @PostMapping("/cadastro-administrador")
    @ResponseStatus(HttpStatus.CREATED)
    SessaoResposta cadastrarAdministrador(@Valid @RequestBody CadastroAdministradorRequisicao req) {
        return SessaoResposta.de(cadastro.cadastrar(req.nome(), req.email(), req.senha(),
                req.academia().paraNovaAcademia()));
    }
}
