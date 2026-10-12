-- Identity of the user at the OpenID Connect provider (Keycloak). Null = password login only.
alter table users add column oidc_subject text unique;
