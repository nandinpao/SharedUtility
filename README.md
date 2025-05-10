# PgCluster Spring Boot Starter

A highly configurable Spring Boot starter module for supporting PostgreSQL master-master-slave-slave (M/M/S/S) architecture, enabling dynamic and annotation-based routing for read and write operations using MyBatis and HikariCP.

---

## 📌 Purpose

This starter provides enterprise-level support for PostgreSQL cluster-based applications with:
- Multi-master write capability with load balancing
- Multi-slave read capability with load balancing
- Annotation-based routing with `@ReadOnly`, `@WriteOnly`, `@Master`, and `@Slave`
- YAML-based dynamic configuration
- Modular design for reuse in multiple Spring Boot services

---

## 🚀 How to Use

### 1. **Add Dependency**
Make sure to publish this module to your internal Maven repository, then include:

```xml
<dependency>
  <groupId>com.agitg</groupId>
	<artifactId>database</artifactId>
	<version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. **Configure `application.yml`**
```yaml
pg:
  write:
    - name: master1
      url: jdbc:postgresql://master1.db:5432/app
      username: writer
      password: secret
      isDefault: true
      hikari:
        minimum-idle: 3
        maximum-pool-size: 10
        idle-timeout: 30000
        pool-name: Master1Pool
    - name: master2
      url: jdbc:postgresql://master2.db:5432/app
      username: writer
      password: secret
      hikari:
        minimum-idle: 3
        maximum-pool-size: 10
        idle-timeout: 30000
        pool-name: Master1Pool
  read:
    - name: slave1
      url: jdbc:postgresql://slave1.db:5432/app
      username: reader
      password: secret
      hikari:
        minimum-idle: 3
        maximum-pool-size: 10
        idle-timeout: 30000
        pool-name: Master1Pool
    - name: slave2
      url: jdbc:postgresql://slave2.db:5432/app
      username: reader
      password: secret
      hikari:
        minimum-idle: 3
        maximum-pool-size: 10
        idle-timeout: 30000
        pool-name: Master1Pool
  driver-class-name: org.postgresql.Driver
  mapper:
    mapper-locations: 
      - classpath:/mappers/**/*.xml
    type-aliases-package: 
      - com.example.pgstarter.entity
    base-packages:
      - com.example.pgstarter.mapper
```

### 3. **Enable Mapper Scanning**
```java
@SpringBootApplication
@EnablePgMapperScan
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 4. **Use Annotations in Service Layer**
```java
@Service
public class UserService {

    @ReadOnly
    public List<User> readFromRandomSlave() {
        return userMapper.findAll();
    }

    @Slave("slave2")
    public List<User> readFromSlave2() {
        return userMapper.findAll();
    }

    @WriteOnly
    public void writeToRandomMaster(User user) {
        userMapper.insert(user);
    }

    @Master("master1")
    public void writeToMaster1(User user) {
        userMapper.insert(user);
    }

    public void writeInfo(User user) {  // use a default connection
        userMapper.insert(user);
    }
}
```

### 5. **Add a new file below of file structure in a project of spring boot**

File in the rescources, should copy from the repository to your spring boot project under the structure necessarily.

```
src/main/resources/
└── META-INF/
    └── spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## ⚠️ Limitations

- Does not include automatic failover/health-check logic (can be extended)
- Assumes all nodes are reachable and connection pool is healthy
- Currently does not support sharding or distributed transactions

---

## ⚙️ Tools & Requirements

| Component | Version |
|----------|---------|
| Java     | 21+     |
| Spring Boot | 3.2+ |
| MyBatis  | 3.0.4 (via `mybatis-spring-boot-starter`) |
| PostgreSQL | 14+ (recommended) |
| HikariCP | default in Spring Boot |
| Maven    | 3.8+   |

---

## ✅ Features Summary
- ✅ Master-master write load balancing
- ✅ Slave read load balancing
- ✅ Annotation-driven dynamic routing
- ✅ Plug & play via Maven starter
- ✅ MyBatis + HikariCP integration
- ✅ Fully YAML configurable
- ✅ Modular and testable architecture



---

# Redis

---

### ✅ `README.md` (for `redisson-spring-boot-starter`)

```markdown
# Redisson Spring Boot Starter

A plug-and-play Spring Boot starter for Redisson – supporting YAML-based configuration, auto-reloading, and type-safe Redis access through `RedissonAccess`.

## 💡 Features

- ✅ Supports both **Single** and **Cluster** Redis modes
- ✅ YAML-based configuration (`redisson.yaml`)
- ✅ Auto-registers `RedissonClient` and `RedissonAccess` beans
- ✅ Type-safe access wrappers for `List`, `Set`, `Map`, `Queue`, `Bucket`, etc.
- ✅ Works in **Spring Boot** or **Pure Java** environments
- ✅ Conditional auto-configuration only if `redis.mode` is provided

---

## 📦 Installation

### Maven
```xml
<dependency>
  <groupId>com.agitg</groupId>
  <artifactId>redisson-client</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## ⚙️ Configuration

### Step 1. Add `redisson.yaml` to your classpath:

```yaml
redis:
  database: 0
  password:
  timeout: 30000
  mode: cluster
  pool:
    maxIdle: 16
    minIdle: 8
    maxActive: 8
    maxWait: 3000
    connTimeout: 3000
    soTimeout: 3000
    size: 10
  single:
    address: 127.0.0.1:7001
  cluster:
    scanInterval: 1000
    nodes:
      - 127.0.0.1:7001
      - 127.0.0.1:7002
      - 127.0.0.1:7003
      - 127.0.0.1:7004
      - 127.0.0.1:7005
      - 127.0.0.1:7006
    readMode: SLAVE
    retryAttempts: 3
    failedAttempts: 3
    slaveConnection-pool-size: 64
    masterConnection-pool-size: 64
    retryInterval: 1500
```

### Step 2. Import it via `application.yml`

```yaml
spring:
  config:
    import: classpath:redisson.yaml
```

> 💡 If `redis.mode` is missing, the auto-configuration will be skipped.

---

## ✨ Usage

### Autowire the `RedissonAccess` Bean

```java
import com.example.redisson.RedissonAccess;
import org.springframework.stereotype.Service;

@Service
public class RedisService {

    private final RedissonAccess redis;

    public RedisService(RedissonAccess redis) {
        this.redis = redis;
    }

    public void storeUser(User user) {
        redis.setBucketValue("user:" + user.getId(), user);
    }

    public User getUser(String userId) {
        return redis.getBucketValue("user:" + userId, User.class);
    }

    public List<User> getUsers() {
        return redis.getAllFromList("user-list", User.class);
    }
}
```

---

## 🧪 Type-safe Access Examples

### List
```java
List<User> users = redis.getAllFromList("user-list", User.class);
redis.pushToList("user-list", new User(...));
```

### Map
```java
Map<String, User> map = redis.getAllFromMap("user-map", String.class, User.class);
User u = redis.getFromMap("user-map", "uid123", User.class);
```

### Queue
```java
redis.enqueue("job-queue", new Task(...));
Task job = redis.dequeue("job-queue", Task.class);
```

---

## 🛑 Auto-Configuration Conditions

- Requires `redis.mode` to be defined in `redisson.yaml`
- If missing, `RedissonClient` and `RedissonAccess` will not be registered
- Works with Spring Boot 2.x and 3.x (supports `spring.factories` and `AutoConfiguration.imports`)

---

## 📁 Directory Structure

```
redisson-spring-boot-starter/
├── RedissonAutoConfiguration.java
├── RedissonAccess.java
├── RedissonProperties.java
├── resources/
│   ├── redisson.yaml              # Imported via application.yml
│   └── META-INF/
│       └── spring.factories       # for Spring Boot 2.x
│       └── spring/.../AutoConfiguration.imports  # for Spring Boot 3.x
```

---

## 🧠 Notes

- `ObjectMapper` is injected from Spring context (inherits global config)
- Uses Jackson for JSON serialization when casting generic types
- Works best with POJOs with public getters/setters and default constructors

---

## 🧑‍💻 For Contributors

Pull requests welcome 🙌  
This starter is ready for extensions like:
- Redis Streams
- Lua scripting
- Metrics & HealthCheck auto-exposure

---

## 🔗 Related Projects

- [Redisson Official](https://github.com/redisson/redisson)
- [Spring Boot External Config](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)

---
```
