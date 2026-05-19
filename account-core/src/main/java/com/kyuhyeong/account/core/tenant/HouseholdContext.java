package com.kyuhyeong.account.core.tenant;

/**
 * 현재 요청의 가구 ID 보관소 (ThreadLocal).
 *
 * <p>본 프로젝트의 multi-tenant 격리 메커니즘의 진입점 (docs/account.md §6.2).
 *
 * <p>흐름:
 * <ol>
 *   <li>{@code HouseholdContextFilter} (Servlet) 가 요청 진입 시 가구 ID 를 추출해 {@link #set}.
 *   <li>{@code HouseholdFilterAspect} 가 {@code @Transactional} 메서드 진입 시점에
 *       {@link #get} 으로 ID 를 읽어 Hibernate {@code householdFilter} 를 활성화.
 *   <li>응답 직전 finally 블록에서 {@link #clear}.
 * </ol>
 *
 * <p>본 클래스는 인스턴스화하지 않는다 (static helper).
 */
public final class HouseholdContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private HouseholdContext() {
    }

    /**
     * 현재 스레드에 가구 ID 를 바인딩.
     *
     * @throws IllegalArgumentException {@code householdId} 가 null
     */
    public static void set(Long householdId) {
        if (householdId == null) {
            throw new IllegalArgumentException("householdId must not be null");
        }
        CURRENT.set(householdId);
    }

    /**
     * 현재 스레드에 바인딩된 가구 ID.
     *
     * @throws IllegalStateException 바인딩 없이 호출된 경우 — 격리 누수의 직접 신호이므로
     *         조용한 기본값 대신 즉시 실패하도록 의도된 설계.
     */
    public static Long get() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException(
                    "HouseholdContext not set — request did not pass through HouseholdContextFilter");
        }
        return id;
    }

    /** 바인딩 여부. 비격리 경로 (헬스체크 등) 에서 분기용. */
    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
