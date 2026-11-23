-- Parâmetros de cobrança recorrente por academia (Fase 3).
alter table academia
    add column dias_antecedencia_cobranca int not null default 7
        check (dias_antecedencia_cobranca between 1 and 30),
    add column dias_carencia int not null default 10
        check (dias_carencia between 0 and 60);
