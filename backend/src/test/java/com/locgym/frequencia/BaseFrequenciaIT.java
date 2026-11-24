package com.locgym.frequencia;

import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static java.time.LocalTime.of;

import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.CalendarioDeTeste;
import com.locgym.apoio.Cenarios.Admin;
import com.locgym.apoio.ClienteHttp;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
import com.locgym.frequencia.dominio.TokenCheckin;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

/** Cenário comum: segunda 2026-11-23, horário seg 19:00–20:00 "Gi", aluno ATIVO com matrícula ATIVA desde 01/11. */
abstract class BaseFrequenciaIT {

    static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 23);

    @LocalServerPort int porta;
    @Autowired JsonMapper json;
    @Autowired BancoSuperusuario su;
    @Autowired CalendarioDeTeste calendario;
    @Autowired TokenCheckin tokens;

    ClienteHttp http;
    Admin admin;
    ClienteHttp ctx;
    UUID professor;
    UUID plano;
    UUID horario;
    UUID aluno;

    @BeforeEach
    void prepararBase() {
        calendario.definir(SEGUNDA.atTime(19, 5));
        http = new ClienteHttp(porta, json);
        admin = novoAdministrador(http);
        ctx = comoAdmin(http, admin);
        professor = DadosDeTeste.professor(su.jdbc(), admin.academiaId(), "ATIVO");
        plano = DadosDeTeste.plano(su.jdbc(), admin.academiaId(), "Mensal");
        horario = horario(of(19, 0), of(20, 0), "Gi");
        aluno = alunoComMatricula("ATIVA", LocalDate.of(2026, 11, 1));
    }

    void agora(int hora, int minuto) {
        calendario.definir(SEGUNDA.atTime(hora, minuto));
    }

    void agora(LocalDateTime momento) {
        calendario.definir(momento);
    }

    UUID horario(LocalTime inicio, LocalTime fim, String tipo) {
        return DadosDeTeste.horario(su.jdbc(), admin.academiaId(), professor, 1, inicio, fim, tipo);
    }

    UUID alunoComMatricula(String status, LocalDate inicio) {
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), "freq-" + UUID.randomUUID() + "@teste.com");
        UUID a = DadosDeTeste.aluno(su.jdbc(), usuario, admin.academiaId(), "ATIVO");
        UUID m = DadosDeTeste.matricula(su.jdbc(), admin.academiaId(), a, plano, inicio);
        su.jdbc().update("update matricula set status = ?, suspensa_desde = ? where id = ?", status,
                "SUSPENSA".equals(status) ? inicio : null, m);
        return a;
    }

    String token(UUID alunoId) {
        return token(alunoId, admin.academiaId(), Instant.now());
    }

    String token(UUID alunoId, UUID academiaId, Instant emissao) {
        byte[] nonce = new byte[8];
        ThreadLocalRandom.current().nextBytes(nonce);
        return tokens.emitir(alunoId, academiaId, emissao, nonce);
    }

    ClienteHttp.Resposta qr(UUID alunoId) {
        return qr(token(alunoId), null);
    }

    ClienteHttp.Resposta qr(String token, UUID horarioId) {
        var corpo = new LinkedHashMap<String, Object>();
        corpo.put("token", token);
        if (horarioId != null) {
            corpo.put("horarioId", horarioId.toString());
        }
        return ctx.post("/api/v1/checkins/qr", corpo);
    }

    ClienteHttp.Resposta manual(UUID alunoId, LocalDate data, String hora, UUID horarioId) {
        var corpo = new LinkedHashMap<String, Object>(Map.of("alunoId", alunoId.toString(), "data", data.toString(),
                "hora", hora));
        if (horarioId != null) {
            corpo.put("horarioId", horarioId.toString());
        }
        return ctx.post("/api/v1/checkins", corpo);
    }

    int checkinsAtivos(UUID alunoId) {
        return su.jdbc().queryForObject("select count(*) from checkin where aluno_id = ? and status = 'ATIVO'",
                Integer.class, alunoId);
    }
}
