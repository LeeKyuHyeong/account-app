/**
 * 도메인 엔티티 패키지.
 *
 * <p>모든 도메인 엔티티가 공유하는 Hibernate {@code householdFilter} 정의를 본 패키지
 * 레벨에 둔다. 개별 엔티티는 {@code @Filter(name = "householdFilter")} 만으로 활성화한다
 * (docs/account.md §6.2 Task 4).
 *
 * <p>비격리 엔티티 (User / Household / HouseholdMember) 에는 {@code @Filter} 를 붙이지
 * 않는다 — User 는 글로벌 식별 단위, Household 는 격리 단위 자신, HouseholdMember 는
 * 격리 메커니즘의 본체.
 */
@org.hibernate.annotations.FilterDef(
        name = "householdFilter",
        parameters = @org.hibernate.annotations.ParamDef(
                name = "currentHouseholdId", type = Long.class
        )
)
package com.kyuhyeong.account.core.entity;
