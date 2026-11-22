-- Única leitura controlada que atravessa academias: os vínculos de um usuário (para login,
-- seletor de academia e validação do contexto de cada requisição).
-- SECURITY DEFINER: executa como o dono (ignora RLS); devolve só os vínculos do próprio usuário.
-- Aluno CANCELADO não tem vínculo (perde o acesso àquela academia).
create function vinculos_do_usuario(p_usuario uuid)
    returns table (academia_id uuid, academia_nome text, academia_status text, perfil text)
    language sql
    stable
    security definer
    set search_path = public
as $$
    select a.id, a.nome::text, a.status::text, 'ADMIN'
      from administrador_academia aa
      join academia a on a.id = aa.academia_id
     where aa.usuario_id = p_usuario
    union all
    select a.id, a.nome::text, a.status::text, 'ALUNO'
      from aluno al
      join academia a on a.id = al.academia_id
     where al.usuario_id = p_usuario
       and al.status <> 'CANCELADO'
$$;

revoke all on function vinculos_do_usuario(uuid) from public;
grant execute on function vinculos_do_usuario(uuid) to ${app_usuario};
