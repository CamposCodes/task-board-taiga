# Login pelo Keycloak e alinhamento do MCP

Registro de como o Taskboard foi alinhado ao login pelo Keycloak e ao MCP de referência
[illodev/taiga-mcp](https://github.com/illodev/taiga-mcp). Data: 2026-10-10.

## O problema

O MCP de referência autentica com `TAIGA_USERNAME` e `TAIGA_PASSWORD` em `POST /api/v1/auth`
(`type: normal`) e renova o token sozinho. Isso pressupõe que o usuário tem senha na aplicação.

Com login pelo Keycloak, a senha fica no Keycloak e a aplicação nunca a vê. Um MCP que pede usuário e
senha não tem como entrar em nome de quem só usa SSO.

## O que foi feito

### Na aplicação

- A API faz o fluxo OpenID Connect "authorization code" com PKCE contra o Keycloak. O navegador vai a
  `/api/v1/auth/oidc/start/sso`, autentica no Keycloak e volta em `/api/v1/auth/oidc/callback`.
- No retorno a API cria ou localiza a conta local e entrega o mesmo cookie de sessão do login por senha.
  Daí em diante permissões, papéis e tokens funcionam do mesmo jeito para os dois tipos de login.
- O login por senha continua existindo para contas locais, como o primeiro `admin`. Na tela ele fica atrás
  de "Entrar com usuário local" quando o Keycloak está configurado.

Regras da conta local no primeiro login pelo Keycloak:

| Situação | Resultado |
| --- | --- |
| Identidade já vinculada (`sub` conhecido) | Usa a mesma conta, atualiza nome e e-mail |
| Existe conta local com o mesmo nome de usuário, nunca usada com o Keycloak | A conta é adotada. Serve para o administrador cadastrar alguém e já colocá-lo em projetos |
| A conta com o mesmo nome é de administrador do sistema | Login recusado. Um usuário "admin" do Keycloak não herda o administrador local |
| Nome já vinculado a outra identidade | Cria conta com sufixo, por exemplo `maria-2` |
| Conta desativada no Taskboard | Login recusado |

Quem entra pelo Keycloak nasce sem privilégio de administrador do sistema. Um administrador concede isso
na tela de usuários.

### No MCP

- Mesma organização da referência: `client.ts`, um arquivo de tools por recurso, registro em `index.ts`,
  SDK oficial em TypeScript, `zod` para os parâmetros, transporte stdio.
- A autenticação usa **token pessoal** (`tb_...`), criado na tela de perfil depois do login. Não há senha
  em configuração nenhuma, e o token pode ser somente leitura e revogado a qualquer momento.
- Nomes das tools no padrão da referência: `taskboard_<recurso>_<ação>`.

Diferenças deliberadas em relação à referência:

| Referência | Taskboard MCP | Motivo |
| --- | --- | --- |
| Cerca de 170 tools, uma por endpoint | 19 tools | Menos tools, com descrições melhores, erram menos |
| Parâmetros por id numérico | Slug do projeto, número do item, nomes de status, sprint e pessoa | É como as pessoas e os modelos se referem às coisas |
| Devolve o JSON bruto da API | Resumo por item nas listas, detalhe completo sob pedido | Um quadro inteiro cabe no contexto do modelo |
| Tools de exclusão e de administração | Não tem | Reduz o dano de um engano do modelo |
| Só stdio | stdio e HTTP | HTTP permite um endereço compartilhado |
| Epics, issues, wiki, webhooks | Não tem | O Taskboard ainda não tem esses recursos |

## Validação feita

- Login completo contra um Keycloak 18.0.2 local: redirecionamento, criação da conta, retorno à página
  pedida, segundo login na mesma conta, login local do administrador preservado.
- Token criado por um usuário de SSO chamando a API sem nenhuma senha.
- MCP por stdio com 27 verificações, rodando em Node 18: leitura, escrita, filtros por nome, token
  somente leitura, token revogado.

## Decisões em aberto

Cada item traz a resposta assumida até aqui.

1. **O Keycloak da empresa é qual versão e qual realm?** Assumido: 17 ou mais novo, emissor
   `https://HOST/realms/REALM`. Para versões antigas o emissor leva `/auth` antes de `/realms`.
2. **Quem pode entrar?** Assumido: qualquer usuário do realm. Alternativa: exigir um papel ou grupo do
   Keycloak, o que pede um ajuste pequeno na API.
3. **Administradores do sistema vêm do Keycloak?** Assumido: não, um administrador local concede.
   Alternativa: mapear um papel do realm.
4. **O login por senha deve sumir da tela?** Assumido: não, fica recolhido. Alternativa: uma opção para
   escondê-lo, mantendo o `admin` como contingência.
5. **Sair do Taskboard deve encerrar a sessão do Keycloak?** Assumido: não. Enquanto a sessão do Keycloak
   estiver ativa, "Entrar com Keycloak" entra direto. Alternativa: logout único.
6. **O MCP remoto precisa de OAuth pelo Keycloak?** Assumido: não, token pessoal no cabeçalho basta.
   Clientes que só aceitam OAuth exigiriam publicar os metadados de recurso protegido no MCP.

## Sobre o Taiga atual da empresa

Se a ideia for usar o MCP de referência no Taiga 6 que já roda com Keycloak, o mesmo problema aparece: ele
precisa de usuário e senha locais. As saídas são criar uma conta de serviço local com senha no Taiga, ou
trocar o cliente dele para usar um "application token" do Taiga (`Authorization: Application <token>`).
