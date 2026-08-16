package com.tmt.input.http.mock

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * mock 컨트롤러들이 공유하는 인메모리 상태. 실구현이 리소스별로 들어올 때
 * 해당 빈과 컨트롤러만 지우면 되도록 전부 mock 패키지 안에 가둔다.
 */
@Configuration
class MockStoreConfig {
    @Bean
    fun mockPlaceStore(): InMemoryStore<MockPlace> =
        InMemoryStore<MockPlace>(idPrefix = "place").apply {
            SEED_PLACES.forEach { seed -> create { id -> seed(id) } }
        }

    @Bean
    fun mockAddressStore(): InMemoryStore<MockAddress> = InMemoryStore(idPrefix = "addr")

    @Bean
    fun mockAssetStore(): InMemoryStore<MockAsset> = InMemoryStore(idPrefix = "asset")

    @Bean
    fun mockSaveStore(): InMemoryStore<MockSave> = InMemoryStore(idPrefix = "save")

    @Bean
    fun mockTicketLedger(): MockTicketLedger = MockTicketLedger()

    @Bean
    fun mockReviewIdGenerator(): MockReviewIdGenerator = MockReviewIdGenerator()

    @Bean
    fun mockIdempotencyRegistry(): MockIdempotencyRegistry = MockIdempotencyRegistry()

    companion object {
        // 도그푸딩 시나리오용 시드 — 검색이 비어 있으면 FE가 1단계를 진행할 수 없다
        private val SEED_PLACES: List<(String) -> MockPlace> =
            listOf(
                { id -> MockPlace(id, "델리스피자", "서울 마포구 도화동 200-14", "마포구 도화동", "양식", 37.5399, 126.9515) },
                { id -> MockPlace(id, "오즈 커피", "서울 마포구 도화동 201-1", "마포구 도화동", "카페·디저트", 37.5401, 126.9520) },
                { id -> MockPlace(id, "서북면옥", "서울 중구 을지로3가 296-1", "중구 을지로3가", "한식", 37.5663, 126.9910) },
                { id -> MockPlace(id, "을지면옥", "서울 중구 입정동 161", "중구 입정동", "한식", 37.5667, 126.9925) },
                { id -> MockPlace(id, "한판승부", "서울 은평구 갈현동 403-38", "은평구 갈현동", "고기·구이", 37.6205, 126.9127) },
                { id -> MockPlace(id, "강남 초밥왕", "서울 강남구 역삼동 812-1", "강남구 역삼동", "일식", 37.4989, 127.0281) },
                { id -> MockPlace(id, "역전할머니맥주 강남점", "서울 강남구 역삼동 815-3", "강남구 역삼동", "주점", 37.4993, 127.0275) },
                { id -> MockPlace(id, "마피아 피자", "서울 양천구 신정동 948-1", "양천구 신정동", "양식", 37.5261, 126.8558) },
            )
    }
}

/** 사용자별 보유 티켓. 회원가입 기본 1장(T?), 상한 999장(T6). */
class MockTicketLedger {
    private val counts = ConcurrentHashMap<Long, Int>()

    fun availableCount(userId: Long): Int = counts.getOrDefault(userId, SIGNUP_BONUS)

    /** 티켓 1장 발급을 시도하고 실제 발급 수(0 또는 1)를 돌려준다. 상한 도달 시 0 (T6). */
    fun tryGrant(userId: Long): Int {
        var granted = 0
        counts.compute(userId) { _, current ->
            val base = current ?: SIGNUP_BONUS
            if (base < MAX_TICKETS) {
                granted = 1
                base + 1
            } else {
                granted = 0
                base
            }
        }
        return granted
    }

    companion object {
        const val SIGNUP_BONUS = 1
        const val MAX_TICKETS = 999
    }
}

class MockReviewIdGenerator {
    private val sequence = AtomicLong()

    fun next(): String = "review_${sequence.incrementAndGet()}"
}

/**
 * Idempotency-Key 등록부 (공통 규약 §9). 같은 사용자·같은 키·같은 바디면 최초 응답을
 * 재현하고, 바디가 다르면 IDEMPOTENCY_CONFLICT다.
 */
class MockIdempotencyRegistry {
    data class Entry(
        val bodyFingerprint: String,
        val saveId: String,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    /** 등록된 항목이 있으면 돌려주고, 없으면 null. 바디가 다르면 예외를 던지는 판단은 호출부 몫. */
    fun find(
        userId: Long,
        key: String,
    ): Entry? = entries["$userId:$key"]

    fun register(
        userId: Long,
        key: String,
        bodyFingerprint: String,
        saveId: String,
    ) {
        entries["$userId:$key"] = Entry(bodyFingerprint, saveId)
    }
}
