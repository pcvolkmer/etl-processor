# ETL-Processor for bwHC data [![Run Tests](https://github.com/pcvolkmer/etl-processor/actions/workflows/test.yml/badge.svg)](https://github.com/pcvolkmer/etl-processor/actions/workflows/test.yml)

Diese Anwendung versendet ein bwHC-MTB-File an das bwHC-Backend und pseudonymisiert die Patienten-ID.

## Einordnung innerhalb einer DNPM-ETL-Strecke

Diese Anwendung erlaubt das Entgegennehmen von HTTP/REST-Anfragen aus dem Onkostar-Plugin **[onkostar-plugin-dnpmexport](https://github.com/CCC-MF/onkostar-plugin-dnpmexport)**.

Der Inhalt einer Anfrage, wenn ein bwHC-MTBFile, wird pseudonymisiert und auf Duplikate geprüft.
Duplikate werden verworfen, Änderungen werden weitergeleitet.

Löschanfragen werden immer als Löschanfrage an das bwHC-backend weitergeleitet.

Zudem ist eine minimalistische Weboberfläche integriert, die einen Einblick in den aktuellen Zustand der Anwendung gewährt.

![Modell DNPM-ETL-Strecke](docs/etl.png)

### Duplikaterkennung

Die Erkennung von Duplikaten ist normalerweise immer aktiv, kann jedoch über den Konfigurationsparameter
`APP_DUPLICATION_DETECTION=false` deaktiviert werden.

### Datenübermittlung über HTTP/REST

Anfragen werden, wenn nicht als Duplikat behandelt, nach der Pseudonymisierung direkt an das bwHC-Backend gesendet.

### Datenübermittlung mit Apache Kafka

Anfragen werden, wenn nicht als Duplikat behandelt, nach der Pseudonymisierung an Apache Kafka übergeben.
Eine Antwort wird dabei ebenfalls mithilfe von Apache Kafka übermittelt und nach der Entgegennahme verarbeitet.

Siehe hierzu auch: https://github.com/CCC-MF/kafka-to-bwhc

## Konfiguration

### Breaking Changes nach Version 0.10

In Versionen des ETL-Processors **nach Version 0.10** werden die folgenden Konfigurationsoptionen entfernt:

* `APP_PSEUDONYMIZE_GPAS_SSLCALOCATION`: Nutzen Sie hier, wie unter [_Integration eines eigenen Root CA
  Zertifikats_](#integration-eines-eigenen-root-ca-zertifikats) beschrieben, das Einbinden eigener Zertifikate.
* `APP_KAFKA_TOPIC`: Nutzen Sie nun die Konfigurationsoption `APP_KAFKA_OUTPUT_TOPIC`
* `APP_KAFKA_RESPONSE_TOPIC`: Nutzen Sie nun die Konfigurationsoption `APP_KAFKA_OUTPUT_RESPONSE_TOPIC`

### Pseudonymisierung der Patienten-ID

Wenn eine URI zu einer gPAS-Instanz (Version >= 2023.1.0) angegeben ist, wird diese verwendet.
Ist diese nicht gesetzt. wird intern eine Anonymisierung der Patienten-ID vorgenommen.

* `APP_PSEUDONYMIZE_PREFIX`: Standortbezogenes Präfix - `UNKNOWN`, wenn nicht gesetzt
* `APP_PSEUDONYMIZE_GENERATOR`: `BUILDIN` oder `GPAS` - `BUILDIN`, wenn nicht gesetzt

**Hinweise**: 

* Der alte Konfigurationsparameter `APP_PSEUDONYMIZER` mit den Werten `GPAS` oder `BUILDIN` sollte nicht mehr verwendet
  werden.
* Die Pseudonymisierung erfolgt im ETL-Prozessor nur für die Patienten-ID.
  Andere IDs werden mithilfe des standortbezogenen Präfixes (erneut) anonymisiert, um für den aktuellen Kontext nicht
  vergleichbare IDs bereitzustellen.

#### Eingebaute Anonymisierung

Wurde keine oder die Verwendung der eingebauten Anonymisierung konfiguriert, so wird für die Patienten-ID der
entsprechende SHA-256-Hash gebildet und Base64-codiert - hier ohne endende "=" - zuzüglich des konfigurierten Präfixes
als Patienten-Pseudonym verwendet.

#### Pseudonymisierung mit gPAS

Wurde die Verwendung von gPAS konfiguriert, so sind weitere Angaben zu konfigurieren.

* `APP_PSEUDONYMIZE_GPAS_URI`: URI der gPAS-Instanz inklusive Endpoint (z.B. `http://localhost:8080/ttp-fhir/fhir/gpas/$$pseudonymizeAllowCreate`)
* `APP_PSEUDONYMIZE_GPAS_TARGET`: gPas Domänenname
* `APP_PSEUDONYMIZE_GPAS_USERNAME`: gPas Basic-Auth Benutzername
* `APP_PSEUDONYMIZE_GPAS_PASSWORD`: gPas Basic-Auth Passwort
* ~~`APP_PSEUDONYMIZE_GPAS_SSLCALOCATION`~~: **Veraltet** - Root Zertifikat für gPas, falls es dediziert hinzugefügt werden muss.
  **Wird in nach Version 0.10 entfernt**

Der Konfigurationsparameter `APP_PSEUDONYMIZE_GPAS_SSLCALOCATION` sollte nicht mehr verwendet werden und wird nach
Version 0.10 entfernt.
Stattdessen sollte das Root Zertifikat wie unter [_Integration eines eigenen Root CA
Zertifikats_](#integration-eines-eigenen-root-ca-zertifikats) beschrieben eingebunden werden.

### Anmeldung mit einem Passwort

Ein initialer Administrator-Account kann optional konfiguriert werden und sorgt dafür, dass bestimmte Bereiche nur nach
einem erfolgreichen Login erreichbar sind.

* `APP_SECURITY_ADMIN_USER`: Muss angegeben werden zur Aktivierung der Zugriffsbeschränkung.
* `APP_SECURITY_ADMIN_PASSWORD`: Das Passwort für den Administrator (Empfohlen).

Ein Administrator-Passwort muss inklusive des Encoding-Präfixes vorliegen.

Hier Beispiele für das Beispielpasswort `very-secret`:

* `{noop}very-secret` (Das Passwort liegt im Klartext vor - nicht empfohlen!)
* `{bcrypt}$2y$05$CCkfsMr/wbTleMyjVIK8g.Aa3RCvrvoLXVAsL.f6KeouS88vXD9b6`
* `{sha256}9a34717f0646b5e9cfcba70055de62edb026ff4f68671ba3db96aa29297d2df5f1a037d58c745657`

Wird kein Administrator-Passwort angegeben, wird ein zufälliger Wert generiert und beim Start der Anwendung in den Logs
angezeigt.

#### Weitere (nicht administrative) Nutzer mit OpenID Connect

Die folgenden Konfigurationsparameter werden benötigt, um die Authentifizierung weiterer Benutzer an einen OIDC-Provider
zu delegieren.
Ein Admin-Benutzer muss dabei konfiguriert sein.

* `APP_SECURITY_ENABLE_OIDC`: Aktiviert die Nutzung von OpenID Connect. Damit sind weitere Parameter erforderlich
* `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_CUSTOM_CLIENT_NAME`: Name. Wird beim zusätzlichen Loginbutton angezeigt.
* `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_CUSTOM_CLIENT_ID`: Client-ID
* `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_CUSTOM_CLIENT_SECRET`: Client-Secret
* `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_CUSTOM_CLIENT_SCOPE[0]`: Hier sollte immer `openid` angegeben werden.
* `SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_CUSTOM_ISSUER_URI`: Die URI des Providers,
  z.B. `https://auth.example.com/realm/example`
* `SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_CUSTOM_USER_NAME_ATTRIBUTE`: Name des Attributes, welches den Benutzernamen
  enthält.
  Oft verwendet: `preferred_username`

Ist die Nutzung von OpenID Connect konfiguriert, erscheint ein zusätzlicher Login-Button zur Nutzung mit OpenID Connect
und dem konfigurierten `CLIENT_NAME`.

![Login mit OpenID Connect](docs/login.png)

Weitere Informationen zur Konfiguration des OIDC-Providers
sind [hier](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html#oauth2-client)
zu finden.

#### Rollenbasierte Berechtigungen

Wird OpenID Connect verwendet, gibt es eine rollenbasierte Berechtigungszuweisung. 

Die Standardrolle für neue OIDC-Benutzer kann mit der Option `APP_SECURITY_DEFAULT_USER_ROLE` festgelegt werden.
Mögliche Werte sind `user` oder `guest`. Standardwert ist `user`.

Benutzer mit der Rolle "Gast" sehen nur die Inhalte, die auch nicht angemeldete Benutzer sehen.

Hierdurch ist es möglich, einzelne Benutzer einzuschränken oder durch Änderung der Standardrolle auf `guest` nur
einzelne Benutzer als vollwertige Nutzer zuzulassen.

![Rollenverwaltung](docs/userroles.png)

Benutzer werden nach dem Entfernen oder der Änderung der vergebenen Rolle automatisch abgemeldet und müssen sich neu anmelden.
Sie bekommen dabei wieder die Standardrolle zugewiesen.

#### Auswirkungen auf den dargestellten Inhalt

Nur Administratoren haben Zugriff auf den Konfigurationsbereich, nur angemeldete Benutzer können die anonymisierte oder
pseudonymisierte Patienten-ID sowie den Qualitätsbericht des bwHC-Backends einsehen.

Wurde kein Administrator-Account konfiguriert, sind diese Inhalte generell nicht verfügbar.

### Tokenbasierte Authentifizierung für MTBFile-Endpunkt

Die Anwendung unterstützt das Erstellen und Nutzen einer tokenbasierten Authentifizierung für den MTB-File-Endpunkt.

Dies kann mit der Umgebungsvariable `APP_SECURITY_ENABLE_TOKENS` aktiviert (`true` oder `false`) werden
und ist als Standardeinstellung nicht aktiv.

Ist diese Einstellung aktiviert worden, ist es Administratoren möglich, Zugriffstokens für Onkostar zu erstellen, die
zur Nutzung des MTB-File-Endpunkts eine HTTP-Basic-Authentifizierung voraussetzen.

![Tokenverwaltung](docs/tokens.png)

In diesem Fall können den Endpunkt für das Onkostar-Plugin **[onkostar-plugin-dnpmexport](https://github.com/CCC-MF/onkostar-plugin-dnpmexport)** wie folgt konfigurieren:

```
https://testonkostar:MTg1NTL...NGU4@etl.example.com/mtbfile
```

Ist die Verwendung von Tokens aktiv, werden Anfragen ohne die Angabe der Token-Information abgelehnt.

Alternativ kann eine Authentifizierung über Benutzername/Passwort oder OIDC erfolgen.

### Transformation von Werten

In Onkostar kann es vorkommen, dass ein Wert eines Merkmalskatalogs an einem Standort angepasst wurde und dadurch nicht dem Wert entspricht,
der vom bwHC-Backend akzeptiert wird.

Diese Anwendung bietet daher die Möglichkeit, eine Transformation vorzunehmen. Hierzu muss der "Pfad" innerhalb des JSON-MTB-Files angegeben werden und
welcher Wert wie ersetzt werden soll.

Hier ein Beispiel für die erste (Index 0 - weitere dann mit 1,2, ...) Transformationsregel:

* `APP_TRANSFORMATIONS_0_PATH`: Pfad zum Wert in der JSON-MTB-Datei. Beispiel: `diagnoses[*].icd10.version` für **alle** Diagnosen
* `APP_TRANSFORMATIONS_0_FROM`: Angabe des Werts, der ersetzt werden soll. Andere Werte bleiben dabei unverändert.
* `APP_TRANSFORMATIONS_0_TO`: Angabe des neuen Werts.

### Mögliche Endpunkte zur Datenübermittlung

Für REST-Requests als auch zur Nutzung von Kafka-Topics können Endpunkte konfiguriert werden.

Es ist dabei nur die Konfiguration eines Endpunkts zulässig.
Werden sowohl REST als auch Kafka-Endpunkt konfiguriert, wird nur der REST-Endpunkt verwendet.

#### REST

Folgende Umgebungsvariablen müssen gesetzt sein, damit ein bwHC-MTB-File an das bwHC-Backend gesendet wird:

* `APP_REST_URI`: URI der zu benutzenden API der bwHC-Backend-Instanz. z.B.: `http://localhost:9000/bwhc/etl/api`
* `APP_REST_USERNAME`: Basic-Auth-Benutzername für bwHC-Backend
* `APP_REST_PASSWORD`: Basic-Auth-Passwort für bwHC-Backend
* `APP_REST_IS_BWHC`: `true` für bwHC, weglassen oder `false` für dnpm:dip

#### Kafka-Topics

Folgende Umgebungsvariablen müssen gesetzt sein, damit ein bwHC-MTB-File an ein Kafka-Topic übermittelt wird:

* `APP_KAFKA_OUTPUT_TOPIC`: Zu verwendendes Topic zum Versenden von Anfragen. 
  Ersetzt ~~`APP_KAFKA_TOPIC`~~, **welches nach Version 0.10 entfernt wird**. 
* `APP_KAFKA_OUTPUT_RESPONSE_TOPIC`: Topic mit Antworten über den Erfolg des Versendens. Standardwert: `APP_KAFKA_TOPIC` mit Anhang "_response".
  Ersetzt ~~`APP_KAFKA_RESPONSE_TOPIC`~~, **welches nach Version 0.10 entfernt wird**.
* `APP_KAFKA_GROUP_ID`: Kafka GroupID des Consumers. Standardwert: `APP_KAFKA_TOPIC` mit Anhang "_group".
* `APP_KAFKA_SERVERS`: Zu verwendende Kafka-Bootstrap-Server als kommagetrennte Liste

Wird keine Rückantwort über Apache Kafka empfangen und es gibt keine weitere Möglichkeit den Status festzustellen, verbleibt der Status auf `UNKNOWN`.

Weitere Einstellungen können über die Parameter von Spring Kafka konfiguriert werden.

Lässt sich keine Verbindung zu dem bwHC-Backend aufbauen, wird eine Rückantwort mit Status-Code `900` erwartet, welchen es
für HTTP nicht gibt.

Wird die Umgebungsvariable `APP_KAFKA_INPUT_TOPIC` gesetzt, kann eine Nachricht auch über dieses Kafka-Topic an den ETL-Prozessor übermittelt werden.

##### Retention Time

Generell werden in Apache Kafka alle Records entsprechend der Konfiguration vorgehalten.
So wird ohne spezielle Konfiguration ein Record für 7 Tage in Apache Kafka gespeichert.
Es sind innerhalb dieses Zeitraums auch alte Informationen weiterhin enthalten, wenn der Consent später abgelehnt wurde.

Durch eine entsprechende Konfiguration des Topics kann dies verhindert werden.

Beispiel - auszuführen innerhalb des Kafka-Containers: Löschen alter Records nach einem Tag
```
kafka-configs.sh --bootstrap-server localhost:9092 --alter --topic test --add-config retention.ms=86400000
```

##### Key based Retention

Möchten Sie hingegen immer nur die letzte Meldung für einen Patienten und eine Erkrankung in Apache Kafka vorhalten,
so ist die nachfolgend genannte Konfiguration der Kafka-Topics hilfreich.


* `retention.ms`: Möglichst kurze Zeit in der alte Records noch erhalten bleiben, z.B. 10 Sekunden 10000
* `cleanup.policy`: Löschen alter Records und Beibehalten des letzten Records zu einem Key [delete,compact]

Beispiele für ein Topic `test`, hier bitte an die verwendeten Topics anpassen.

```
kafka-configs.sh --bootstrap-server localhost:9092 --alter --topic test --add-config retention.ms=10000
kafka-configs.sh --bootstrap-server localhost:9092 --alter --topic test --add-config cleanup.policy=[delete,compact]
```

Da als Key eines Records die (pseudonymisierte) Patienten-ID verwendet wird, stehen mit obiger Konfiguration 
der Kafka-Topics nach 10 Sekunden nur noch der jeweils letzte Eintrag für den entsprechenden Key zur Verfügung.

Da der Key sowohl für die Records in Richtung bwHC-Backend für die Rückantwort identisch aufgebaut ist, lassen sich so
auch im Falle eines Consent-Widerspruchs die enthaltenen Daten als auch die Offenlegung durch Verifikationsdaten in der
Antwort effektiv verhindern, da diese nach 10 Sekunden gelöscht werden.

Es steht dann nur noch die jeweils letzten Information zur Verfügung, dass für einen Patienten/eine Erkrankung
ein Consent-Widerspruch erfolgte.

Dieses Vorgehen empfiehlt sich, wenn Sie gespeicherte Records nachgelagert für andere Auswertungen verwenden möchten.

## Docker-Images

Diese Anwendung ist auch als Docker-Image verfügbar: https://github.com/pcvolkmer/etl-processor/pkgs/container/etl-processor

### Images lokal bauen 

```bash
./gradlew bootBuildImage
```

### Integration eines eigenen Root CA Zertifikats

Wird eine eigene Root CA verwendet, die nicht offiziell signiert ist, wird es zu Problemen beim SSL-Handshake kommen, wenn z.B. gPAS zur Generierung von Pseudonymen verwendet wird.

Hier bietet es sich an, das Root CA Zertifikat in das Image zu integrieren.

#### Integration beim Bauen des Images

Hier muss die Zeile `"BP_EMBED_CERTS" to "true"` in der Datei `build.gradle.kts` verwendet werden und darf nicht als Kommentar verwendet werden.

Die PEM-Datei mit dem/den Root CA Zertifikat(en) muss dabei im vorbereiteten Verzeichnis [`bindings/ca-certificates`](bindings/ca-certificates) enthalten sein.

#### Integration zur Laufzeit

Hier muss die Umgebungsvariable `SERVICE_BINDING_ROOT` z.B. auf den Wert `/bindings` gesetzt sein.
Zudem muss ein Verzeichnis `bindings/ca-certificates` - analog zum Verzeichnis [`bindings/ca-certificates`](bindings/ca-certificates) mit einer PEM-Datei als Docker-Volume eingebunden werden.

Beispiel für Docker-Compose:

```
...  
  environment:
    SERVICE_BINDING_ROOT: /bindings
    ...
  volumes:
    - "/path/to/bindings/ca-certificates/:/bindings/ca-certificates/:ro"
...
```

## Deployment
*Ausführen als Docker Container:*

```bash
cd ./deploy
cp env-sample.env .env
```
Wenn gewünscht, Änderungen in der `.env` vornehmen.

```bash
docker compose up -d
```

### Einfaches Beispiel für ein eigenes Docker-Compose-File

Die Datei [`docs/docker-compose.yml`](docs/docker-compose.yml) zeigt eine einfache Konfiguration für REST-Requests basierend 
auf Docker-Compose mit der gestartet werden kann.

### Betrieb hinter einem Reverse-Proxy

Die Anwendung verarbeitet `X-Forwarded`-HTTP-Header und kann daher auch hinter einem Reverse-Proxy betrieben werden.

Dabei werden, je nachdem welche Header durch den Reverse-Proxy gesendet werden auch Protokoll, Host oder auch Path-Präfix
automatisch erkannt und verwendet werden. Dadurch ist z.B. eine abweichende Angabe des Pfads problemlos möglich.

#### Beispiel *Traefik* (mit Docker-Labels):

Das folgende Beispiel zeigt die Konfiguration in einer Docker-Compose-Datei mit Service-Labels.

```
...
  deploy:
    labels:
      - "traefik.http.routers.etl.rule=PathPrefix(`/etl-processor`)"
      - "traefik.http.routers.etl.middlewares=etl-path-strip"
      - "traefik.http.middlewares.etl-path-strip.stripprefix.prefixes=/etl-processor"
...
```

#### Beispiel *nginx*

Das folgende Beispiel zeigt die Konfiguration einer _location_ in einer nginx-Konfigurationsdatei.

```
...
  location /etl-processor {
    set              $upstream http://<beispiel:8080>/;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Scheme $scheme;
    proxy_set_header X-Forwarded-Proto  $scheme;
    proxy_set_header X-Forwarded-For    $remote_addr;
    proxy_set_header X-Real-IP          $remote_addr;
    proxy_pass       $upstream;
  }
...
```

## Entwicklungssetup

Zum Starten einer lokalen Entwicklungs- und Testumgebung kann die beiliegende Datei `dev-compose.yml` verwendet werden.
Diese kann zur Nutzung der Datenbanken **MariaDB** als auch **PostgreSQL** angepasst werden.

Zur Nutzung von Apache Kafka muss dazu ein Eintrag im hosts-File vorgenommen werden und der Hostname `kafka` auf die lokale
IP-Adresse verweisen. Ohne diese Einstellung ist eine Nutzung von Apache Kafka außerhalb der Docker-Umgebung nicht möglich.

Beim Start der Anwendung mit dem Profil `dev` wird die in `dev-compose.yml` definierte Umgebung beim Start der
Anwendung mit gestartet:

```
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

Die Datei `application-dev.yml` enthält hierzu die Konfiguration für das Profil `dev`.

Beim Ausführen der Integrationstests wird eine Testdatenbank in einem Docker-Container gestartet.
Siehe hier auch die Klasse `AbstractTestcontainerTest` unter `src/integrationTest`.
