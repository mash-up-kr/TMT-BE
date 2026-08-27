package com.tmt.input.http.mock

import java.time.Instant

/**
 * UT 대상자 계정(`X-User-Id: 1~4`)의 리뷰·그룹 가입·찜을 채운다.
 * 마이페이지·타인 프로필은 남의 데이터가 아니라 **그 사람 자신의 것**을 봐야 화면이 말이 되므로,
 * 시드 리뷰(작성자 999)와 별개로 각 계정에 붙인다. UT 시나리오가 바뀌면 이 파일만 고친다.
 */
object MockUserSeeds {
    /** (userId, placeId, rating, content, 요약 유무) */
    private val REVIEWS =
        listOf(
            Seed(1, "place_2", 5, "라떼가 부드럽고 자리가 넓어요. 노트북 하기 좋습니다.", withSummary = true),
            Seed(1, "place_5", 4, "고기 질이 좋고 밑반찬이 깔끔해요.", withSummary = true),
            Seed(2, "place_5", 5, "회식하기 딱 좋아요. 사장님이 친절하십니다.", withSummary = false),
            Seed(3, "place_3", 5, "육수가 슴슴한데 계속 들어가요. 겨울에 더 좋습니다.", withSummary = true),
            Seed(3, "place_4", 4, "면이 툭툭 끊기는 게 매력이에요.", withSummary = false),
            Seed(4, "place_2", 4, "디저트가 생각보다 알차요.", withSummary = false),
        )

    /** userId → 가입할 그룹 순번 (0-based, SEED_GROUPS 순서) */
    private val JOINS = mapOf(1L to listOf(0), 2L to listOf(1), 3L to listOf(0, 1))

    /** userId → 찜한 placeId (앞이 먼저 찜한 것) */
    private val FAVORITES =
        mapOf(
            1L to listOf("place_1", "place_6"),
            2L to listOf("place_7"),
            3L to listOf("place_3"),
        )

    /** 티켓 이력의 `작성 중` 행을 보여주려면 완성되지 않은 저장이 하나 필요하다 (T10). */
    private const val IN_PROGRESS_OWNER = 1L
    private const val IN_PROGRESS_PLACE = "place_8"

    fun apply(
        saveStore: InMemoryStore<MockSave>,
        assetStore: InMemoryStore<MockAsset>,
        groupStore: InMemoryStore<MockGroup>,
        membershipStore: MockMembershipStore,
        shareStore: MockReviewShareStore,
        favoriteStore: MockFavoriteStore,
        aiSummaryStore: MockAiSummaryStore,
        reviewIdGenerator: MockReviewIdGenerator,
    ) {
        val groups = groupStore.findAll()

        JOINS.forEach { (userId, indexes) ->
            indexes.forEach { index ->
                groups.getOrNull(index)?.let {
                    membershipStore.join(
                        it.groupId,
                        userId,
                        JOINED_AT.plusSeconds(
                            index * 60L,
                        ),
                    )
                }
            }
        }

        FAVORITES.forEach { (userId, placeIds) ->
            placeIds.forEachIndexed { index, placeId ->
                favoriteStore.add(userId, placeId, FAVORITED_AT.plusSeconds(index * 60L))
            }
        }

        REVIEWS.forEachIndexed { index, seed ->
            val asset =
                assetStore.create { id ->
                    MockAsset(assetId = id, ownerId = seed.userId, contentType = "image/jpeg", attached = true)
                }
            val reviewId = reviewIdGenerator.next()
            val at = REVIEWED_AT.plusSeconds(index * 3600L)
            val save =
                saveStore.create { id ->
                    MockSave(
                        saveId = id,
                        ownerId = seed.userId,
                        placeId = seed.placeId,
                        photoAssetIds = listOf(asset.assetId),
                        companionTagIds = listOf("tag_friend"),
                        positivePointTagIds = listOf("tag_tasty"),
                        rating = seed.rating,
                        content = seed.content,
                        reviewId = reviewId,
                        createdAt = at,
                        updatedAt = at,
                    )
                }
            if (seed.withSummary) {
                aiSummaryStore.put(reviewId, pros = "재료가 신선해요", cons = "웨이팅이 있을 수 있어요")
            }
            // 가입한 그룹에 공유해 둔다 — 그룹 상세 리뷰 목록이 비어 있으면 미가입 마스킹 화면도 못 그린다
            JOINS[seed.userId].orEmpty().forEach { groupIndex ->
                groups.getOrNull(groupIndex)?.let { shareStore.add(it.groupId, seed.userId, save.reviewId!!) }
            }
        }

        saveStore.create { id ->
            MockSave(
                saveId = id,
                ownerId = IN_PROGRESS_OWNER,
                placeId = IN_PROGRESS_PLACE,
                photoAssetIds = emptyList(),
                companionTagIds = emptyList(),
                positivePointTagIds = emptyList(),
                rating = null,
                content = null,
                reviewId = null,
                createdAt = IN_PROGRESS_AT,
                updatedAt = IN_PROGRESS_AT,
            )
        }
    }

    private data class Seed(
        val userId: Long,
        val placeId: String,
        val rating: Int,
        val content: String,
        val withSummary: Boolean,
    )

    private val JOINED_AT: Instant = Instant.parse("2026-08-13T00:00:00Z")
    private val FAVORITED_AT: Instant = Instant.parse("2026-08-14T00:00:00Z")
    private val REVIEWED_AT: Instant = Instant.parse("2026-08-15T00:00:00Z")
    private val IN_PROGRESS_AT: Instant = Instant.parse("2026-08-20T00:00:00Z")
}

/** 시드 적용이 부팅 시 한 번 돌았다는 표식 — 빈 그래프에 순서를 주기 위한 것이다. */
object MockUserSeedApplier
