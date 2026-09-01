# Releasing to Maven Central

The libraries in this repo publish to **Maven Central** through the
[Central Portal](https://central.sonatype.com). Everything is wired in the
parent POM's `release` profile — this file is the operator's manual.

## What publishes, and what does not

The published set is **every library** — core, the extensions, the JavaFX
renderer and the visual editor:

| Module | Coordinates |
| --- | --- |
| Core | `ai.mindconnect:mc-semantic-ui-core` |
| JSON extension | `ai.mindconnect:mc-semantic-ui-ext-json` |
| Markdown extension | `ai.mindconnect:mc-semantic-ui-ext-markdown` |
| Diagram extension | `ai.mindconnect:mc-semantic-ui-ext-diagram` |
| Chart extension | `ai.mindconnect:mc-semantic-ui-ext-chart` |
| JavaFX renderer | `ai.mindconnect:mc-semantic-ui-javafx` (experimental) |
| Visual editor | `ai.mindconnect:mc-sui-editor` |

Only the apps, the demos and the aggregator root stay off Central.

This is controlled by one property, `sui.publish.skip`: it defaults to `true`
in the parent, and each library module overrides it to `false` in its own
`<properties>`. A module is therefore **not** published unless you opt it in —
so a new app or demo never ships to Central by accident.

## Two publish targets

**The Java artifacts → Maven Central.** `mvn deploy -Prelease` builds sources +
javadoc, GPG-signs everything, and uploads to the Central Portal, where the
bundle waits as a draft until somebody presses Publish.

**The client → npm**, as `@mindconnect-ai/mc-semantic-ui-core`. Cut from the
same commit and carrying the same version, because the JAR and the tarball ship
the identical `dist/` and SSR and SPA markup have to match.

The two differ in how final they are: Central holds a draft nobody sees, while
an npm publish is live the moment it returns and cannot be withdrawn after 72
hours. That is why the workflow publishes to npm last — if it fails, the
Central draft is still unpressed and nothing is public.

**Snapshots are not published anywhere.** They stay on the machine that built
them (`mvn install`); the parent sets `maven.deploy.skip=true`, so a plain
`mvn deploy` is a no-op rather than an error.

> Why no snapshot repository: these artifacts were once published to GitHub
> Packages from the old monorepo, and GitHub binds a Maven package to the
> repository that first pushed it — every push from this repo is rejected with
> `422 Unprocessable Entity`. Central's snapshot repository
> (`central.sonatype.com/repository/maven-snapshots/`) is the obvious
> replacement if remote snapshots are ever needed; nothing consumes them today.

---

## The changelog

[CHANGELOG.md](CHANGELOG.md) has an `## [Unreleased]` section at the top. Write
entries there as you go; nothing is moved by hand at release time.

The workflow, in order:

1. **Before the Central upload**, `[Unreleased]` is renamed to the version
   being cut, dated. Its body is kept aside as the release notes.
2. **With the next `-SNAPSHOT` bump**, a fresh empty `[Unreleased]` is opened,
   so `main` always has somewhere to write the next entry.
3. **The GitHub Release** uses that body, with the generated commit list
   appended under it.

An **empty `[Unreleased]` fails the release** — before anything is published,
since a version on Central can never be taken back, and an empty section almost
always means someone forgot rather than that nothing changed. For the case
where nothing really did, re-run with the `allowEmptyChangelog` input.

# The normal way: the release workflow

**You do not have to release from your machine.**
[`.github/workflows/release.yml`](.github/workflows/release.yml) does the whole
thing: it cuts the version, updates the version in the docs, publishes to
Maven Central, tags, opens the next `-SNAPSHOT`, and creates the GitHub
Release.

Run it from **Actions → release → Run workflow**. Both inputs are optional —
blank means "drop `-SNAPSHOT`" and "bump the patch level".

This needs no GPG and no `settings.xml` on your laptop. It needs these
secrets, once. They live on the **organisation**, not the repository —
**github.com/organizations/mindconnect-ai → Settings → Security → Secrets and
variables → Actions**:

| Secret | Value |
| --- | --- |
| `MAVEN_GPG_PRIVATE_KEY` | output of `gpg --armor --export-secret-keys <KEY_ID>` |
| `MAVEN_GPG_PASSPHRASE` | that key's passphrase |
| `CENTRAL_TOKEN_USERNAME` | Central Portal user token — username half |
| `CENTRAL_TOKEN_PASSWORD` | Central Portal user token — password half |
| `NPM_TOKEN` | only until trusted publishing is set up — see [npm](#4-npm) |

Set each one's **Repository access** to *Selected repositories* →
`mc-semantic-ui`. Not *Private repositories*: this repo is public, so that
option would leave the secret unreachable and the failure would only surface
mid-release, as an authentication error with no obvious cause.

You still create the GPG key once yourself (see below) — but after exporting it
into the secret, you never need it locally again.

Afterwards the bundle waits in the
[Central Portal](https://central.sonatype.com/publishing/deployments) as a
validated draft until you press **Publish**. That gate is deliberate: a Central
release can never be deleted.

---

# The manual way: releasing from your machine

Everything below is only needed if you want to release locally instead of
letting the workflow do it.

## One-time setup

### 1. A GPG key

**Needed for both routes** — also for the workflow, which just wants the key in
a secret. Central requires every artifact to carry a detached PGP signature.
The private key and its passphrase are yours; they never go into the repo or
into any file tracked by git.

```bash
# Generate a key. Pick "RSA and RSA", 4096 bits, no expiry (or your policy),
# your name and the project e-mail, and a passphrase you keep safe.
gpg --full-generate-key

# Find its id (the long hex string on the "sec" line):
gpg --list-secret-keys --keyid-format=long

# Publish the PUBLIC half so Central can verify signatures.
# keyserver.ubuntu.com is the one Sonatype actually checks — do that one first.
gpg --keyserver keyserver.ubuntu.com --send-keys <YOUR_KEY_ID>
gpg --keyserver keys.openpgp.org     --send-keys <YOUR_KEY_ID>

# Verify it really arrived:
gpg --keyserver keyserver.ubuntu.com --recv-keys <YOUR_KEY_ID>
```

> **Mind the spelling: `keys.openpgp.org`** (open**PGP**). The typo
> `keys.opengpg.org` is a live domain that redirects to an unrelated
> survey/scam site — do not upload anything there.

> `keys.openpgp.org` only publishes your e-mail after you confirm a
> verification mail. Central does not care — it only needs the key itself.

> `pgp.mit.edu` is historically flaky. If it hangs, skip it.

For the **workflow route**, export the private key into the
`MAVEN_GPG_PRIVATE_KEY` secret — paste the whole block including the
`-----BEGIN…` and `-----END…` lines:

```bash
gpg --armor --export-secret-keys <YOUR_KEY_ID>
```

> Keep an encrypted backup of the private key. If you lose it you cannot
> re-sign an update under the same key.

### 2. A Central Portal token

You said you already have this. In the [Central Portal](https://central.sonatype.com)
under **View Account → Generate User Token**, you get a *username/password*
pair (not your login). The `ai.mindconnect` namespace is already verified.

### 3. `~/.m2/settings.xml`

Add the Central server — the `id` must be `central`, matching the profile's
`publishingServerId`.

```xml
<settings>
  <servers>
    <!-- Maven Central (release). -->
    <server>
      <id>central</id>
      <username>YOUR_CENTRAL_TOKEN_USERNAME</username>
      <password>YOUR_CENTRAL_TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

For the GPG passphrase, the cleanest option is to let the gpg-agent prompt you,
or export it for the release shell only:

```bash
export MAVEN_GPG_PASSPHRASE='your-passphrase'
```

The `maven-gpg-plugin` reads `MAVEN_GPG_PASSPHRASE` from the environment — so
the passphrase never has to be written into `settings.xml` or the POM.

### 4. npm

Two things, and the second one retires the first.

**The organisation.** `@mindconnect-ai` has to exist on npmjs.com before
anything can be published into that scope — **Add Organization**, free for
public packages.

**A token, for the first publish only.** npm can authenticate this workflow
without any secret, through trusted publishing over OIDC — but a trusted
publisher is configured on a package's settings page, and that page does not
exist until the package does. So the first version goes up with a token, and
after that the token is not needed again.

Profile picture → **Access Tokens** → **Generate New Token**, a *granular*
one:

| Field | Value |
| --- | --- |
| Packages and scopes | Read and write, limited to the `@mindconnect-ai` scope |
| Organizations | Read and write on `mindconnect-ai` — the first publish creates a package in the scope, which needs it |
| Bypass two-factor authentication | tick it, or CI fails if your account requires 2FA to publish |
| Expiration | mandatory; a granular token always expires |

Copy it once — npm never shows it again — and put it in the `NPM_TOKEN`
repository secret.

**Then switch to OIDC and delete the token.** Once the package is on npm, open
its settings there and add a trusted publisher: this GitHub organisation, this
repository, and the workflow **filename** `release.yml` (not a path). Delete
the `NPM_TOKEN` secret afterwards.

Nothing in the workflow changes. It writes an `.npmrc` only when the secret is
there and otherwise lets npm authenticate over OIDC, and the `id-token: write`
permission it needs is already granted. That expiring token is the reason to
bother: left in place, it will one day break a release for no reason anybody
remembers.

---

## Cutting a release

1. **Set the release version** (drop `-SNAPSHOT`) across every module:

   ```bash
   mvn versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false
   ```

   `core/mc-semantic-ui-core/package.json` carries the same version by hand —
   it is the npm side of the same client, and the two must agree. Nothing
   publishes it to a registry yet, so a stale value breaks nothing today; set
   it anyway, so the first npm release does not inherit a lie.

   ```bash
   npm --prefix core/mc-semantic-ui-core version 0.1.0 --no-git-tag-version
   ```

2. **Build, sign and upload:**

   ```bash
   export MAVEN_GPG_PASSPHRASE='your-passphrase'   # or use the gpg-agent
   mvn clean deploy -Prelease
   ```

   This produces the main, `-sources` and `-javadoc` jars, a `.asc` signature
   for each, and uploads the bundle to the Central Portal.

3. **Publish in the Portal.** The profile sets `autoPublish=false`, so the
   bundle lands as a *validated draft*. Open
   [Central Portal → Deployments](https://central.sonatype.com/publishing/deployments),
   check the validation passed, and click **Publish**. It reaches Maven Central
   within a few minutes and appears in search within a few hours.

   > Once you trust the flow, set `<autoPublish>true</autoPublish>` in the
   > parent's `release` profile to skip the manual click.

4. **Tag and bump back to a snapshot:**

   ```bash
   git commit -am "release 0.1.0"
   git tag v0.1.0
   mvn versions:set -DnewVersion=0.2.0-SNAPSHOT -DgenerateBackupPoms=false
   npm --prefix core/mc-semantic-ui-core version 0.2.0-SNAPSHOT --no-git-tag-version
   git commit -am "back to snapshot 0.2.0-SNAPSHOT"
   git push && git push --tags
   ```

## A dry run before the real thing

To exercise the whole path except the upload, build to `verify` (which runs the
signing) without `deploy`:

```bash
mvn clean verify -Prelease
```

If your GPG key and passphrase are set up correctly this signs every artifact
and fails nothing. It does not contact the Portal.

## Notes

- **Releases are permanent.** A version published to Central can never be
  overwritten or deleted. Double-check the version and the artifacts before
  clicking Publish.
- The JavaFX renderer (`mc-semantic-ui-javafx`) publishes classifier-less
  JavaFX dependencies; consumers on another OS get the right native jars via
  the `javafx.platform` the OpenJFX POMs resolve from their JDK.
