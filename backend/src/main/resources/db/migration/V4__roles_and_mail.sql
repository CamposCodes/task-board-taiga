-- Roles as in the setup the company already uses, and the e-mail that tells about every movement.

-- Who holds a role marked here gets an e-mail about everything that happens in the project;
-- the others only about what they are assigned to, watch or created.
alter table roles add column follows_all boolean not null default false;
update roles set follows_all = true where name in ('Product Owner', 'Líder de Projeto');

-- The team role takes the name and the place it has there, between the two roles that lead.
update roles r
set name = 'R2DA'
where r.name = 'Equipe'
  and not exists (select 1 from roles o where o.project_id = r.project_id and o.name = 'R2DA');

update roles
set sort_order = case name when 'Product Owner' then 1 when 'R2DA' then 2 when 'Líder de Projeto' then 3 else 4 end
where name in ('Product Owner', 'R2DA', 'Líder de Projeto', 'Consulta');

-- A project leader may do everything, like the product owner.
update roles
set permissions = array[
    'view_project', 'view_milestones', 'add_milestone', 'modify_milestone', 'delete_milestone',
    'view_us', 'add_us', 'modify_us', 'comment_us', 'delete_us',
    'view_tasks', 'add_task', 'modify_task', 'comment_task', 'delete_task',
    'view_issues', 'add_issue', 'modify_issue', 'comment_issue', 'delete_issue',
    'view_wiki_pages', 'add_wiki_page', 'modify_wiki_page', 'delete_wiki_page']
where name = 'Líder de Projeto';

-- Without a kanban, user stories are written by who leads: the team works on their tasks and on the issues.
update roles r
set permissions = array(select p from unnest(r.permissions) p
                        where p not in ('add_us', 'modify_us', 'comment_us', 'delete_us'))
from projects pr
where pr.id = r.project_id and r.name = 'R2DA' and not pr.is_kanban_activated;

-- E-mails waiting to be delivered. Each row is written in the same transaction as the movement it tells about.
create table mail_outbox (
    id bigserial primary key,
    recipient text not null,
    subject text not null,
    body_text text not null,
    body_html text not null,
    created_at timestamptz not null default now(),
    sent_at timestamptz,
    attempts integer not null default 0,
    last_try timestamptz,
    last_error text
);

create index mail_outbox_pending on mail_outbox (id) where sent_at is null;
