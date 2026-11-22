package com.locgym.apoio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Montagem de requisições e cenários comuns aos testes de integração. */
public final class Cenarios {

    public static final String SENHA = "senha-forte-123";

    private Cenarios() {}

    public static String emailUnico(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
    }

    public static String cnpjAleatorio() {
        return String.format("%014d", ThreadLocalRandom.current().nextLong(1, 99_999_999_999_999L));
    }

    public static Map<String, Object> dadosAcademia(String nome) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("nome", nome);
        a.put("cnpj", cnpjAleatorio());
        a.put("endereco", "Rua das Faixas, 100");
        a.put("cidade", "São Paulo");
        a.put("estado", "SP");
        a.put("cep", "01001000");
        a.put("telefone", "11999990000");
        a.put("email", "contato@academia.com");
        return a;
    }

    public static Map<String, Object> cadastroAdministrador(String email, String senha) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("nome", "Dono da Academia");
        c.put("email", email);
        c.put("senha", senha);
        c.put("academia", dadosAcademia("Academia " + UUID.randomUUID().toString().substring(0, 6)));
        return c;
    }

    /** Administrador recém-cadastrado: token, id do usuário e academia criada. */
    public record Admin(String email, String token, UUID usuarioId, UUID academiaId) {}

    public static Admin novoAdministrador(ClienteHttp http) {
        String email = emailUnico("admin");
        var r = http.post("/api/v1/auth/cadastro-administrador", cadastroAdministrador(email, SENHA));
        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(201);
        return new Admin(email, r.texto("accessToken"),
                UUID.fromString(r.corpo().get("usuario").get("id").asString()),
                UUID.fromString(r.corpo().get("vinculos").get(0).get("academiaId").asString()));
    }

    public static ClienteHttp comoAdmin(ClienteHttp http, Admin admin) {
        return http.comToken(admin.token()).comContexto(admin.academiaId(), "ADMIN");
    }
}
