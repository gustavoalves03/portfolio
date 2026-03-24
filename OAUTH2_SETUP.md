# OAuth2 Google Sign-In - Guide de Configuration

## 🎉 Implémentation Terminée !

L'authentification OAuth2 avec Google Sign-In a été entièrement intégrée dans votre application. Le système fonctionne comme suit :

1. L'utilisateur navigue librement sur le site
2. Lorsqu'il clique sur "Confirmer la réservation", une modale d'authentification s'ouvre
3. Il se connecte avec Google (Facebook et Apple bientôt disponibles)
4. Après authentification, la réservation est créée avec son vrai userId
5. Le JWT est stocké et automatiquement ajouté aux requêtes API

---

## ⚙️ Configuration Requise

### 1. Configuration Google Cloud Console

Vous devez configurer vos credentials OAuth2 dans Google Cloud Console :

1. Accédez à [Google Cloud Console](https://console.cloud.google.com)
2. Créez un nouveau projet ou sélectionnez un projet existant
3. Activez l'API Google+ (Legacy) ou Google Identity Services
4. Créez des identifiants OAuth 2.0 :
   - **Type** : Application Web
   - **Nom** : Pretty Face
   - **Origines JavaScript autorisées** :
     - `http://localhost:4200`
     - `http://localhost:4300`
     - `http://localhost:8080`
   - **URI de redirection autorisés** :
     - `http://localhost:8080/login/oauth2/code/google`

5. Copiez votre **Client ID** et **Client Secret**

### 2. Variables d'Environnement Backend

Configurez les variables d'environnement suivantes :

**Option 1 : Dans votre système (recommandé pour dev local)**
```bash
export GOOGLE_CLIENT_ID="your-google-client-id.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="your-google-client-secret"
export JWT_SECRET="votre-secret-jwt-super-securise-minimum-256-bits"
export OAUTH2_REDIRECT_URI="http://localhost:4300/oauth2/redirect"
```

**Option 2 : Modifier directement `application.properties`** (non recommandé pour production)
```properties
spring.security.oauth2.client.registration.google.client-id=your-google-client-id
spring.security.oauth2.client.registration.google.client-secret=your-google-client-secret
app.auth.token.secret=votre-secret-jwt-super-securise-minimum-256-bits
app.oauth2.authorized-redirect-uri=http://localhost:4300/oauth2/redirect
```

### 3. Base de Données

La nouvelle structure de la table `USERS` sera créée automatiquement par Hibernate au démarrage (ddl-auto=update).

**Nouvelles colonnes ajoutées :**
- `provider` (VARCHAR) : GOOGLE, FACEBOOK, APPLE, LOCAL
- `provider_id` (VARCHAR) : ID unique du provider OAuth2
- `image_url` (VARCHAR) : URL de la photo de profil
- `email_verified` (BOOLEAN) : Email vérifié par le provider
- `password` (VARCHAR, nullable) : Mot de passe (null pour OAuth2)
- `created_at` (TIMESTAMP) : Date de création
- `updated_at` (TIMESTAMP) : Date de mise à jour

---

## 🚀 Démarrage

### 1. Backend (Spring Boot)

```bash
cd backend

# Avec variables d'environnement
export GOOGLE_CLIENT_ID="your-client-id"
export GOOGLE_CLIENT_SECRET="your-client-secret"
mvn clean spring-boot:run

# Ou directement
mvn clean spring-boot:run
```

Le backend démarrera sur `http://localhost:8080`

### 2. Frontend (Angular)

```bash
cd frontend
npm start
```

Le frontend démarrera sur `http://localhost:4200`

**Ou avec Docker (port 4300) :**
```bash
docker compose --profile dev up frontend-dev
```

---

## 🧪 Test du Flux OAuth2

### Scénario de Test Complet

1. **Accédez à la page d'accueil** : `http://localhost:4200` ou `http://localhost:4300`

2. **Sélectionnez un soin** (exemple : Soin du visage)

3. **Choisissez une date et une heure**

4. **Cliquez sur "Confirmer la réservation"**
   - ✅ La modale d'authentification s'ouvre
   - ✅ Cliquez sur "Continuer avec Google"
   - ✅ Vous êtes redirigé vers la page de connexion Google
   - ✅ Connectez-vous avec votre compte Google
   - ✅ Google vous redirige vers `http://localhost:8080/login/oauth2/code/google`
   - ✅ Le backend crée/récupère votre utilisateur et génère un JWT
   - ✅ Vous êtes redirigé vers `http://localhost:4300/oauth2/redirect?token=xxx`
   - ✅ Le frontend stocke le JWT et charge vos infos utilisateur
   - ✅ Vous êtes redirigé vers la page d'accueil
   - ✅ La réservation est créée automatiquement avec votre userId

5. **Vérifiez dans la console** :
   - Backend : Logs de création d'utilisateur OAuth2
   - Frontend : User chargé dans AuthService (signal)

---

## 📁 Fichiers Créés/Modifiés

### Backend (Spring Boot)

**Nouveaux fichiers :**
- `backend/src/main/java/com/prettyface/app/auth/`
  - `OAuth2UserInfo.java` - Interface abstraite pour les infos OAuth2
  - `GoogleOAuth2UserInfo.java` - Implémentation Google
  - `OAuth2UserInfoFactory.java` - Factory pour créer les UserInfo
  - `CustomOAuth2User.java` - OAuth2User personnalisé
  - `CustomOAuth2UserService.java` - Service OAuth2 (crée/met à jour les users)
  - `TokenService.java` - Génération et validation JWT
  - `JwtAuthenticationFilter.java` - Filtre pour authentifier via JWT
  - `UserPrincipal.java` - Principal pour Spring Security
  - `OAuth2AuthenticationSuccessHandler.java` - Handler de succès OAuth2
  - `OAuth2AuthenticationFailureHandler.java` - Handler d'échec OAuth2
  - `AuthController.java` - Endpoint REST `/api/auth/me`
  - `dto/AuthResponse.java` - DTO de réponse d'authentification
  - `dto/UserDto.java` - DTO utilisateur

- `backend/src/main/java/com/prettyface/app/users/domain/`
  - `AuthProvider.java` - Enum des providers (GOOGLE, FACEBOOK, APPLE, LOCAL)

**Fichiers modifiés :**
- `pom.xml` - Ajout des dépendances OAuth2 et JWT
- `application.properties` - Configuration OAuth2 et JWT
- `User.java` - Enrichi avec champs OAuth2
- `UserRepository.java` - Méthodes de recherche OAuth2
- `SecurityConfig.java` - Configuration OAuth2 + JWT

### Frontend (Angular)

**Nouveaux fichiers :**
- `frontend/src/app/core/auth/`
  - `auth.model.ts` - Interfaces User, AuthResponse, AuthProvider
  - `auth.service.ts` - Service d'authentification avec signals
  - `auth.interceptor.ts` - Intercepteur HTTP pour ajouter JWT

- `frontend/src/app/shared/modals/auth-modal/`
  - `auth-modal.component.ts` - Modale de connexion
  - `auth-modal.component.html` - Template de la modale
  - `auth-modal.component.scss` - Styles de la modale

- `frontend/src/app/pages/oauth2-redirect/`
  - `oauth2-redirect.component.ts` - Page de callback OAuth2

**Fichiers modifiés :**
- `app.config.ts` - Ajout de l'intercepteur authInterceptor
- `app.routes.ts` - Route `/oauth2/redirect`
- `home.ts` - Intégration de l'authentification dans le flux de réservation
- `fr.json` - Traductions françaises
- `en.json` - Traductions anglaises

---

## 🔐 Sécurité

### JWT
- **Algorithme** : HS256 (HMAC-SHA256)
- **Expiration** : 24 heures (86400000 ms)
- **Secret** : Minimum 256 bits (configuré via `JWT_SECRET`)
- **Stockage** : localStorage (browser-only, SSR-safe)

### OAuth2
- **Flow** : Authorization Code (le plus sécurisé)
- **Scope** : openid, profile, email
- **CSRF Protection** : Désactivée pour `/oauth2/**` et `/api/auth/**`
- **State Parameter** : Géré automatiquement par Spring Security

### API
- **Endpoints publics** :
  - `GET /api/cares/**` - Liste des soins
  - `GET /api/categories/**` - Liste des catégories
  - `GET /api/images/**` - Images publiques
  - `GET /api/csrf` - Token CSRF
  - `/oauth2/**` - Endpoints OAuth2
  - `/api/auth/**` - Endpoints d'authentification

- **Endpoints protégés** :
  - `POST /api/bookings` - Création de réservation (JWT requis)
  - Tous les autres `/api/**`

---

## 🎨 UX/UI

### Modale d'Authentification
- Design cohérent avec Material 3 (thème rose/nacré)
- Transitions douces (200ms)
- Responsive (mobile-first)
- Boutons :
  - ✅ **Google** : Actif avec icône officielle
  - ⏳ **Facebook** : Désactivé (coming soon)
  - ⏳ **Apple** : Désactivé (coming soon)

### Flux Utilisateur
1. Navigation libre sans compte
2. Sélection de date/heure
3. Clic "Confirmer" → Modale OAuth2
4. Connexion Google → Redirect automatique
5. Retour à l'accueil → Réservation créée

---

## 🔄 Prochaines Étapes

### Pour Ajouter Facebook
1. Créez une app Facebook : [Facebook Developers](https://developers.facebook.com)
2. Activez Facebook Login
3. Ajoutez dans `application.properties` :
   ```properties
   spring.security.oauth2.client.registration.facebook.client-id=${FACEBOOK_CLIENT_ID}
   spring.security.oauth2.client.registration.facebook.client-secret=${FACEBOOK_CLIENT_SECRET}
   spring.security.oauth2.client.registration.facebook.scope=email,public_profile
   ```
4. Créez `FacebookOAuth2UserInfo.java` :
   ```java
   public class FacebookOAuth2UserInfo extends OAuth2UserInfo {
       @Override
       public String getId() { return (String) attributes.get("id"); }
       @Override
       public String getName() { return (String) attributes.get("name"); }
       @Override
       public String getEmail() { return (String) attributes.get("email"); }
       @Override
       public String getImageUrl() {
           Map<String, Object> picture = (Map<String, Object>) attributes.get("picture");
           if (picture != null) {
               Map<String, Object> data = (Map<String, Object>) picture.get("data");
               if (data != null) return (String) data.get("url");
           }
           return null;
       }
   }
   ```
5. Mettez à jour `OAuth2UserInfoFactory.java`
6. Activez le bouton Facebook dans `auth-modal.component.html`

### Pour Ajouter Apple Sign In
1. Créez un Service ID : [Apple Developer](https://developer.apple.com)
2. Configurez Sign in with Apple
3. Apple utilise un flux légèrement différent (JWT + private key)
4. Suivez la documentation Spring Security OAuth2 pour Apple

---

## 🐛 Dépannage

### Erreur : "Redirect URI mismatch"
➡️ Vérifiez que l'URI dans Google Cloud Console correspond exactement :
   `http://localhost:8080/login/oauth2/code/google`

### Erreur : "Invalid JWT signature"
➡️ Vérifiez que `JWT_SECRET` a au moins 256 bits (32 caractères)

### Erreur : "Email not found from OAuth2 provider"
➡️ Vérifiez que le scope `email` est bien configuré dans Google Console

### La modale ne s'ouvre pas
➡️ Vérifiez que `MatDialog` est bien injecté dans `home.ts`

### Token non ajouté aux requêtes
➡️ Vérifiez que `authInterceptor` est bien enregistré dans `app.config.ts`

---

## 📞 Support

Pour toute question ou problème :
1. Vérifiez les logs du backend (Spring Boot)
2. Vérifiez la console du navigateur (erreurs JS)
3. Vérifiez la console réseau (requêtes HTTP)
4. Consultez la documentation Spring Security OAuth2

---

**Bonne chance avec votre application Pretty Face ! 🌸🐚**
