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
    fun mockAssetStore(): InMemoryStore<MockAsset> = InMemoryStore(idPrefix = "asset")

    /** 리뷰·저장은 전부 UT2 시드가 채운다 (TMT-213) — [MockUt2Seeds]. */
    @Bean
    fun mockSaveStore(): InMemoryStore<MockSave> = InMemoryStore(idPrefix = "save")

    @Bean
    fun mockAiSummaryStore(): MockAiSummaryStore = MockAiSummaryStore()

    @Bean
    fun mockTicketLedger(): MockTicketLedger = MockTicketLedger()

    @Bean
    fun mockReviewIdGenerator(): MockReviewIdGenerator = MockReviewIdGenerator()

    @Bean
    fun mockIdempotencyRegistry(): MockIdempotencyRegistry = MockIdempotencyRegistry()

    @Bean
    fun mockFavoriteStore(): MockFavoriteStore = MockFavoriteStore()

    @Bean
    fun mockUserStore(): MockUserStore = MockUserStore(SEED_USERS)

    /**
     * UT2 콘텐츠를 붙인다 (TMT-213). 다른 시드 빈이 모두 만들어진 뒤에 실행돼야 해서
     * 스토어를 전부 주입받는 빈으로 둔다 — 반환값은 쓰이지 않고 부팅 시 한 번 도는 것이 전부다.
     */
    @Bean
    fun mockUserSeedApplier(
        mockPlaceStore: InMemoryStore<MockPlace>,
        mockSaveStore: InMemoryStore<MockSave>,
        mockAssetStore: InMemoryStore<MockAsset>,
        mockGroupStore: InMemoryStore<MockGroup>,
        mockMembershipStore: MockMembershipStore,
        mockReviewShareStore: MockReviewShareStore,
        mockAiSummaryStore: MockAiSummaryStore,
        mockReviewIdGenerator: MockReviewIdGenerator,
        mockTicketLedger: MockTicketLedger,
    ): MockUserSeedApplier {
        MockUt2Seeds.apply(
            mockPlaceStore,
            mockSaveStore,
            mockAssetStore,
            mockGroupStore,
            mockMembershipStore,
            mockReviewShareStore,
            mockAiSummaryStore,
            mockReviewIdGenerator,
            mockTicketLedger,
        )
        return MockUserSeedApplier
    }

    @Bean
    fun placeCardAssembler(
        mockMediaUrls: MockMediaUrls,
        mockSaveStore: InMemoryStore<MockSave>,
        mockFavoriteStore: MockFavoriteStore,
    ): PlaceCardAssembler = PlaceCardAssembler(mockMediaUrls, mockSaveStore, mockFavoriteStore)

    /** 그룹은 전부 UT2 시드가 만든다 (TMT-213) — [MockUt2Seeds]. */
    @Bean
    fun mockGroupStore(): InMemoryStore<MockGroup> = InMemoryStore(idPrefix = "group")

    @Bean
    fun mockMembershipStore(): MockMembershipStore = MockMembershipStore()

    @Bean
    fun mockReviewShareStore(): MockReviewShareStore = MockReviewShareStore()

    @Bean
    fun groupAssembler(
        mockMediaUrls: MockMediaUrls,
        mockSaveStore: InMemoryStore<MockSave>,
        mockMembershipStore: MockMembershipStore,
        mockReviewShareStore: MockReviewShareStore,
    ): GroupAssembler = GroupAssembler(mockMediaUrls, mockSaveStore, mockMembershipStore, mockReviewShareStore)

    @Bean
    fun reviewCardAssembler(
        mockMediaUrls: MockMediaUrls,
        mockPlaceStore: InMemoryStore<MockPlace>,
        mockFavoriteStore: MockFavoriteStore,
        mockAiSummaryStore: MockAiSummaryStore,
        mockUserStore: MockUserStore,
    ): ReviewCardAssembler =
        ReviewCardAssembler(mockMediaUrls, mockPlaceStore, mockFavoriteStore, mockAiSummaryStore, mockUserStore)

    companion object {
        /**
         * UT 대상자 계정 4개 — `X-User-Id: 1~4`가 그대로 이 사람들이다.
         * 가입 그룹은 [MockUt2Seeds]가 붙인다 — 리뷰는 대상자가 UT에서 직접 쓴다. 여기 없는 ID로도 호출은 되지만
         * 타인 프로필(`GET /v1/users/{userId}`)은 USER_NOT_FOUND다.
         */
        private val SEED_USERS: List<MockUser> =
            listOf(
                MockUser(1, "조용한 미식가", "tester1@example.com"),
                MockUser(2, "매콤한 하루", "tester2@example.com"),
                MockUser(3, "면요리 연구가", "tester3@example.com"),
                MockUser(4, "커피 마시는 곰", null),
                // UT2 콘텐츠(TMT-213)의 persona 작성자들 — 그룹장·리뷰 작성자로 쓰인다
                MockUser(MockUt2Seeds.PERSONA_OFFICE, "회사원 미식러", null),
                MockUser(MockUt2Seeds.PERSONA_JAMSIL, "잠실 토박이", null),
                MockUser(MockUt2Seeds.PERSONA_EXPLORER, "골목 탐험가", null),
            )

        /**
         * UT2 시드가 이름으로 찾아 재사용하는 유일한 매장 (TMT-213, MockUt2Seeds.apply).
         * 나머지 매장·그룹·리뷰는 UT2 콘텐츠가 대신하므로 걷어냈다 (TMT-248).
         */
        private val SEED_PLACES: List<(String) -> MockPlace> =
            listOf(
                { id -> MockPlace(id, "한판승부", "서울 은평구 갈현동 403-38", "은평구 갈현동", "고기·구이", 37.6205, 126.9127) },
            )
    }
}

/** 시드 적용이 부팅 시 한 번 돌았다는 표식 — 빈 그래프에 순서를 주기 위한 것이다. */
object MockUserSeedApplier

/**
 * 사용자별 보유 티켓과 그 이력. 회원가입 기본 1장(T2), 상한 999장(T6).
 * 잔액은 이력의 합이 정본이다 (T5) — 내 티켓 배너와 이력이 어긋나지 않게 한 곳에서 나온다.
 */
class MockTicketLedger {
    // 값이 불변 리스트다 — 갱신은 전부 compute 안에서 통째로 갈아끼우므로
    // 잔액 확인과 기록이 한 번에 일어나고(원자적), 읽는 쪽은 스냅샷을 본다
    private val entries = ConcurrentHashMap<Long, List<MockTicketEntry>>()
    private val sequence = AtomicLong()

    fun availableCount(userId: Long): Int = historyOf(userId).sumOf { it.amount }

    /**
     * 가입 보상 없이 잔고 0으로 시작시킨다 (UT2 임시 — TMT-213).
     * 이력을 빈 리스트로 미리 채워두면 [historyOf]·[append]가 가입 보상 행을 만들지 않는다.
     */
    fun startWithNoTickets(userId: Long) {
        entries.putIfAbsent(userId, emptyList())
    }

    /** 발급·소비 이력. 미완성 저장(SAVE_IN_PROGRESS)은 저장에서 파생하므로 여기 없다 (T10). */
    fun historyOf(userId: Long): List<MockTicketEntry> = entries.computeIfAbsent(userId) { listOf(signupReward(it)) }

    /** 티켓 1장 회수를 시도한다. 잔고가 0이면 false — 리뷰 삭제가 거부되는 조건이다 (R7). */
    fun tryConsume(
        userId: Long,
        type: TicketEntryType,
        saveId: String? = null,
        placeId: String? = null,
        groupId: String? = null,
    ): Boolean = append(userId, type, amount = -1, saveId = saveId, placeId = placeId, groupId = groupId) { it > 0 }

    /** 티켓 1장 발급을 시도하고 실제 발급 수(0 또는 1)를 돌려준다. 상한 도달 시 0 (T6). */
    fun tryGrant(
        userId: Long,
        saveId: String? = null,
        placeId: String? = null,
    ): Int {
        val granted =
            append(
                userId,
                TicketEntryType.REVIEW_REWARD,
                amount = 1,
                saveId = saveId,
                placeId = placeId,
                groupId = null,
            ) {
                it < MAX_TICKETS
            }
        return if (granted) 1 else 0
    }

    /** 잔액 판정과 기록을 한 compute 안에서 끝낸다 — 같은 사용자의 동시 요청이 잔액을 두 번 쓰지 못한다. */
    private fun append(
        userId: Long,
        type: TicketEntryType,
        amount: Int,
        saveId: String?,
        placeId: String?,
        groupId: String?,
        allows: (Int) -> Boolean,
    ): Boolean {
        var applied = false
        entries.compute(userId) { id, current ->
            val history = current ?: listOf(signupReward(id))
            if (!allows(history.sumOf { it.amount })) {
                history
            } else {
                applied = true
                history +
                    MockTicketEntry(
                        entryId = nextEntryId(),
                        userId = id,
                        type = type,
                        amount = amount,
                        saveId = saveId,
                        placeId = placeId,
                        groupId = groupId,
                        occurredAt = java.time.Instant.now(),
                    )
            }
        }
        return applied
    }

    // 잔액이 이력의 합이라, 가입 보상 행이 없으면 아무 이력도 없는 사용자의 잔액이 0이 된다 (T2)
    private fun signupReward(userId: Long) =
        MockTicketEntry(
            entryId = nextEntryId(),
            userId = userId,
            type = TicketEntryType.SIGNUP_REWARD,
            amount = SIGNUP_BONUS,
            saveId = null,
            placeId = null,
            groupId = null,
            occurredAt = SIGNUP_AT,
        )

    private fun nextEntryId(): String = "tkh_${sequence.incrementAndGet()}"

    companion object {
        const val SIGNUP_BONUS = 1
        const val MAX_TICKETS = 999

        // 가입 보상은 이력의 맨 아래에 오도록 과거 시각으로 고정한다
        private val SIGNUP_AT: java.time.Instant = java.time.Instant.parse("2026-08-01T00:00:00Z")
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
    /**
     * 최초 응답을 통째로 들고 재현한다 — 상태에서 다시 조립하면 이번 요청으로 발급된
     * 티켓 수(grantedCount) 같은 "그 순간의 값"이 복원되지 않는다.
     * DB 스키마의 `idempotency_key.response_body JSONB`에 대응한다.
     */
    data class Entry(
        val bodyFingerprint: String,
        val response: Any,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    /** 등록된 항목이 있으면 돌려주고, 없으면 null. 바디가 다르면 예외를 던지는 판단은 호출부 몫. */
    fun find(
        userId: Long,
        endpoint: String,
        key: String,
    ): Entry? = entries[keyOf(userId, endpoint, key)]

    fun register(
        userId: Long,
        endpoint: String,
        key: String,
        bodyFingerprint: String,
        response: Any,
    ) {
        entries[keyOf(userId, endpoint, key)] = Entry(bodyFingerprint, response)
    }

    // 규약·DB PK와 같은 (user_id, endpoint, idem_key) 조합. endpoint가 빠지면
    // 서로 다른 엔드포인트가 키 공간을 공유해 남의 응답이 재현된다.
    private fun keyOf(
        userId: Long,
        endpoint: String,
        key: String,
    ): String = "$userId:$endpoint:$key"
}
