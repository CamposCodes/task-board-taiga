# Taskboard MCP

Servidor MCP do Taskboard: permite que um modelo de IA consulte e atualize projetos, histórias de
usuário, tarefas, problemas e sprints em nome de um usuário.

A organização do código segue a do [illodev/taiga-mcp](https://github.com/illodev/taiga-mcp) (MIT): um
cliente HTTP em `src/client.ts` e um arquivo de tools por recurso em `src/tools/`, registrados em
`src/index.ts`. A diferença central é a autenticação, explicada em
[docs/keycloak-e-mcp.md](../docs/keycloak-e-mcp.md).

## Autenticação

O servidor usa um **token pessoal** no lugar de usuário e senha. Funciona igual para quem entra pelo
Keycloak, que não tem senha no Taskboard.

1. Entre no Taskboard (Keycloak ou usuário local).
2. Abra o menu do usuário, "Perfil e tokens de API", e crie um token.
3. Marque "Permitir escrita" só se o modelo precisar criar ou alterar itens.

O token age com os mesmos projetos e permissões do dono. Tudo o que for alterado por ele aparece no histórico
como "via token". Um token somente leitura não enxerga as tools de escrita.

## Uso em um cliente MCP (stdio)

```bash
cd mcp
npm install
npm run build
```

Exemplo de `.mcp.json` na raiz de um projeto:

```json
{
  "mcpServers": {
    "taskboard": {
      "command": "node",
      "args": ["C:/caminho/para/mcp/dist/index.js"],
      "env": {
        "TASKBOARD_URL": "https://taskboard.exemplo.com.br",
        "TASKBOARD_TOKEN": "tb_..."
      }
    }
  }
}
```

Funciona com Node 18 ou mais novo.

No Claude Code, o mesmo registro em um comando, sem gravar o token no repositório:

```bash
claude mcp add taskboard -e TASKBOARD_URL=https://taskboard.exemplo.com.br -e TASKBOARD_TOKEN=tb_... -- node C:/caminho/para/mcp/dist/index.js
```

| Variável | Para que serve |
| --- | --- |
| `TASKBOARD_URL` | Endereço do Taskboard |
| `TASKBOARD_TOKEN` | Token pessoal (`tb_...`) |
| `TASKBOARD_READ_ONLY` | `1` esconde as tools de escrita mesmo com token de escrita |

## Uso compartilhado (HTTP)

`node dist/index.js --http` abre um único endereço para várias pessoas. Cada requisição traz o token de
quem chama em `Authorization: Bearer tb_...`, então cada pessoa age como ela mesma. Nada fica guardado
entre as requisições.

| Variável | Padrão | Para que serve |
| --- | --- | --- |
| `TASKBOARD_URL` | obrigatória | Endereço da API |
| `PORT` | `3333` | Porta |
| `HOST` | `127.0.0.1` | Interface de rede. Em contêiner use `0.0.0.0` |
| `MCP_PATH` | `/mcp` | Caminho do endereço |

No Docker Compose de produção: `docker compose --profile mcp up -d` publica em `https://DOMINIO/mcp`.

O modo HTTP roda em Node 18 ou mais novo. Se o cliente receber respostas truncadas ou um erro de
"chunk size", verifique o antivírus ou proxy da máquina cliente: alguns filtram tráfego MCP e corrompem
a resposta. O modo stdio não passa pela rede local e não é afetado.

## Tools

Projetos são identificados pelo slug. Histórias de usuário, tarefas e problemas são identificados pelo
número (`#12` é `ref: 12`), que é único no projeto. Status, sprints, pessoas e valores de problema são
informados pelo nome. Um nome inválido devolve a lista dos nomes aceitos.

| Tool | O que faz |
| --- | --- |
| `taskboard_me` | Usuário do token e se pode escrever |
| `taskboard_projects_list` | Projetos visíveis e o papel em cada um |
| `taskboard_projects_get` | Status de cada tipo de item, membros, sprints, tags, pontos e valores de problema |
| `taskboard_projects_stats` | Histórias abertas, concluídas, bloqueadas e atrasadas, pontos, tarefas, por status e por pessoa |
| `taskboard_projects_activity` | O que mudou, com valores antigos e novos, quem e quando |
| `taskboard_sprints_list` | Sprints com datas, histórias e pontos |
| `taskboard_userstories_list` | Histórias de usuário com filtros: status, sprint, pessoa, tag, texto, prazo, bloqueio |
| `taskboard_tasks_list` | Tarefas, com os mesmos filtros e por história |
| `taskboard_issues_list` | Problemas, com tipo, gravidade e prioridade |
| `taskboard_items_get` | Um item pelo número, com descrição; uma história traz suas tarefas. Opcional: comentários e histórico |
| `taskboard_search` | Busca por texto ou `#ref` em histórias, tarefas, problemas e wiki |
| `taskboard_wiki_get` | Lista as páginas da wiki ou devolve o conteúdo de uma |
| `taskboard_userstories_create` | Cria uma história de usuário |
| `taskboard_tasks_create` | Cria uma tarefa em uma história ou em uma sprint |
| `taskboard_issues_create` | Registra um problema |
| `taskboard_items_update` | Altera só os campos enviados de uma história, tarefa ou problema, inclusive concluir |
| `taskboard_userstories_move` | Muda uma história de coluna e de posição no kanban |
| `taskboard_userstories_set_sprint` | Envia várias histórias a uma sprint ou ao backlog |
| `taskboard_items_comment` | Comenta em uma história, tarefa ou problema |

As sete últimas só existem quando o token permite escrita. Não há tools de exclusão.

O token age com as permissões do papel do dono em cada projeto: um papel que só consulta não cria nem altera,
mesmo com token de escrita.
