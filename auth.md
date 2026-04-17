# Architecture d'authentification

## 1. Vue d'ensemble
Le projet utilise une architecture de type BFF (Backend For Frontend) avec session serveur.

- Frontend Angular: interface utilisateur, navigation, garde des routes.
- API Gateway (Spring WebFlux): point d'entree unique, OAuth2/OIDC client.
- Keycloak: fournisseur d'identite (IdP) pour login/logout OIDC.
- Redis: stockage des sessions Spring.
- Postgres: base de donnees de Keycloak.

L'authentification ne repose pas sur un token stocke dans le navigateur. Le navigateur conserve surtout le cookie de session, et la session est maintenue cote serveur.

## 2. Composants impliques

### Frontend Angular
- auth.service.ts
  - checkSession(): appelle GET /api/user pour savoir si une session existe.
  - login(returnUrl): redirige vers /oauth2/authorization/keycloak avec redirect_uri.
  - logout(): notifie les autres onglets puis redirige vers /logout.
  - BroadcastChannel: synchronisation inter-onglets pour le logout.

- auth.guard.ts
  - Protege les routes (dashboard, alerts, settings, profile).
  - Si non authentifie: redirection vers Keycloak (une seule fois, anti-boucle).

- auth.interceptor.ts
  - Ajoute withCredentials=true sur les requetes HTTP.
  - Gere 401/403: relance le login (sauf cas checkSession /api/user).

### API Gateway Spring
- SecurityConfig
  - Autorise certaines routes publiques (health, info, oauth2, login, etc.).
  - Exige authentification pour le reste.
  - Pour /api/** non authentifie: reponse HTTP 401.
  - Pour les routes web non API non authentifiees: redirection vers /oauth2/authorization/keycloak.

- OAuth2/OIDC
  - oauth2Login active avec Keycloak (authorization code flow).
  - Au succes, redirection vers l'URL frontend (ou redirect_uri validee).

- Sessions
  - Session Spring stockee dans Redis (EnableRedisWebSession).
  - Cookie SESSION utilise entre navigateur et gateway.

- Logout
  - GET /logout intercepte: invalide session Redis, supprime cookie SESSION.
  - Redirige vers le endpoint Keycloak de logout (avec id_token_hint si disponible).
  - Permet un logout silencieux sans ecran de confirmation Keycloak.

- RouteConfig (token relay)
  - Les routes proxy (ex: /api/trains/**) utilisent tokenRelay().
  - Le token est transmis aux microservices en aval.

### Keycloak
- Realm: fleet-management
- Client: actia-app
- Base Keycloak: keycloak_db (Postgres)

## 3. Flux d'authentification (login)
1. L'utilisateur ouvre le frontend.
2. Angular lance checkSession() -> GET /api/user.
3. Si session absente: authGuard appelle login() et redirige vers /oauth2/authorization/keycloak.
4. Le gateway demarre le flow OIDC avec Keycloak.
5. L'utilisateur s'authentifie sur Keycloak.
6. Keycloak renvoie vers /login/oauth2/code/keycloak du gateway.
7. Gateway cree/met a jour la session Redis puis redirige vers frontend.
8. Angular recharge l'etat utilisateur via /api/user.

## 4. Flux de deconnexion (logout)
1. Angular appelle logout() et diffuse LOGOUT aux autres onglets.
2. Chaque onglet redirige vers /logout (gateway).
3. Gateway invalide la session Redis + efface cookie SESSION.
4. Gateway redirige vers logout Keycloak avec post_logout_redirect_uri.
5. Utilisateur revient sur le frontend (et doit se reconnecter pour acceder aux routes protegees).

## 5. CORS et cookies
- CORS autorise explicitement l'origine frontend.
- allowCredentials=true est active.
- withCredentials=true cote Angular est obligatoire.
- Ce trio est necessaire pour transporter le cookie SESSION entre frontend et gateway.

## 6. Variables et dependances runtime
Dans Docker Compose:
- postgres -> keycloak -> api-gateway -> frontend
- redis est dependance du gateway pour la session.

Ports exposes:
- Frontend: 4200
- API Gateway: 8888
- Keycloak: 8080
- Postgres: 5431
- Redis: 6379

## 7. Point d'attention
La propriete frontend-url du gateway est lue via ANGULAR_APP_URL dans application.yaml.
Dans docker-compose, la variable transmise est FRONTEND_URL.

Effet actuel:
- Le systeme fonctionne souvent grace a la valeur par defaut http://localhost:4200.

Recommendation:
- Aligner les noms de variables d'environnement entre application.yaml et docker-compose pour eviter les ecarts selon les environnements.

## 8. Resume
Ton architecture d'authentification est solide et moderne:
- Login OIDC via Keycloak
- Session serveur via Redis
- Protection frontend par guard + interceptor
- Protection backend centralisee dans le gateway
- Relay de token vers les microservices
- Logout global cohérent multi-onglets




enabled = true
name = Keycloak-OAuth
allow_sign_up = true
auto_login = true
client_id = grafana
client_secret = NflDifb7DDDQingXOwYsOb5WQ3OdiLvU
scopes = openid email profile offline_access roles
email_attribute_path = email
login_attribute_path = username
name_attribute_path = full_name
auth_url = http://localhost:8080/realms/fleet-management/protocol/openid-connect/auth
token_url = http://localhost:8080/realms/fleet-management/protocol/openid-connect/token
api_url = http://localhost:8080/realms/fleet-management/protocol/openid-connect/userinfo
role_attribute_path = contains(realm_access.roles[*], 'grafana-admin') && 'Admin' || contains(realm_access.roles[*][*], 'grafana-Viewer') &&'Viewer'|| contains(realm_access.roles[*][*], 'grafana-Editor') && 'Editor'
skip_org_role_sync = false
