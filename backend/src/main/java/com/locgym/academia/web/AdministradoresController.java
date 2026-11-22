package com.locgym.academia.web;

import com.locgym.academia.aplicacao.ServicoAdministradores;
import com.locgym.academia.dominio.Administrador;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/administradores")
class AdministradoresController {

    record ConviteRequisicao(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres") String senhaInicial) {}

    private final ServicoAdministradores servico;

    AdministradoresController(ServicoAdministradores servico) {
        this.servico = servico;
    }

    @GetMapping
    List<Administrador> listar() {
        return servico.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Administrador convidar(@Valid @RequestBody ConviteRequisicao req) {
        return servico.convidar(req.nome(), req.email(), req.senhaInicial());
    }

    @DeleteMapping("/{usuarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remover(@PathVariable UUID usuarioId) {
        servico.remover(usuarioId);
    }
}
