// account-batch 모듈
//
// 책임 (Week 5+ 구체화):
//   - 월별 카테고리 집계 (monthly_summaries 사전 계산) — 첫 잡
//   - 영수증 이미지 단계적 압축/삭제 (v1.1)
//   - FCM 알림 발송 (v1.1)
//
// 현재는 Spring 의 기본 @Scheduled 로 단순한 잡 한 개만. 잡 개수가 늘거나 재시도 /
// 분산 락 / 청크 처리가 필요해지면 Spring Batch 또는 Quartz 도입을 검토.
// account-api 가 본 모듈을 의존해 단일 프로세스로 같이 기동된다 (운영에서 트래픽이
// 늘면 별도 프로세스 분리 가능 — entrypoint 를 추가하면 됨).

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":account-core"))

    implementation("org.springframework.boot:spring-boot-starter")
    // account-core 의 JPA starter 가 implementation 스코프라 jakarta.persistence /
    // @Transactional 컴파일 노출이 안 됨. batch 잡 안에서 @Transactional 을 직접 쓰므로
    // starter 를 명시 의존 (account-api 와 동일 이유).
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}

// 별도 부팅 entrypoint 아직 없음. library jar로 취급.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
tasks.named<Jar>("jar") {
    enabled = true
}
