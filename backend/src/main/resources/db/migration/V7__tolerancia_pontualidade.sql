-- Fase 4: minutos após o início da aula em que o check-in ainda é pontual.
alter table academia
    add column tolerancia_pontualidade_min int not null default 10
        check (tolerancia_pontualidade_min between 0 and 60);
