# SSL Server: SHA-256 checksum service

A small Spring Boot REST service, served over HTTPS, that returns the SHA-256
checksum of a fixed data value. TLS is terminated in-process by embedded Tomcat
using a self-signed PKCS12 keystore. The `ssl-server_student/` directory holds the
Maven project; the two PDFs at the repo root are described under
[Coursework documents](#coursework-documents).

## Overview

HTTPS is handled in-process. Embedded Tomcat terminates TLS on port `8443` using a
self-signed PKCS12 keystore that you generate locally, and the keystore password
is read from the `SSL_KEYSTORE_PASSWORD` environment variable or an untracked
`application-local.properties`, not from `application.properties` (see
[Security Notes](#security-notes)). Plain HTTP is not served.

The service itself is one endpoint, `GET /hash`. It computes a SHA-256 digest with
`java.security.MessageDigest` and returns it as lowercase hex inside a short HTML
fragment; the [Endpoint](#endpoint) section has the exact response.

The build also runs OWASP Dependency-Check. The Maven plugin is bound to the
`verify` phase and writes `target/dependency-check-report.html`.

This service was the code artifact for SNHU CS-305 (Software Security); the two
PDFs at the repo root are the written deliverables.

## Repository layout

```
ssl-server_student/                 Maven project (the service)
CS 305 Project One Template.pdf     Vulnerability assessment report
CS 305 Project Two Template.pdf     Secure software practices report
```

## Coursework documents

Both PDFs are written deliverables for CS-305, addressed to the course's fictional
client (Artemis Financial, a financial consultancy) and employer (Global Rain).

**Project One vulnerability assessment report.** A manual security code review of
the course's sample application, broken down by class and by category (input
validation, cryptography, client/server, code quality, and so on), an OWASP
Dependency-Check scan of that app's dependencies, and a mitigation plan. The
application it reviews is not in this repo.

**Project Two practices for secure software report.** Covers this checksum
service: why SHA-256 for the file-verification feature, generating the self-signed
certificate with `keytool`, turning on HTTPS, and a re-run of Dependency-Check
against the finished build.

The filenames still begin with "CS 305" from when the repo was named `CS-305`.
The PDF contents are unchanged from submission.

## Tech stack

- Java 8
- Spring Boot 2.2.4. Uses `spring-boot-starter-web`; `spring-boot-starter-data-rest`
  comes from the course template and is unused (no repositories or entities).
- Embedded Tomcat with TLS, PKCS12 keystore
- Maven, through the bundled `mvnw` wrapper
- OWASP Dependency-Check Maven plugin 8.4.0
- JUnit 5 (`spring-boot-starter-test`), currently just the generated
  `contextLoads()` smoke test

## Endpoint

`GET https://localhost:8443/hash` returns:

```html
<p>Data: Jarrale Butts!<br>SHA-256 checksum: <64 hex chars></p>
```

HTTP is not served.

## Setup

Requirements: JDK 8 or newer, plus network access on the first Maven run. It pulls
dependencies, and Dependency-Check downloads the NVD vulnerability data (slow the
first time).

```bash
cd ssl-server_student
```

### 1. Generate a keystore

Nothing ships with one. Create a self-signed keystore for local use:

```bash
keytool -genkeypair -alias selfsigned -keyalg RSA -keysize 2048 -validity 365 \
  -storetype PKCS12 -keystore src/main/resources/keystore.p12
```

or with openssl:

```bash
openssl req -x509 -newkey rsa:2048 -sha256 -days 365 -nodes \
  -keyout k.pem -out c.pem -subj "/CN=localhost"
openssl pkcs12 -export -in c.pem -inkey k.pem -name selfsigned \
  -out src/main/resources/keystore.p12
```

`*.p12` is gitignored, so the file stays local. The `key-alias` in
`application.properties` is `selfsigned`; match it if you change the command.

### 2. Supply the keystore password

Export it:

```bash
export SSL_KEYSTORE_PASSWORD='<the password you set above>'
```

or put it in `src/main/resources/application-local.properties` (gitignored) and
run with the `local` profile:

```
SSL_KEYSTORE_PASSWORD=<the password>
```

### 3. Run

```bash
./mvnw spring-boot:run
# with the local profile instead:
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Call it (self-signed cert, so `-k`):

```bash
curl -k https://localhost:8443/hash
```

### Build a jar, run the scan

```bash
./mvnw clean verify        # writes target/dependency-check-report.html
./mvnw clean package
java -jar target/ssl-server-0.0.1-SNAPSHOT.jar
```

## Security Notes

### Finding: hardcoded SSL keystore password in git history

Earlier revisions shipped the server as `ssl-server_student.zip` at the repo root.
That archive held `src/main/resources/keystore.jks` and an `application.properties`
with the keystore password in plaintext:

```
server.ssl.key-store-password=<redacted>
server.ssl.key-store=classpath:keystore.jks
server.ssl.key-store-type=jks
```

The loose files were never `git add`ed on their own, but the zip was tracked and
pushed, so the password and the keystore came back out of public history for
anyone who pulled it.

### Fix

1. **Rotated the credential.** Generated a fresh self-signed keystore in PKCS12
   format (`keystore.p12`, RSA-2048, alias `selfsigned`) with a new 192-bit random
   password. The old JKS keystore was thrown away, so the leaked password opens
   nothing now.
2. **Externalized the password.** `application.properties` reads it from a
   placeholder:

   ```
   server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}
   server.ssl.key-store=classpath:keystore.p12
   server.ssl.key-store-type=PKCS12
   ```

   The value comes from the `SSL_KEYSTORE_PASSWORD` environment variable or from
   `application-local.properties` (Spring `local` profile). Both stay out of git.
3. **Ignored the sensitive file types.** `.gitignore` now excludes `*.jks`,
   `*.p12`, `*.pfx`, `*.pem`, `*.key`, `*.cer`, `*.crt`,
   `application-local.properties`, and `.env*`, plus Eclipse `.metadata/`.
4. **Purged the file from history.** Removed `ssl-server_student.zip` from every
   commit with `git filter-repo --invert-paths --path ssl-server_student.zip`,
   then force-pushed. Any commit left with no changes by the removal was dropped.

### Residual risk

The keystore is self-signed and only used for localhost, so the practical impact
of the original leak was low. A history rewrite and force-push doesn't reach
existing clones, forks, or a host's cached view of old commits. So the part that
actually kills the leaked value is the rotation in step 1, not the rewrite.
