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

    /** CPF aleatório com dígitos verificadores válidos. */
    public static String cpfValido() {
        int[] n = new int[11];
        for (int i = 0; i < 9; i++) n[i] = ThreadLocalRandom.current().nextInt(10);
        n[0] = 1 + ThreadLocalRandom.current().nextInt(9);
        for (int d = 9; d <= 10; d++) {
            int soma = 0;
            for (int i = 0; i < d; i++) soma += n[i] * (d + 1 - i);
            n[d] = (soma * 10) % 11 % 10;
        }
        StringBuilder sb = new StringBuilder();
        for (int x : n) sb.append(x);
        return sb.toString();
    }

    public static Map<String, Object> dadosAluno(String nome, String email, String cpf) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("nome", nome);
        a.put("email", email);
        a.put("cpf", cpf);
        a.put("telefone", "11988887777");
        a.put("dataNascimento", "1998-07-15");
        a.put("endereco", "Rua do Tatame, 10");
        a.put("cidade", "Rio de Janeiro");
        a.put("estado", "RJ");
        a.put("cep", "20040-002");
        a.put("senhaInicial", "inicial-123");
        return a;
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

    /**
     * Aluno cadastrado pelo admin com {@link #dadosAluno} (senha inicial "inicial-123"): entra, troca a senha
     * obrigatória e devolve o cliente com o contexto ALUNO na academia.
     */
    public static ClienteHttp comoAluno(ClienteHttp http, String email, UUID academiaId) {
        String token = http.post("/api/v1/auth/login", Map.of("email", email, "senha", "inicial-123")).texto("accessToken");
        String novo = http.comToken(token).post("/api/v1/auth/trocar-senha",
                Map.of("senhaAtual", "inicial-123", "novaSenha", "minha-senha-1")).texto("accessToken");
        return http.comToken(novo).comContexto(academiaId, "ALUNO");
    }

    public static ClienteHttp comoAdmin(ClienteHttp http, Admin admin) {
        return http.comToken(admin.token()).comContexto(admin.academiaId(), "ADMIN");
    }
}
