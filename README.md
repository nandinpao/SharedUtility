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
  <groupId>com.example</groupId>
  <artifactId>pg-spring-boot-starter</artifactId>
  <version>1.0.0</version>
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
    mapper-locations: classpath:/mappers/**/*.xml
    type-aliases-package: com.example.pgstarter.entity
    base-packages:
      - com.agitg.pgstarter.mapper
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
}
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
| MyBatis  | 3.0.2 (via `mybatis-spring-boot-starter`) |
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
