# Redis on-wire golden fixtures — NOT YET SEALED

`phase3_8-redis-golden.tsv` is **intentionally absent** from this delivery.
This runtime has no JDK 25 + Maven dependency cache, and manually authored JSON
cannot honestly be labeled Redisson 3.50.0 on-wire bytes.

On an environment using **JDK 25**, execute the frozen POM versions first:

```bash
./mvnw -B -ntp -pl redisson-client -Dtest=RedisGoldenFixtureCaptureTest \
    -Dphase39.captureGolden=true test
cp redisson-client/target/phase39-capture/phase3_8-redis-golden.tsv \
    redisson-client/src/test/resources/compatibility/redis/phase3_8-redis-golden.tsv
./mvnw -B -ntp -pl redisson-client -Dphase39.golden.required=true test
```

**Review and commit** the fixture and its generating SHA-256 metadata while the
pom.xml still pins Redisson 3.50.0. NEVER regenerate after a dependency upgrade.
The fixture test is explicitly blocked by `-Dphase39.golden.required=true` when
this file is missing; default unit-test runs report it as skipped, not passed.
The capture validates its real Redisson jar location and JDK runtime.

Future 3.52 / 4.x branches must run the golden-required test **without recapture**.
For full live Redis key TTL / consumer-group / RStream compatibility, also run
real Redis IT tests; the byte fixture suite does not substitute for those tests.
