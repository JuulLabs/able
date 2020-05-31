# Distribution

**Able** uses [`gradle-maven-publish-plugin`] for Maven publication, which provides
`installArchives` and `uploadArchives` Gradle tasks.

## Publishing to Maven Central

### Snapshot

Snapshots are [published to Sonatype] via GitHub Actions when a branch ending in `-SNAPSHOT` is
pushed. For example:

```
git branch 1.0.0-SNAPSHOT
git push origin 1.0.0-SNAPSHOT
```

### Release artifacts

Release artifacts are published to staging via GitHub Actions when a tag (without `-SNAPSHOT`
postfix) is pushed.

```
git tag 1.0.0
git push origin 1.0.0
```

The artifacts must then be [released from staging], see the [Easy Publishing to Central Repository]
video guide for detailed instructions.

## Install to local Maven

```
./gradlew installArchives -PVERSION_NAME=$version
```

_Replace `$version` with desired version name. Must end in `-SNAPSHOT` to skip signing process._

To use the dependency from your local maven, add the following to your `build.grade`:

```groovy
repositories {
    mavenLocal()
}
```


[`gradle-maven-publish-plugin`]: https://github.com/vanniktech/gradle-maven-publish-plugin
[published to Sonatype]: https://oss.sonatype.org/content/repositories/snapshots/com/juul/able/
[released from staging]: https://oss.sonatype.org/#stagingProfiles
[Easy Publishing to Central Repository]: https://youtu.be/dXR4pJ_zS-0?t=191
