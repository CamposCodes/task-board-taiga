create table users (
    id bigint generated always as identity primary key,
    username text not null unique,
    email text not null unique,
    full_name text not null,
    password_hash text not null,
    is_superuser boolean not null default false,
    is_active boolean not null default true,
    password_changed_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create table api_tokens (
    id bigint generated always as identity primary key,
    user_id bigint not null references users (id) on delete cascade,
    name text not null,
    token_hash text not null unique,
    prefix text not null,
    scopes text[] not null,
    expires_at timestamptz,
    last_used_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);
create index api_tokens_user_idx on api_tokens (user_id);

create table projects (
    id bigint generated always as identity primary key,
    slug text not null unique,
    name text not null,
    description text not null default '',
    owner_id bigint not null references users (id),
    is_private boolean not null default true,
    is_archived boolean not null default false,
    tags_colors jsonb not null default '{}',
    last_ref integer not null default 0,
    created_at timestamptz not null default now(),
    modified_at timestamptz not null default now()
);

create table memberships (
    id bigint generated always as identity primary key,
    project_id bigint not null references projects (id) on delete cascade,
    user_id bigint not null references users (id) on delete cascade,
    role text not null check (role in ('owner', 'admin', 'member', 'viewer')),
    created_at timestamptz not null default now(),
    unique (project_id, user_id)
);
create index memberships_user_idx on memberships (user_id);

create table statuses (
    id bigint generated always as identity primary key,
    project_id bigint not null references projects (id) on delete cascade,
    kind text not null check (kind in ('card', 'task')),
    name text not null,
    color text not null,
    sort_order integer not null default 0,
    is_closed boolean not null default false,
    wip_limit integer check (wip_limit > 0),
    unique (project_id, kind, name)
);

create table milestones (
    id bigint generated always as identity primary key,
    project_id bigint not null references projects (id) on delete cascade,
    name text not null,
    estimated_start date not null,
    estimated_finish date not null,
    is_closed boolean not null default false,
    created_at timestamptz not null default now(),
    modified_at timestamptz not null default now(),
    unique (project_id, name),
    check (estimated_finish >= estimated_start)
);

create table cards (
    id bigint generated always as identity primary key,
    project_id bigint not null references projects (id) on delete cascade,
    ref integer not null,
    subject text not null,
    description text not null default '',
    status_id bigint not null references statuses (id),
    milestone_id bigint references milestones (id) on delete set null,
    points numeric(6, 1) check (points >= 0),
    assigned_to bigint references users (id) on delete set null,
    created_by bigint references users (id) on delete set null,
    tags text[] not null default '{}',
    due_date date,
    kanban_order integer not null default 0,
    is_blocked boolean not null default false,
    blocked_note text not null default '',
    version integer not null default 0,
    created_at timestamptz not null default now(),
    modified_at timestamptz not null default now(),
    finished_at timestamptz,
    search_vector tsvector generated always as (
        to_tsvector('portuguese'::regconfig, subject || ' ' || description)) stored,
    unique (project_id, ref)
);
create index cards_board_idx on cards (project_id, status_id, kanban_order);
create index cards_milestone_idx on cards (milestone_id);
create index cards_search_idx on cards using gin (search_vector);

create table tasks (
    id bigint generated always as identity primary key,
    project_id bigint not null references projects (id) on delete cascade,
    card_id bigint not null references cards (id) on delete cascade,
    ref integer not null,
    subject text not null,
    description text not null default '',
    status_id bigint not null references statuses (id),
    assigned_to bigint references users (id) on delete set null,
    created_by bigint references users (id) on delete set null,
    sort_order integer not null default 0,
    due_date date,
    version integer not null default 0,
    created_at timestamptz not null default now(),
    modified_at timestamptz not null default now(),
    finished_at timestamptz,
    search_vector tsvector generated always as (
        to_tsvector('portuguese'::regconfig, subject || ' ' || description)) stored,
    unique (project_id, ref)
);
create index tasks_card_idx on tasks (card_id, sort_order);
create index tasks_search_idx on tasks using gin (search_vector);

create table comments (
    id bigint generated always as identity primary key,
    project_id bigint not null references projects (id) on delete cascade,
    card_id bigint references cards (id) on delete cascade,
    task_id bigint references tasks (id) on delete cascade,
    author_id bigint references users (id) on delete set null,
    body text not null,
    created_at timestamptz not null default now(),
    edited_at timestamptz,
    deleted_at timestamptz,
    check (num_nonnulls(card_id, task_id) = 1)
);
create index comments_card_idx on comments (card_id, created_at);
create index comments_task_idx on comments (task_id, created_at);

-- History outlives its target, so the target is not a foreign key.
create table history (
    id bigint generated always as identity primary key,
    project_id bigint not null references projects (id) on delete cascade,
    target_type text not null check (target_type in ('card', 'task')),
    target_id bigint not null,
    target_ref integer not null,
    target_subject text not null,
    actor_id bigint references users (id) on delete set null,
    via_token_id bigint references api_tokens (id) on delete set null,
    kind text not null check (kind in ('create', 'change', 'delete', 'comment')),
    diff jsonb not null default '{}',
    comment_id bigint references comments (id) on delete set null,
    created_at timestamptz not null default now()
);
create index history_project_idx on history (project_id, created_at desc);
create index history_target_idx on history (target_type, target_id, created_at);
