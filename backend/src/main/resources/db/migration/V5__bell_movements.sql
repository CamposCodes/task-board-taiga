-- The bell tells about every movement the e-mail tells about, to the same people: besides being assigned and
-- comments, it now holds what was created, changed and deleted, wiki pages included (item_type 'wiki', ref 0).
alter table notifications drop constraint notifications_kind_check;
alter table notifications add constraint notifications_kind_check
    check (kind in ('assigned', 'comment', 'create', 'change', 'delete'));
