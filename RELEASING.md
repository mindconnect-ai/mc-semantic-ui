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

## Two publish targets, kept apart

- **Snapshots → GitHub Packages.** A plain `mvn deploy` (no profile) pushes
  `-SNAPSHOT` builds to GitHub Packages, unchanged from before.
- **Releases → Maven Central.** `mvn deploy -Prelease` builds sources +
  javadoc, GPG-signs everything, and uploads to the Central Portal. The
  `release` profile flips `maven.deploy.skip=true`, so the GitHub-Packages
  deploy does **not** also fire — the two never run in one command.

---

# The normal way: the release workflow

**You do not have to release from your machine.**
[`.github/workflows/release.yml`](.github/workflows/release.yml) does the whole
thing: it cuts the version, updates the version in the docs, publishes to
GitHub Packages *and* Maven Central, tags, opens the next `-SNAPSHOT`, and
creates the GitHub Release.

Run it from **Actions → release → Run workflow**. Both inputs are optional —
blank means "drop `-SNAPSHOT`" and "bump the patch level".

This needs no GPG and no `settings.xml` on your laptop. It needs four
repository secrets, once (**Settings → Secrets and variables → Actions**):

| Secret | Value |
| --- | --- |
| `MAVEN_GPG_PRIVATE_KEY` | output of `gpg --armor --export-secret-keys <KEY_ID>` |
| `MAVEN_GPG_PASSPHRASE` | that key's passphrase |
| `CENTRAL_TOKEN_USERNAME` | Central Portal user token — username half |
| `CENTRAL_TOKEN_PASSWORD` | Central Portal user token — password half |

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

Add the Central server (the `id` must be `central`, matching the profile's
`publishingServerId`) and, if you don't rely on a gpg-agent, the passphrase.
Keep the GitHub server you already have for snapshots.

```xml
<settings>
  <servers>
    <!-- Maven Central (release). -->
    <server>
      <id>central</id>
      <username>YOUR_CENTRAL_TOKEN_USERNAME</username>
      <password>YOUR_CENTRAL_TOKEN_PASSWORD</password>
    </server>

    <!-- GitHub Packages (snapshots) — unchanged. -->
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_PAT</password>
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

---

## Cutting a release

1. **Set the release version** (drop `-SNAPSHOT`) across every module:

   ```bash
   mvn versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false
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
