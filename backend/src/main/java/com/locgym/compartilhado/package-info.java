/**
 * Infraestrutura técnica compartilhada por todos os módulos (eventos/outbox, tratamento de erros web).
 * Módulo aberto: qualquer módulo de negócio pode usá-lo; ele não depende de nenhum módulo de negócio.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.locgym.compartilhado;

import org.springframework.modulith.ApplicationModule;
