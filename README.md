# Taskboard

Gestor de projetos próprio, com a mesma organização do Taiga que a equipe já usa: histórias de usuário em
kanban ou em sprints (backlog e quadro de tarefas), tarefas, problemas, wiki, time, papéis com permissões,
anexos, comentários, histórico e busca. Tem API REST documentada, login local ou pelo Keycloak, e um servidor
MCP para que modelos de IA leiam e atualizem os projetos.

O nome `taskboard` é provisório. Telas, rotas e regras seguem o Taiga da empresa para ninguém precisar
reaprender a ferramenta, mas nenhum código, ícone, texto ou dado dele foi copiado.

## Estrutura

| Pasta | O que é |
| --- | --- |
| `backend/` | API REST em Java 21 e Spring Boot, com PostgreSQL e migrações Flyway |
| `frontend/` | Interface em Nuxt e Quasar, renderizada no servidor (SSR) |
| `mcp/` | Servidor MCP em TypeScript, ver [mcp/README.md](mcp/README.md) |
| `deploy/` | Docker Compose de produção, proxy com TLS e backup |
| `dev/` | Realm de desenvolvimento do Keycloak |
| `docs/` | Decisões: [login pelo Keycloak e MCP](docs/keycloak-e-mcp.md) |

## Telas e endereços

Os endereços são os mesmos do Taiga, então um link antigo só muda de domínio.

| Endereço | Tela |
| --- | --- |
| `/` | Painel: o que está atribuído a você e o que você observa |
| `/projects/` | Meus projetos, na ordem que você escolher |
| `/project/new` | Novo projeto, com modelo Scrum ou Kanban |
| `/project/SLUG/timeline` | Início do projeto: atividade e time |
| `/project/SLUG/backlog` | Scrum: backlog, sprints e gráfico |
| `/project/SLUG/taskboard/SPRINT` | Quadro de tarefas de uma sprint |
| `/project/SLUG/kanban` | Kanban das histórias de usuário |
| `/project/SLUG/issues` | Problemas |
| `/project/SLUG/us/N`, `/task/N`, `/issue/N` | História de usuário, tarefa ou problema número N |
| `/project/SLUG/wiki/PAGINA` | Wiki |
| `/project/SLUG/team`, `/search` | Time e busca |
| `/project/SLUG/admin/...` | Configurações: detalhes, módulos, status, pontos, valores dos problemas, tags, membros e papéis |
| `/profile`, `/user-settings/...`, `/notifications`, `/discover` | Perfil, conta, tokens de API, notificações e projetos públicos |

Cada projeto liga ou desliga os módulos Scrum, Kanban, Problemas e Wiki. Um projeto novo nasce com o fluxo
de status da equipe (de "A fazer" a "Arquivado"), a escala de pontos e os valores de tipo, gravidade e
prioridade. Tudo isso é editável nas configurações do projeto.

## Rodar em desenvolvimento

Requisitos: JDK 21, Node 22 ou mais novo e Docker.

```bash
# 1. Banco (PostgreSQL na porta 5433)
docker compose -f docker-compose.dev.yml up -d

# 2. API em http://localhost:8080 (documentação em /swagger-ui)
cd backend
SERVER_ADDRESS=127.0.0.1 APP_ADMIN_PASSWORD=admin12345 APP_COOKIE_SECURE=false ./gradlew bootRun

# 3. Interface em http://localhost:3000
cd frontend
npm install
npm run dev
```

`SERVER_ADDRESS=127.0.0.1` deixa a API de desenvolvimento acessível só nesta máquina.

Entre com `admin` e a senha definida em `APP_ADMIN_PASSWORD`. Sem essa variável, a senha é gerada e
aparece uma vez no log da API. O usuário `admin` só é criado quando a tabela de usuários está vazia.

Se a porta 3000 estiver ocupada, use `npm run dev -- --port 3010` e informe o endereço à API com
`APP_PUBLIC_URL=http://localhost:3010`.

Para ter o que ver, `python dev/seed-demo.py` cria um projeto Kanban, um projeto Scrum e quatro pessoas de
demonstração. A senha delas está no início do script.

### Login pelo Keycloak em desenvolvimento

```bash
docker compose -f docker-compose.dev.yml --profile sso up -d   # Keycloak em http://localhost:8081

cd backend
APP_OIDC_ISSUER=http://localhost:8081/realms/empresa \
APP_OIDC_CLIENT_SECRET=dev-only-taskboard-secret \
SERVER_ADDRESS=127.0.0.1 APP_ADMIN_PASSWORD=admin12345 APP_COOKIE_SECURE=false ./gradlew bootRun
```

A tela de login passa a mostrar "Entrar com Keycloak". O realm de desenvolvimento tem o usuário `maria`
com a senha `maria12345`. A conta local é criada no primeiro login.

## Testes

```bash
cd backend && ./gradlew test      # precisa do Docker: sobe um PostgreSQL real com Testcontainers
cd frontend && npm run typecheck
cd mcp && npm run check
```

## Configuração da API

Todas as opções são variáveis de ambiente, descritas em
[application.properties](backend/src/main/resources/application.properties). As principais:

| Variável | Para que serve |
| --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexão com o PostgreSQL |
| `APP_JWT_SECRET` | Assina o cookie de sessão. Pelo menos 32 caracteres. Vazio gera uma chave nova a cada partida |
| `APP_ADMIN_PASSWORD` | Senha do primeiro `admin` |
| `APP_PUBLIC_URL` | Endereço que as pessoas digitam no navegador |
| `APP_OIDC_ISSUER`, `APP_OIDC_CLIENT_ID`, `APP_OIDC_CLIENT_SECRET` | Login pelo Keycloak. Emissor vazio desliga |
| `APP_FILES_DIR` | Pasta onde ficam os anexos. Padrão: `./data/files` |
| `APP_MAX_UPLOAD` | Maior anexo aceito. Padrão: `20MB` |
| `APP_COOKIE_SECURE` | `false` só para testar sem HTTPS |

## Produção

Uma máquina com Docker basta: 2 vCPU e 4 GB de RAM.

```bash
cd deploy
cp .env.example .env      # preencha domínio, senhas e, se usar, o Keycloak
docker compose up -d --build
```

O Caddy atende em 80 e 443 e obtém o certificado sozinho quando o domínio tem DNS público. Para rede
interna, veja `TLS_DIRECTIVE` no `.env.example`.

No Keycloak, crie um client confidencial `taskboard` com o fluxo padrão e a URI de retorno
`https://SEU-DOMINIO/api/v1/auth/oidc/callback`.

O banco e os anexos ficam em volumes do Docker. O backup diário dos dois e a restauração estão descritos em
[deploy/backup.sh](deploy/backup.sh).

Redes com inspeção de TLS precisam do certificado da inspeção para montar as imagens: veja
[deploy/certs/README.md](deploy/certs/README.md).

## Papéis e permissões

Cada projeto tem seus papéis, e cada papel é uma lista de permissões: ver, criar, alterar, comentar e
excluir, separadamente para sprints, histórias de usuário, tarefas, problemas e wiki. Um projeto novo nasce
com quatro papéis, que o administrador pode mudar:

| Papel | Pode | E-mail |
| --- | --- | --- |
| Product Owner | Tudo | Toda movimentação |
| R2DA | Tarefas e problemas. Vê histórias, sprints e wiki, sem alterar. Em projeto com kanban também escreve histórias | Só o que o envolve |
| Líder de Projeto | Tudo | Toda movimentação |
| Consulta | Só ver | Só o que o envolve |

### Avisos por e-mail

Toda movimentação registrada no histórico vira e-mail: criação, alteração, mudança de status ou de sprint,
atribuição, comentário, anexo e exclusão de histórias, tarefas e problemas, e alterações na wiki.

- Quem tem um papel marcado com "Receber e-mail de toda movimentação do projeto" recebe tudo. Product Owner
  e Líder de Projeto nascem marcados; a opção fica em Configurações, Papéis.
- Os demais recebem só do item em que estão envolvidos: atribuídos a ele, observando ou autores dele.
- Quem fez a ação não recebe e-mail dela. Quem não pode ver aquele tipo de item também não.

Os e-mails ficam em uma fila no banco e saem em segundo plano. Se o servidor de e-mail estiver fora do ar,
cada mensagem é tentada de novo, com espera crescente, por cerca de cinco horas. Sem `MAIL_HOST` no `.env`
nenhum e-mail é gerado.

Além do papel, um membro pode ser administrador do projeto. Administradores mudam as configurações e têm
todas as permissões. Só o dono arquiva ou exclui o projeto.

Projeto privado só aparece para os membros. Projeto público pode ser lido por qualquer usuário logado.

## O que ainda não existe

Épicos, campos personalizados, raias no kanban, foto de perfil e logo enviados pelo usuário, escolha do
nível de e-mail por pessoa, resumo agrupado de e-mails, webhooks e importadores.
