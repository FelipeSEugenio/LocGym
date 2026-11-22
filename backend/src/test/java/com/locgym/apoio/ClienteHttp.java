package com.locgym.apoio;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cliente HTTP dos testes de integração: nunca lança por status, devolve status, corpo JSON e headers. */
public class ClienteHttp {

    public record Resposta(int status, JsonNode corpo, HttpHeaders headers) {
        public String texto(String campo) {
            JsonNode n = corpo.get(campo);
            return n == null || n.isNull() ? null : n.asString();
        }
    }

    private final RestClient http;
    private final JsonMapper json;
    private final Map<String, String> headersFixos = new LinkedHashMap<>();
    private String token;

    public ClienteHttp(int porta, JsonMapper json) {
        this.json = json;
        this.http = RestClient.builder()
                .baseUrl("http://localhost:" + porta)
                .defaultStatusHandler(status -> true, (req, res) -> {})
                .build();
    }

    /** Novo cliente com o mesmo destino, autenticado com o token informado. */
    public ClienteHttp comToken(String token) {
        ClienteHttp c = copia();
        c.token = token;
        return c;
    }

    /** Novo cliente que envia o contexto (academia + perfil) em toda requisição. */
    public ClienteHttp comContexto(Object academiaId, String perfil) {
        ClienteHttp c = copia();
        c.headersFixos.put("X-Academia-Id", String.valueOf(academiaId));
        c.headersFixos.put("X-Perfil", perfil);
        return c;
    }

    public ClienteHttp comHeader(String nome, String valor) {
        ClienteHttp c = copia();
        c.headersFixos.put(nome, valor);
        return c;
    }

    public Resposta get(String caminho) {
        return executar(HttpMethod.GET, caminho, null);
    }

    public Resposta post(String caminho, Object corpo) {
        return executar(HttpMethod.POST, caminho, corpo);
    }

    public Resposta put(String caminho, Object corpo) {
        return executar(HttpMethod.PUT, caminho, corpo);
    }

    public Resposta patch(String caminho, Object corpo) {
        return executar(HttpMethod.PATCH, caminho, corpo);
    }

    public Resposta delete(String caminho) {
        return executar(HttpMethod.DELETE, caminho, null);
    }

    private ClienteHttp copia() {
        ClienteHttp c = new ClienteHttp(this.http, this.json);
        c.headersFixos.putAll(this.headersFixos);
        c.token = this.token;
        return c;
    }

    private ClienteHttp(RestClient http, JsonMapper json) {
        this.http = http;
        this.json = json;
    }

    private Resposta executar(HttpMethod metodo, String caminho, Object corpo) {
        var req = http.method(metodo).uri(caminho).headers(h -> {
            headersFixos.forEach(h::set);
            if (token != null) h.setBearerAuth(token);
        });
        if (corpo != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(corpo));
        }
        var entidade = req.retrieve().toEntity(String.class);
        String texto = entidade.getBody();
        JsonNode no = texto == null || texto.isBlank() ? json.createObjectNode() : json.readTree(texto);
        return new Resposta(entidade.getStatusCode().value(), no, entidade.getHeaders());
    }
}
