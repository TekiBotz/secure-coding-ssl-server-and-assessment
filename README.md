# SSL Server — SHA-256 Checksum Service

A small Spring Boot REST service, served over HTTPS, that computes and returns the
SHA-256 checksum of a data value. It was built for SNHU CS-305 (Software Security)
as the subject of a manual vulnerability assessment and remediation exercise; the
`ssl-server_student/` directory holds the Maven project.

## Overview

The service exposes a single endpoint, `GET /hash`, on TLS port `8443`. It hashes a
fixed data string with `java.security.MessageDigest` (SHA-256) and returns the
digest as a lowercase hex string inside a small HTML fragment. TLS is terminated by
the embedded Tomcat container using a self-signed PKCS12 keystore on the classpath.

The repository also carries the assignment's static-analysis wiring: the OWASP
Dependency-Check Maven plugin runs on every build and writes a report to
`target/dependency-check-report.html`.

## Tech Stack

- Java 8
- Spring Boot 2.2.4 (`spring-boot-starter-web`, `spring-boot-starter-data-rest`)
- Embedded Tomcat with TLS (PKCS12 keystore)
- Maven (via the bundled `mvnw` wrapper)
- OWASP Dependency-Check Maven plugin 8.4.0 (SCA)
- JUnit 5 (`spring-boot-starter-test`) — currently only the generated
  `contextLoads()` smoke test

## Features

- `GET https://localhost:8443/hash` — returns the SHA-256 checksum of the data
  value as hex, e.g. `Data: Jarrale Butts!` / `SHA-256 checksum: <64 hex chars>`
- HTTPS-only; HTTP is not served
- Keystore password supplied at runtime, never committed (see Security Notes)
- Dependency vulnerability scan bound to the Maven `verify` phase

## Setup / Installation

Requirements: JDK 8+ and network access for the first Maven run.

```bash
cd ssl-server_student

# 1. Provide the keystore password. Either export it:
export SSL_KEYSTORE_PASSWORD='<the keystore password>'

#    ...or create src/main/resources/application-local.properties (gitignored):
#    SSL_KEYSTORE_PASSWORD=<the keystore password>
#    and run with the "local" profile:
#      ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 2. Run
./mvnw spring-boot:run

# 3. Call the endpoint (self-signed cert, so -k / trust the cert)
curl -k https://localhost:8443/hash
```

Build a jar and run the dependency scan:

```bash
./mvnw clean verify        # produces target/dependency-check-report.html
./mvnw clean package
java -jar target/ssl-server-0.0.1-SNAPSHOT.jar
```

### Regenerating the keystore

The committed keystore is a throwaway self-signed cert for local development. To
create a fresh one:

```bash
# with keytool (JDK):
keytool -genkeypair -alias selfsigned -keyalg RSA -keysize 2048 -validity 365 \
  -storetype PKCS12 -keystore ssl-server_student/src/main/resources/keystore.p12

# or with openssl:
openssl req -x509 -newkey rsa:2048 -sha256 -days 365 -nodes \
  -keyout k.pem -out c.pem -subj "/CN=localhost"
openssl pkcs12 -export -in c.pem -inkey k.pem -name selfsigned \
  -out ssl-server_student/src/main/resources/keystore.p12
```

Then set `SSL_KEYSTORE_PASSWORD` to the password you chose.

## Security Notes

### Finding: hardcoded SSL keystore password committed to git history

Earlier revisions of this repository shipped the server as `ssl-server_student.zip`
at the repo root. That archive contained `src/main/resources/keystore.jks` together
with `application.properties`, which held the keystore password in plaintext:

```
server.ssl.key-store-password=<redacted 6-digit value>
server.ssl.key-store=classpath:keystore.jks
server.ssl.key-store-type=jks
```

Because the zip was tracked and pushed, the password and the keystore file were
recoverable from the public history even though the loose files were never added
directly.

### Fix

1. **Rotated the credential.** Generated a new self-signed keystore in **PKCS12**
   format (`keystore.p12`, RSA-2048, alias `selfsigned`) with a new 192-bit random
   password. The old JKS keystore was discarded; the old password no longer opens
   anything.
2. **Externalized the password.** `application.properties` now resolves it from a
   placeholder:

   ```
   server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}
   server.ssl.key-store=classpath:keystore.p12
   server.ssl.key-store-type=PKCS12
   ```

   The actual value is provided by the `SSL_KEYSTORE_PASSWORD` environment variable
   or by `src/main/resources/application-local.properties` (Spring `local`
   profile), which is gitignored.
3. **Ignored the sensitive file types.** `.gitignore` now excludes `*.jks`,
   `*.p12`, `*.pfx`, `*.pem`, `*.key`, `*.cer`, `*.crt`, `application-local.properties`,
   and `.env*`, plus Eclipse `.metadata/` workspace state.
4. **Purged the exposed file from history.** `ssl-server_student.zip` was removed
   from every commit with `git filter-repo --invert-paths --path ssl-server_student.zip`.
   Rewriting the already-published history this way requires a force-push to
   `origin`; the commit whose only change was adding the zip became empty and was
   dropped, so the branch went from four commits to four (three rewritten plus this
   remediation commit).

### Residual risk

The keystore is self-signed and only used for localhost development, so the
practical impact of the original exposure was low. A history rewrite plus
force-push does not guarantee removal from forks, existing clones, or the
platform's cached views of old commits, so the credential rotation in step 1 —
not the history rewrite — is what actually neutralizes the leaked value.
