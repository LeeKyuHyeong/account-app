// account-api 모듈
//
// 책임:
//   - REST 엔드포인트 (@RestController)
//   - Spring Security + JWT 인증
//   - HouseholdContextFilter (가구 격리 진입점)
//   - Spring Boot 실행 가능 jar (bootRun, bootJar)
//
// 의존성 정책:
//   - account-core (Entity, Repository, Service)
//   - account-ai (영수증 분석)

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // ─── 모듈 간 의존 ───
    implementation(project(":account-core"))
    implementation(project(":account-ai"))

    // ─── Spring Boot ───
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // account-core 의 JPA starter 가 implementation 스코프라 jakarta.persistence /
    // @Transactional / JpaRepository 등이 컴파일 노출 안 됨. account-api 도 controller /
    // service 작성 시 JPA 타입을 직접 다루므로 starter 를 명시 의존.
    // 향후 account-core 가 java-library plugin 으로 전환되면 api(...) 스코프로 정리 가능.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // ─── JWT (jjwt) ───
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // ─── 테스트 ───
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:mariadb:1.20.4")
}
