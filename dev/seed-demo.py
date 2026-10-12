"""Fills a development Taskboard with two demo projects: one kanban, one scrum. Invented people and work.

Usage: python dev/seed-demo.py [address]     (default http://127.0.0.1:8080, needs admin / admin12345)
Creates ana, bruno, carla and diego, all with the password "password123". For development only.
"""
import json
import ssl
import sys
import urllib.error
import urllib.request
from datetime import date, timedelta
from http.cookiejar import CookieJar

API = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080") + "/api/v1"


class Session:
    def __init__(self, username, password):
        context = ssl._create_unverified_context() if API.startswith('https://localhost') else None
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()),
                                                  urllib.request.HTTPSHandler(context=context))
        self.call("POST", "/auth/login", {"username": username, "password": password})

    def call(self, method, path, body=None):
        request = urllib.request.Request(API + path, method=method, data=json.dumps(body).encode() if body is not None else None,
                                         headers={"Content-Type": "application/json", "X-Requested-With": "seed"})
        try:
            with self.opener.open(request) as response:
                text = response.read().decode()
                return json.loads(text) if text else None
        except urllib.error.HTTPError as error:
            raise SystemExit(f"{method} {path} -> {error.code} {error.read().decode()}")


admin = Session("admin", "admin12345")
PEOPLE = [("ana", "Ana Beatriz Rocha"), ("bruno", "Bruno Carvalho"), ("carla", "Carla Mendes"), ("diego", "Diego Farias")]
existing = {u["username"] for u in admin.call("GET", "/users?size=200")["items"]}
for username, name in PEOPLE:
    if username not in existing:
        admin.call("POST", "/users", {"username": username, "email": f"{username}@example.com", "fullName": name, "password": "password123"})

if any(p["slug"] == "portal-de-servicos" for p in admin.call("GET", "/projects")):
    raise SystemExit("demo projects already exist")

ana = Session("ana", "password123")
bruno = Session("bruno", "password123")


def named(entries, name):
    return next(e["id"] for e in entries if e["name"] == name)


def status(project, kind, name):
    return next(s["id"] for s in project["statuses"] if s["kind"] == kind and s["name"] == name)


def members(session, project, roles):
    for username, role, is_admin in roles:
        session.call("POST", f"/projects/{project['id']}/members", {"user": username, "roleId": named(project["roles"], role), "isAdmin": is_admin})
    return {m["username"]: m["userId"] for m in session.call("GET", f"/projects/{project['id']}/members")}


today = date.today()

portal = ana.call("POST", "/projects", {"name": "Portal de Serviços", "description": "Atendimento ao cidadão pela internet", "template": "kanban"})
ids = members(ana, portal, [("bruno", "Líder de Projeto", False), ("carla", "R2DA", False), ("diego", "Consulta", False), ("admin", "Product Owner", True)])
ana.call("PATCH", f"/projects/{portal['id']}", {"tagsColors": {"backend": "#137acf", "urgente": "#e44057", "front": "#8c52ff", "banco": "#d3ac50"}})
stories = [
    ("Possibilidade de cadastro e realização de dois ou mais cursos simultaneamente", "Subir em produção", ["ana"], ["backend"], 5, None),
    ("Correção de erro de registro duplicado para cartórios que reiniciaram o processo de migração", "Subir em produção", ["ana"], ["banco", "urgente"], 3, -2),
    ("Fechar o chamado ao reiniciar o fluxo de migração de uma serventia", "Subir em produção", ["ana", "bruno"], [], 2, 2),
    ("Tela de acompanhamento do pedido", "A fazer", [], ["front"], 8, None),
    ("Exportar relatório mensal em PDF", "A fazer", ["carla"], [], None, 10),
    ("Envio de e-mail de confirmação", "Em andamento", ["bruno"], ["backend"], 3, None),
    ("Ajuste no campo idade em registro antigo", "Em andamento", ["carla"], [], 1, None),
    ("Padronizar mensagens de erro do sistema", "Pronto para homologar", ["bruno"], ["front"], 2, None),
    ("Sincronizar cadastro com o sistema legado", "Em homologação", ["ana"], ["backend", "banco"], 13, None),
    ("Alterar texto do rodapé", "Concluído", ["carla"], [], 0.5, None),
    ("Migrar autenticação para o provedor central", "Concluído", ["ana"], ["backend"], 8, None),
    ("Layout antigo da página inicial", "Arquivado", ["bruno"], [], 3, None),
]
created = []
for subject, status_name, people, tags, points, due in stories:
    body = {"subject": subject, "statusId": status(portal, "us", status_name), "assignedUsers": [ids[p] for p in people], "tags": tags, "points": points}
    if due is not None:
        body["dueDate"] = str(today + timedelta(days=due))
    created.append(ana.call("POST", f"/projects/{portal['id']}/userstories", body))
ana.call("PATCH", f"/items/{created[5]['id']}", {"isBlocked": True, "blockedNote": "Aguardando liberação do servidor de e-mail", "version": created[5]["version"]})
ana.call("PATCH", f"/items/{created[2]['id']}", {"version": created[2]["version"], "description": "**Projeto:**\n\n- api-chamados\n- api-web\n\n**Descrição:**\n\nFechar o chamado ao reiniciar o fluxo de migração. Hoje o chamado antigo fica aberto e o cartório recomeça tudo com um chamado novo."})
task = ana.call("POST", f"/projects/{portal['id']}/tasks", {"subject": "Escrever o texto do comentário de fechamento", "parentId": created[2]["id"], "assignedTo": ids["bruno"]})
ana.call("POST", f"/projects/{portal['id']}/tasks", {"subject": "Testar com um chamado real", "parentId": created[2]["id"], "statusId": status(portal, "task", "Em andamento")})
bruno.call("POST", f"/items/{created[2]['id']}/comments", {"body": "Branch: `fix/reinicio-migracao`. Falta validar em homologação."})
bruno.call("POST", f"/items/{created[2]['id']}/watch")
ana.call("POST", f"/projects/{portal['id']}/issues", {"subject": "Botão de salvar fica desabilitado no Firefox", "severityId": named(portal["severities"], "Importante"), "assignedTo": ids["carla"], "tags": ["front"]})
ana.call("POST", f"/projects/{portal['id']}/issues", {"subject": "Como emitir a segunda via do protocolo?", "typeId": named(portal["issueTypes"], "Pergunta"), "priorityId": named(portal["priorities"], "Baixa")})
ana.call("PUT", f"/projects/{portal['id']}/wiki/home", {"content": "# Portal de Serviços\n\nComo publicar: veja [[publicacao]].\n\n- Ambiente de homologação\n- Ambiente de produção"})

app = ana.call("POST", "/projects", {"name": "Aplicativo Web", "description": "Quadro relacionado ao projeto do aplicativo web", "template": "scrum"})
ids = members(ana, app, [("bruno", "Líder de Projeto", False), ("carla", "R2DA", False), ("diego", "R2DA", False), ("admin", "Product Owner", True)])
ana.call("PATCH", f"/projects/{app['id']}", {"tagsColors": {"api": "#97c65c", "tela": "#8c52ff"}})
sprints = []
for number, start in ((1, -15), (2, -8), (3, -1)):
    begin, end = today + timedelta(days=start), today + timedelta(days=start + 7)
    sprints.append(ana.call("POST", f"/projects/{app['id']}/milestones", {
        "name": f"Sprint {number} - {begin:%d/%m} a {end:%d/%m}", "estimatedStart": str(begin), "estimatedFinish": str(end)}))


def story(subject, sprint=None, status_name="A fazer", points=None, people=(), tags=()):
    return ana.call("POST", f"/projects/{app['id']}/userstories", {
        "subject": subject, "milestoneId": sprint["id"] if sprint else None, "statusId": status(app, "us", status_name),
        "points": points, "assignedUsers": [ids[p] for p in people], "tags": list(tags)})


def subtask(parent, subject, status_name="A fazer", person=None, iocaine=False, tags=()):
    return ana.call("POST", f"/projects/{app['id']}/tasks", {
        "subject": subject, "parentId": parent["id"], "statusId": status(app, "task", status_name),
        "assignedTo": ids[person] if person else None, "isIocaine": iocaine, "tags": list(tags)})


for subject, points in (("Scroll lateral na aba de averbações nos registros de nascimento", None), ("Ajuste no campo idade em registro antigo", 2),
                        ("Fluxo de pagamentos", 13), ("Adicionar scroll lateral na aba de averbações", 1)):
    story(subject, points=points)
s1 = story("Possibilidade de espelhar as páginas pares dos modelos", sprints[2], "A fazer", 5, ["bruno"])
subtask(s1, "Possibilidade de espelhar as páginas pares dos modelos", "A fazer", "carla")
s2 = story("Alterações no cadastro de averbações e envio ao sistema externo", sprints[2], "Concluído", 3, ["carla"])
subtask(s2, "Enviar averbação ao sistema externo", "Concluído", "carla")
s3 = story("Correções no preenchimento automático para reaproveitar o máximo de informações", sprints[2], "A fazer", 8, ["diego"], ["tela"])
t3 = subtask(s3, "Correções no preenchimento automático dos tipos disponíveis", "Aguardando autorização produção", "diego", tags=["api", "tela"])
bruno.call("POST", f"/items/{t3['id']}/comments", {"body": "Validado em homologação."})
bruno.call("POST", f"/items/{t3['id']}/watch")
s4 = story("Formulário de pré-registro", sprints[2], "Em andamento", 5, ["bruno", "carla"])
subtask(s4, "Desenvolvimento do construtor de formulários personalizáveis", "Pronto para homologar", "bruno", True, ["api", "tela"])
subtask(s4, "Validação dos campos obrigatórios", "Em andamento", "carla")
ana.call("POST", f"/projects/{app['id']}/tasks", {"subject": "Atualizar a biblioteca de PDF", "milestoneId": sprints[2]["id"], "assignedTo": ids["diego"]})
for subject, sprint, status_name, points in (
        ("Erro no cálculo de idade da mãe no parto", sprints[1], "Concluído", 3), ("Exibir a seção de ex-cônjuge na transcrição de óbito", sprints[1], "Concluído", 5),
        ("Aumentar o tamanho do QR Code das mini certidões", sprints[1], "Subir em produção", 2), ("Fluxo de grupo de usuário", sprints[0], "Concluído", 8),
        ("Parametrização e rotina de envio de selo eletrônico automático", sprints[0], "Concluído", 13)):
    story(subject, sprint, status_name, points, ["bruno"])
ana.call("POST", f"/projects/{app['id']}/issues", {"subject": "Certidão sai com margem errada na impressora térmica", "milestoneId": sprints[2]["id"], "assignedTo": ids["bruno"]})
ana.call("POST", f"/projects/{app['id']}/like")
bruno.call("POST", f"/projects/{app['id']}/watch")
print("ok:", portal["slug"], app["slug"])
