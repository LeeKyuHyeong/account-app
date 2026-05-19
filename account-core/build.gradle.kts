// account-core 모듈
//
// 책임:
//   - JPA Entity, Repository, Service (도메인 본체)
//   - Hibernate @Filter 기반 가구 격리 메커니즘
//   - Flyway 마이그레이션 (db/migration/)
//
// 의존성 정책 (§10.5):
//   - 다른 어떤 account-* 모듈에도 의존하지 않음 (단방향 결합)
//   - account-api, account-batch 가 본 모듈을 의존
//   - account-ai 는 본 모듈과 독립 (인터페이스만 공유)

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // ─── Spring Boot ───
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Task 4 — HouseholdFilterAspect (Hibernate @Filter 활성화) 용 AOP
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // ─── DB ───
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")

    // ─── 테스트 ───
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:mariadb:1.20.4")
}

// library jar — bootJar 비활성, plain jar 활성
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
tasks.named<Jar>("jar") {
    enabled = true
}
