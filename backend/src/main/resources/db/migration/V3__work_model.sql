-- Work model in the style the team already uses: user stories, tasks and issues share one table,
-- roles carry permission lists, and each project turns its modules on or off.

alter table users add column bio text not null default '';

alter table projects
    add column is_backlog_activated boolean not null default true,
    add column is_kanban_activated boolean not null default true,
    add column is_issues_activated boolean not null default true,
    add column is_wiki_activated boolean not null default true;

-- Roles -------------------------------------------------------------------------------------------------

create table roles (
    id bigint generated always as identity primary key,
    project_id bigint not null references projects (id) on delete cascade,
    name text not null,
    sort_order integer not null default 0,
    permissions text[] not null default '{}',
    unique (project_id, name)
);

insert into roles (project_id, name, sort_order, permissions)
select p.id, r.name, r.sort_order, r.permissions
from projects p
cross join (values
    ('Product Owner', 1, array[
        'view_project', 'view_milestones', 'add_milestone', 'modify_milestone', 'delete_milestone',
        'view_us', 'add_us', 'modify_us', 'comment_us', 'delete_us',
        'view_tasks', 'add_task', 'modify_task', 'comment_task', 'delete_task',
        'view_issues', 'add_issue', 'modify_issue', 'comment_issue', 'delete_issue',
        'view_wiki_pages', 'add_wiki_page', 'modify_wiki_page', 'delete_wiki_page']),
    ('Líder de Projeto', 2, array[
        'view_project', 'view_milestones', 'add_milestone', 'modify_milestone',
        'view_us', 'add_us', 'modify_us', 'comment_us', 'delete_us',
        'view_tasks', 'add_task', 'modify_task', 'comment_task', 'delete_task',
        'view_issues', 'add_issue', 'modify_issue', 'comment_issue', 'delete_issue',
        'view_wiki_pages', 'add_wiki_page', 'modify_wiki_page']),
    ('Equipe', 3, array[
        'view_project', 'view_milestones',
        'view_us', 'add_us', 'modify_us', 'comment_us', 'delete_us',
        'view_tasks', 'add_task', 'modify_task', 'comment_task', 'delete_task',
        'view_issues', 'add_issue', 'modify_issue', 'comment_issue', 'delete_issue',
        'view_wiki_pages']),
    ('Consulta', 4, array[
        'view_project', 'view_milestones', 'view_us', 'view_tasks', 'view_issues', 'view_wiki_pages'])
) as r (name, sort_order, permissions);

alter table memberships
    add column role_id bigint references roles (id),
    add column is_admin boolean not null default false,
    add column user_order integer not null default 0;

update memberships m
set is_admin = m.role in ('owner', 'admin'),
    role_id = (select r.id from roles r
               where r.project_id = m.project_id
                 and r.name = case m.role when 'viewer' then 'Consulta' when 'member' then 'Equipe'
                                          else 'Product Owner' end);

alter table memberships alter column role_id set not null;
alter table memberships drop column role;

create table project_fans (
    project_id bigint not null references projects (id) on delete cascade,
    user_id bigint not null references users (id) on delete cascade,
    kind text not null check (kind in ('like', 'watch')),
    primary key (project_id, user_id, kind)
);

-- Statuses per kind, lists of values ----------------------------------------------------------------------

alter table statuses drop constraint statuses_kind_check;
update statuses set kind = 'us' where kind = 'card';
alter table statuses add constraint statuses_kind_check check (kind in ('us', 'task', 'issue'));
alter table statuses add column is_archived boolean not null default false;
update statuses set is_archived = true where kind = 'us' and name = 'Arquivado';

insert into statuses (project_id, kind, name, color, sort_order, is_closed)
select project_id, 'issue', name, color, sort_order, is_closed from statuses where kind = 'task';

create table choices (
    id bigint generated always as identity primary key,
    project_id bigint not null references projects (id) on delete cascade,
    kind text not null check (kind in ('points', 'issue_type', 'severity', 'priority')),
    name text not null,
    color text not null default '#70728f',
    value numeric(6, 1),
    sort_order integer not null default 0,
    unique (project_id, kind, name)
);

insert into choices (project_id, kind, name, color, value, sort_order)
select p.id, c.kind, c.name, c.color, c.value, c.sort_order
from projects p
cross join (values
    ('points', '?', '#70728f', null::numeric, 1),
    ('points', '0', '#70728f', 0, 2),
    ('points', '1/2', '#70728f', 0.5, 3),
    ('points', '1', '#70728f', 1, 4),
    ('points', '2', '#70728f', 2, 5),
    ('points', '3', '#70728f', 3, 6),
    ('points', '5', '#70728f', 5, 7),
    ('points', '8', '#70728f', 8, 8),
    ('points', '10', '#70728f', 10, 9),
    ('points', '13', '#70728f', 13, 10),
    ('points', '20', '#70728f', 20, 11),
    ('points', '40', '#70728f', 40, 12),
    ('issue_type', 'Bug', '#e44057', null, 1),
    ('issue_type', 'Pergunta', '#5178d3', null, 2),
    ('issue_type', 'Melhoria', '#40e4ce', null, 3),
    ('severity', 'Baixa', '#70728f', null, 1),
    ('severity', 'Normal', '#40e47c', null, 2),
    ('severity', 'Importante', '#e4a240', null, 3),
    ('severity', 'Crítica', '#d31313', null, 4),
    ('priority', 'Baixa', '#a8e440', null, 1),
    ('priority', 'Normal', '#e4ce40', null, 2),
    ('priority', 'Alta', '#e47c40', null, 3)
) as c (kind, name, color, value, sort_order);

alter table milestones add column slug text;
update milestones set slug = 'sprint-' || id;
alter table milestones alter column slug set not null;
alter table milestones add constraint milestones_project_slug_key unique (project_id, slug);

-- Items: user stories, tasks and issues ------------------------------------------------------------------

create table items (
    id bigint generated always as identity primary key,
    project_id bigint not null references projects (id) on delete cascade,
    type text not null check (type in ('us', 'task', 'issue')),
    ref integer not null,
    subject text not null,
    description text not null default '',
    status_id bigint not null references statuses (id),
    milestone_id bigint references milestones (id) on delete set null,
    -- The user story a task belongs to.
    parent_id bigint references items (id) on delete cascade,
    points numeric(6, 1) check (points >= 0),
    assigned_users bigint[] not null default '{}',
    watchers bigint[] not null default '{}',
    created_by bigint references users (id) on delete set null,
    tags text[] not null default '{}',
    due_date date,
    type_id bigint references choices (id) on delete set null,
    severity_id bigint references choices (id) on delete set null,
    priority_id bigint references choices (id) on delete set null,
    -- Position on a board: kanban column for a user story, taskboard cell for a task.
    board_order integer not null default 0,
    -- Position in a list: backlog or sprint for a user story, the story's task list for a task.
    list_order integer not null default 0,
    is_blocked boolean not null default false,
    blocked_note text not null default '',
    team_requirement boolean not null default false,
    client_requirement boolean not null default false,
    is_iocaine boolean not null default false,
    version integer not null default 0,
    created_at timestamptz not null default now(),
    modified_at timestamptz not null default now(),
    finished_at timestamptz,
    search_vector tsvector generated always as (
        to_tsvector('portuguese'::regconfig, subject || ' ' || description)) stored,
    legacy_card bigint,
    legacy_task bigint,
    unique (project_id, ref)
);

insert into items (project_id, type, ref, subject, description, status_id, milestone_id, points, assigned_users,
                   created_by, tags, due_date, board_order, list_order, is_blocked, blocked_note, version,
                   created_at, modified_at, finished_at, legacy_card)
select project_id, 'us', ref, subject, description, status_id, milestone_id, points,
       case when assigned_to is null then '{}'::bigint[] else array[assigned_to] end,
       created_by, tags, due_date, kanban_order, kanban_order, is_blocked, blocked_note, version,
       created_at, modified_at, finished_at, id
from cards;

insert into items (project_id, type, ref, subject, description, status_id, milestone_id, parent_id,
                   assigned_users, created_by, due_date, board_order, list_order, version,
                   created_at, modified_at, finished_at, legacy_task)
select t.project_id, 'task', t.ref, t.subject, t.description, t.status_id, us.milestone_id, us.id,
       case when t.assigned_to is null then '{}'::bigint[] else array[t.assigned_to] end,
       t.created_by, t.due_date, t.sort_order, t.sort_order, t.version,
       t.created_at, t.modified_at, t.finished_at, t.id
from tasks t
join items us on us.legacy_card = t.card_id;

create index items_board_idx on items (project_id, type, status_id, board_order);
create index items_milestone_idx on items (milestone_id);
create index items_parent_idx on items (parent_id, list_order);
create index items_search_idx on items using gin (search_vector);

alter table comments add column item_id bigint references items (id) on delete cascade;
update comments c set item_id = i.id from items i where i.legacy_card = c.card_id;
update comments c set item_id = i.id from items i where i.legacy_task = c.task_id;
alter table comments drop column card_id, drop column task_id;
alter table comments alter column item_id set not null;
create index comments_item_idx on comments (item_id, created_at);

alter table history drop constraint history_target_type_check;
update history h set target_id = i.id from items i where h.target_type = 'card' and i.legacy_card = h.target_id;
update history h set target_id = i.id from items i where h.target_type = 'task' and i.legacy_task = h.target_id;
update history set target_type = 'us' where target_type = 'card';
alter table history add constraint history_target_type_check
    check (target_type in ('us', 'task', 'issue', 'wiki'));
create index history_actor_idx on history (actor_id, created_at desc);

drop table tasks;
drop table cards;
alter table items drop column legacy_card, drop column legacy_task;

-- Attachments, wiki, notifications -------------------------------------------------------------------------

create table attachments (
    id bigint generated always as identity primary key,
    project_id bigint not null references projects (id) on delete cascade,
    item_id bigint not null references items (id) on delete cascade,
    name text not null,
    size bigint not null,
    content_type text not null,
    -- File name inside the project's folder of the files directory.
    storage_key text not null unique,
    created_by bigint references users (id) on delete set null,
    created_at timestamptz not null default now()
);
create index attachments_item_idx on attachments (item_id, id);

create table wiki_pages (
    id bigint generated always as identity primary key,
    project_id bigint not null references projects (id) on delete cascade,
    slug text not null,
    content text not null default '',
    version integer not null default 0,
    last_modifier bigint references users (id) on delete set null,
    created_at timestamptz not null default now(),
    modified_at timestamptz not null default now(),
    unique (project_id, slug)
);

create table notifications (
    id bigint generated always as identity primary key,
    user_id bigint not null references users (id) on delete cascade,
    project_id bigint not null references projects (id) on delete cascade,
    actor_id bigint references users (id) on delete set null,
    kind text not null check (kind in ('assigned', 'comment')),
    item_type text not null,
    item_ref integer not null,
    item_subject text not null,
    created_at timestamptz not null default now(),
    read_at timestamptz
);
create index notifications_user_idx on notifications (user_id, created_at desc);
