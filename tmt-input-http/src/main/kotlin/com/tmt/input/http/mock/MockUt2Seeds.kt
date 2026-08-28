package com.tmt.input.http.mock

import java.time.Instant

/**
 * UT2 콘텐츠 시드 (TMT-213) — 디자이너가 UT 대상자들의 실제 저장 목록을 모아 만든
 * 그룹 7개·리뷰 21건. 리뷰 본문은 대상자가 쓴 원문 그대로이고(500자 초과 1건만 축약),
 * 사진은 media 버킷 `seed/ut2/` 아래 올려둔 실사진이다 ([MockSeedMediaUrls] 오버라이드).
 *
 * 매장 좌표·주소는 상가정보(2026-06) 기준. '데이터 미확인' 주석이 붙은 매장은
 * 공공데이터·웹 어디에도 없어 동네 좌표로 근사했다 — 디자이너 확인 후 보정한다.
 */
object MockUt2Seeds {
    private const val PHOTO_BASE = "https://ttalkkak-tmt-media.s3.ap-northeast-2.amazonaws.com/seed/ut2"

    /** persona 작성자 — MockStoreConfig.SEED_USERS에 함께 등록된다. UT 대상자(1~4)와 겹치지 않는 ID. */
    const val PERSONA_OFFICE = 901L // 회사원 미식러
    const val PERSONA_JAMSIL = 902L // 잠실 토박이
    const val PERSONA_EXPLORER = 903L // 골목 탐험가

    private val PERSONAS = listOf(PERSONA_OFFICE, PERSONA_JAMSIL, PERSONA_EXPLORER)

    /**
     * UT 대상자별 가입 그룹 (GROUPS 순번). 전부 가입시키면 가입 플로우·미가입 마스킹을 못 태우므로 일부만.
     * 마지막 그룹(7 — 마곡)에는 **아무도 넣지 않는다** — 네 명 모두 가입 플로우를 태울 그룹이 하나는 있어야 한다.
     */
    private val UT_USER_JOINS =
        mapOf(
            1L to listOf(0, 1, 2, 3),
            2L to listOf(0, 1, 2, 4),
            3L to listOf(0, 1, 2, 5),
            4L to listOf(0, 1, 2, 6),
        )

    private data class Ut2Place(
        val key: String,
        val name: String,
        val roadAddress: String,
        val regionName: String,
        val categoryName: String?,
        val latitude: Double,
        val longitude: Double,
    )

    private data class Ut2Group(
        val name: String,
        val oneLine: String,
        val foodCategoryId: String,
        val regionTagId: String,
        val ownerId: Long,
    )

    private data class Ut2Review(
        val placeKey: String,
        val authorId: Long,
        val groupIndexes: List<Int>,
        val rating: Int,
        val companionTagIds: List<String>,
        val positivePointTagIds: List<String>,
        val photoDir: String,
        val photoCount: Int,
        val content: String,
        val pros: String? = null,
        val cons: String? = null,
    )

    private val PLACES =
        listOf(
            Ut2Place("sasanoha", "사사노하", "서울 송파구 백제고분로42길 4-13", "송파구 송파동", "주점", 37.50560, 127.10897),
            Ut2Place("dorimhang", "도림항 본점", "서울 관악구 조원로4길 8", "관악구 신림동", "해산물", 37.4829942, 126.9043818),
            // 데이터 미확인 — 구디 야장 골목 인근으로 근사
            Ut2Place("mukeunji", "우리집 묵은지 생삼겹", "서울 구로구 구로동", "구로구 구로동", "고기·구이", 37.4835, 126.8975),
            Ut2Place("keunjip", "큰집", "서울 구로구 도림로10길 23", "구로구 구로동", "고기·구이", 37.4857827, 126.8887635),
            Ut2Place("malttuk", "말뚝곱창", "서울 구로구 시흥대로 571", "구로구 구로동", "고기·구이", 37.4839742, 126.9018140),
            Ut2Place("niwasushi", "니와스시참치", "서울 구로구 시흥대로163길 33", "구로구 구로동", "일식", 37.4819979, 126.8980968),
            Ut2Place("ohansu", "오한수우육면가", "서울 구로구 디지털로31길 41", "구로구 구로동", "아시안", 37.4851647, 126.8927557),
            Ut2Place("drunkenthai", "드렁킨타이", "서울 구로구 디지털로26길 111", "구로구 구로동", "아시안", 37.4825826, 126.8970445),
            Ut2Place("rondo", "론도론도", "서울 서대문구 연희맛로 17-13", "서대문구 연희동", "바", 37.5667778, 126.9290975),
            Ut2Place("eoseureum", "어스름", "서울 강남구 도산대로57길 7", "강남구 청담동", "한식", 37.5241, 127.0415),
            Ut2Place("golsu", "골수", "서울 중구 을지로3가 296-16", "중구 을지로3가", "한식", 37.5658, 126.9920),
            Ut2Place("hwadol", "화돌농장 신정점", "서울 양천구 중앙로34길 12", "양천구 신정동", "고기·구이", 37.5192354, 126.8541449),
            Ut2Place("kushinoa", "쿠시노아 마곡나루점", "서울 강서구 마곡중앙로 161-22", "강서구 마곡동", "주점", 37.5686437, 126.8257842),
            // 데이터 미확인 — 마곡나루 인근으로 근사
            Ut2Place("sodam", "소담면옥", "서울 강서구 마곡동", "강서구 마곡동", "한식", 37.5680, 126.8290),
            Ut2Place("byeolmi", "별미곱창", "서울 송파구 오금로11길 11", "송파구 방이동", "고기·구이", 37.5146536, 127.1083610),
            Ut2Place("geumdon", "금돈옥 잠실점", "서울 송파구 백제고분로 83", "송파구 잠실동", "고기·구이", 37.5094598, 127.0793198),
            Ut2Place("thebitnam", "더빛남", "서울 송파구 오금로18길 5", "송파구 송파동", "아시안", 37.5101913, 127.1108203),
            Ut2Place("hikiniku", "히키니쿠토코메 도산", "서울 강남구 선릉로155길 21", "강남구 신사동", "일식", 37.5255002, 127.0379432),
            Ut2Place("mur", "무르", "서울 강남구 테헤란로29길 8", "강남구 역삼동", "주점", 37.5021639, 127.0389040),
            Ut2Place("younghyang", "영향", "서울 금천구 남부순환로108길 7", "금천구 가산동", "양식", 37.4783171, 126.8927881),
            Ut2Place("sotnaeum", "솥내음 마곡역점", "서울 강서구 마곡중앙6로 66", "강서구 마곡동", "한식", 37.5599164, 126.8343610),
            Ut2Place("menshokatsu", "멘쇼카츠 발산역점", "서울 강서구 강서로 378", "강서구 등촌동", "일식", 37.5592559, 126.8388443),
            // 마곡점·발산점 두 지점이 있고 리뷰에 단서가 없어 그룹 주제(마곡)를 따랐다
            Ut2Place("sokcho", "속초그바람에 마곡점", "서울 강서구 마곡중앙6로 10", "강서구 마곡동", "해산물", 37.5603852, 126.8282142),
            // 은평구 갈현동 한판승부는 기존 시드(SEED_PLACES)에 이미 있어 여기서 만들지 않는다 → apply()에서 이름으로 찾는다
        )

    private val GROUPS =
        listOf(
            Ut2Group(
                "한국인인데 김치보다 회를 더 좋아하는 한국 사람 추천 숙성회 맛집",
                "숙성회에 진심인 사람들",
                "cat_seafood",
                "region_seoul_all",
                PERSONA_JAMSIL,
            ),
            Ut2Group("법카로 회식하기 좋은 고깃집(구디)", "구디 회식은 여기서 정합니다", "cat_meat", "region_guro", PERSONA_OFFICE),
            Ut2Group("점심때 회사 사람들이랑 맛있는거 먹고싶을 때 가는 맛집(구디)", "구디 점심 원정대", "cat_korean", "region_guro", PERSONA_OFFICE),
            Ut2Group("나만 알고 싶은 분위기 좋은 데이트 장소", "소개하기 아까운 곳만 모음", "cat_bar", "region_seoul_all", PERSONA_EXPLORER),
            Ut2Group("강서구에도 맛집 많다 무시하지마라(강서구)", "강서구 맛집 부심", "cat_korean", "region_gangseo", PERSONA_EXPLORER),
            Ut2Group("잠실에서 놀면 맨날 여기만 가는 맛집 모음(잠실)", "잠실 단골집만 모았습니다", "cat_meat", "region_songpa", PERSONA_JAMSIL),
            Ut2Group(
                "강남에서 몇 안되는 체인점 아닌 내 맛집 모임(강남)",
                "강남에서 찾은 진짜 단골집",
                "cat_japanese",
                "region_gangnam",
                PERSONA_JAMSIL,
            ),
            Ut2Group(
                "마곡에서 일하는 사람들을 위한 맛집 모임(강서구)",
                "마곡 직장인 점심 원정대",
                "cat_korean",
                "region_gangseo",
                PERSONA_OFFICE,
            ),
        )

    // 사사노하는 g1·g6 두 그룹에 같은 리뷰가 공유된다 — save 1건, 공유 2건
    private val REVIEWS =
        listOf(
            Ut2Review(
                "sasanoha",
                PERSONA_JAMSIL,
                listOf(0, 5),
                5,
                listOf("tag_friend"),
                listOf("tag_tasty", "tag_value"),
                "g6-sasanoha",
                3,
                "근처 술집 중에서도 유독 리뷰가 많고 별점이 높아서 석촌호수 가게 되면 들려야지 하고 찜 해놓았다가 가봤는데 웬걸... 인생 맛집이 될 줄이야... 가격도 싸고 이것저것 종류별로 시킬 수 있어서 너무 좋아요. 일본인도 방문할 정도로 제대로 된 이자카야 집입니다~ 특히 양맥이랑 같이 먹는 숙성회가 짱!! 근데 갈때마다 웨이팅이 사악해서 가기 2시간 전에 꼭 캐치테이블로 미리 웨이팅 걸어놓고 가세요… 서서 먹으면 앉아서 먹는거 보다는 빨리 들어갈 수 있음. 그리고 전부 닷지 테이블이라서 단체 방문은 불가능ㅠㅠ",
                pros = "숙성회가 신선하고 가성비가 좋아요",
                cons = "웨이팅이 길고 단체 방문은 어려워요",
            ),
            Ut2Review(
                "dorimhang",
                PERSONA_EXPLORER,
                listOf(0),
                5,
                listOf("tag_couple"),
                listOf("tag_tasty", "tag_mood"),
                "g1-dorimhang",
                3,
                "구로동, 신림동에서 손꼽히는 이자카야✨ 서울에서도 몇 안 되는 최고의 맛집이라 자부할 수 있어요. 생일날 남자친구와 방문했는데 정말 최고의 선택이었습니다! 다만 평일에도 웨이팅 심하니까 꼭 어플로 예약 후 방문하시길 추천드려요:)",
                pros = "분위기가 좋고 특별한 날에 어울려요",
                cons = "평일에도 웨이팅이 있어 예약이 필요해요",
            ),
            Ut2Review(
                "hanpan",
                PERSONA_OFFICE,
                listOf(0),
                5,
                listOf("tag_friend"),
                listOf("tag_tasty"),
                "g1-hanpan",
                3,
                "이 집의 단점은 가격대가 조금 높다는 점이에요. 하지만 한 번도 안 가본 사람은 있어도 한 번만 간 사람은 없을 만큼 중독적인 맛집입니다. 저희는 너무 맛있어서 둘이서 메인 메뉴 세 가지나 시켜 먹은 적도 있고 한 번은 1차에서 회랑 파스타 먹고 2차로 옆자리로 옮겨서 3명에서 후토마키와 탕까지 먹은 적도 있어요. 그만큼 진심으로 추천할 수 있는 집입니다🙌 근데 최대 수용 인원이 3명까지인게 단점ㅠㅠ",
                pros = "메뉴 구성이 다양하고 중독성 있는 맛이에요",
                cons = "가격대가 높고 최대 3명까지만 갈 수 있어요",
            ),
            Ut2Review(
                "mukeunji",
                PERSONA_OFFICE,
                listOf(1),
                4,
                listOf("tag_friend"),
                listOf("tag_mood"),
                "g2-mukeunji",
                3,
                "친구가 야장 삼겹살이 먹고 싶다고 해서 마침 회사 동료가 추천해준 맛집이 떠올라 가보았어요. 묵은지가 킥이었고 요즘 같은 날씨에 즐기는 야장은 그야말로 행복입니다:) 근데 가격이 굉장히 사악하긴 함. 삼겹살도 일반 냉동 삼겹…",
            ),
            Ut2Review(
                "keunjip",
                PERSONA_OFFICE,
                listOf(1),
                5,
                listOf("tag_colleague"),
                listOf("tag_tasty", "tag_value"),
                "g2-keunjip",
                3,
                "회사 회식으로 간 삼겹살집... 가게는 허름한데 고기가 1인분에 16,000원 하길래 좀 비싼 거 같기도... 했지만 전혀 1도 돈이 아깝다는 생각이 들지 않았음 지금까지 내가 가본 삼겹살집 중에 제일 두툼하고 맛있었음ㅠㅠ 정말 사장님 맛잘알인게 메뉴에 미나리 추가도 있었고 분명 삼겹살 시켰는데 묵은지도 같이 주심 김치 러버는 죽어ㅎㅎ 그리고는 두번째로 가브리살을 시켰는데 응..? 우리 목살시켰나..? 생맥주는 또 왜 이렇게 싼 건지..! 생맥주는 3,000원이고 스텔라 생맥주는 5,000원..! 여기 계란찜에는 새우도 넣어서 주심... 마지막 입가심으로 먹은 차돌박이까지 모든 메뉴가 다 100% 만족스러웠던 집ㅠㅠ 다음에 가면 다른 메뉴들도 진짜 다 뿌순다..!",
                pros = "고기가 두툼하고 생맥주가 저렴해요",
                cons = "회식 테이블이 떨어져 있을 수 있어요",
            ),
            Ut2Review(
                "malttuk",
                PERSONA_JAMSIL,
                listOf(1),
                4,
                listOf("tag_colleague"),
                listOf("tag_tasty"),
                "g2-malttuk",
                3,
                "가끔 그런 날이 있잖아요... 폭력적이고, 입안 가득 기름진 음식을 먹고 싶은 그런 날... 구디에서 유명한 말뚝곱창. 구디에 지점이 제일 많이 있는 데는 이유가 있습니다ㅎㅎ 저는 떡을 별로 안 좋아하는데 생각보다 떡이 너무 부드럽고 맛있었어요! 추가는 절대 안하는 사람인데 이날은 떡 추가해서 먹었습니다. 근데 아무래도 소곱창이라 매장에 기름이 많고 테이블에 기름이 많았습니다. 매장 깨끗한거 선호하시는 분은 약간 그럴수도…",
            ),
            Ut2Review(
                "niwasushi",
                PERSONA_OFFICE,
                listOf(2),
                4,
                listOf("tag_colleague"),
                listOf("tag_value"),
                "g3-niwasushi",
                1,
                "초밥집 많이 가봤는데 나쁘지 않음. 회도 두툼하고 맛도 있음. 구로에서 초밥 먹고싶으면 내가 가본 곳 중에 제일 추천. 점심 메뉴 시키면 우동이랑 튀김도 같이 나옴. 근데 남자분들이 가면 양이 좀 적을수도 있음.",
            ),
            Ut2Review(
                "ohansu",
                PERSONA_EXPLORER,
                listOf(2),
                4,
                listOf("tag_colleague"),
                listOf("tag_tasty"),
                "g3-ohansu",
                1,
                "우육탕면 먹으러 왔는데 확실히 실패 없는 맛. 국물이 갈비탕 육수랑 비슷한 느낌인데 훨씬 진하고 감칠맛남. 군만두도 많이 말고 맛만 볼 수 있을 정도로 시킬 수 있음. 파랑 고수도 무료. 근데 내가 갔을 때는 서비스가 좀 아쉬웠음.",
                pros = "국물이 진하고 감칠맛이 좋아요",
                cons = "서비스가 아쉬울 때가 있어요",
            ),
            Ut2Review(
                "drunkenthai",
                PERSONA_OFFICE,
                listOf(2),
                4,
                listOf("tag_colleague"),
                listOf("tag_tasty"),
                "g3-drunkenthai",
                1,
                "새콤한 맛과 동남아 커리의 고소하고 부드럽게 퍼지는 질감을 맛보기 좋은 곳. 애매하게 로컬화되지 않은 맛. 커리 먹으러 다시 가고 싶은 곳. 근데 11시 20분쯤 안가면 자리가 없어서 못 먹을수도 있음. 구로에서 제일 맛있는 가게 탑 5안에 듬. 매장이 약간 작아서 회식이나 단체로 가기는 좀 애매함.",
            ),
            Ut2Review(
                "rondo",
                PERSONA_EXPLORER,
                listOf(3),
                5,
                listOf("tag_couple"),
                listOf("tag_mood", "tag_value"),
                "g4-rondo",
                3,
                "남자친구 생일에 지인들과 함께 방문했어요! 와인에 막 입문했을 때였는데 가격도 합리적이고 안주도 맛있어서 와인 초보자에게 딱 좋은 곳이었습니다. 다만 메뉴 양이 조금 적어서 여러 가지를 함께 주문해야 했어요:)",
                pros = "와인 입문자에게 좋고 가격이 합리적이에요",
                cons = "메뉴 양이 적은 편이에요",
            ),
            Ut2Review(
                "eoseureum",
                PERSONA_JAMSIL,
                listOf(3),
                5,
                listOf("tag_couple"),
                listOf("tag_mood"),
                "g4-eoseureum",
                3,
                "크리스마스날 남자친구와 방문했어요! 이번에는 코스로 즐겼는데 다음에는 와인이랑 단품 메뉴를 따로 시켜보고 싶었어요. 매장이 한식을 재해석한 다이닝바라서 코스 메뉴가 계절마다 바뀌는 점이 인상적이었어요. 메뉴들 양이 좀 적어서 코스는 양이 딱 적당했지만 단품으로 먹는다면 조금 아쉬울 수도 있을 것 같아요:)",
            ),
            Ut2Review(
                "golsu",
                PERSONA_EXPLORER,
                listOf(3),
                5,
                listOf("tag_friend"),
                listOf("tag_tasty"),
                "g4-golsu",
                3,
                "인스타 맛집은 잘 안믿는 1인 ....\n근데 여긴 진짜 쥐림 .. 에바임 진짜 에바임\n일부러 인스타에 없는 맛집들 찾아가려고 굉장히 애쓰는 편인데 수제비에 소주가 너무 좋아보여서 감. 사실 첫인상이 생각보다 양이 막 많고 맛있는 거는 아닌데,\n수육 끝나고 먹은 수제비가 ㄹㅇ 지렸음 ... 1시간안에 여자 둘이서 소주 5병 까고 2차갔는데 기억이없음. 수제비만 리필해서 두 번 정도 더 먹고 싶었음. 여기는 메인 메뉴가 ... 뼈구이랑 수육전골 + 수제비인데, 2명이서 가면 둘다 먹을 수는.. 없는 .. 그래서 무조건 4명이서 가서 뼈구이 하나 수육전골 하나 먹어야함 ㅠㅠ !! 감자탕도 진짜 진짜 맛있어 보였는데 못먹었음 .. 왜냐면 배가 없어서 .....\n여기는 진짜 꼭 4명이서 가길 바람 그래야 여러 종류로 먹을 수 있음",
                pros = "수제비와 수육전골 조합이 훌륭해요",
                cons = "2명이서는 메뉴를 다양하게 못 시켜요",
            ),
            Ut2Review(
                "hwadol",
                PERSONA_EXPLORER,
                listOf(4),
                4,
                listOf("tag_friend"),
                listOf("tag_tasty"),
                "g5-hwadol",
                3,
                "신정역 근처 내 원픽 맛집. 원래 맛집은 아저씨들 얼마나 있는지 보고 알 수 있다고 했는데 가보면 할아버지랑 아저씨밖에 없음. 나 갔을때는 할아버지들 회식하고 있었음. 오리고기집인데 삼겹살이 더 맛있음 그리고ㅠㅠ 오리고기, 냉동, 생삼겹 다 먹어봤는데 생삼겹이 제일 맛있기는 함. 근데 가게가 오래되서 깨끗한거 좋아하고 화장실, 매장 시설 중요하게 생각하는 사람은 별로일수도 있음.",
            ),
            Ut2Review(
                "kushinoa",
                PERSONA_JAMSIL,
                listOf(4),
                5,
                listOf("tag_friend"),
                listOf("tag_tasty"),
                "g5-kushinoa",
                3,
                "가을 - 겨울 ... 단새우 & 우니\n철 가시기전에 지금 가야함. 그리고 오뎅바 싫어하는사람? 여기 가야함. 여기 안가봐서 오뎅바 싫어하는 거임 ㄹㅇ",
                pros = "제철 단새우와 우니가 훌륭해요",
                cons = "제철이 지나면 아쉬울 수 있어요",
            ),
            Ut2Review(
                "sodam",
                PERSONA_EXPLORER,
                listOf(4),
                5,
                listOf("tag_alone"),
                listOf("tag_tasty"),
                "g5-sodam",
                3,
                "평냉 덕후들 모여라. 4계절 내내 평냉 먹을 수 있는, 메밀면을 가게에서 뽑는 자가제면 평냉집 딱 알려준다. 진짜 여기 안가봤으면 평냉 먹었다고 할 수 없음. 여기 그냥 '메밀면' 자체가 맛있어서 국물? 양념 없이 진짜 순수 면만 먹어도 고소함..",
            ),
            Ut2Review(
                "byeolmi",
                PERSONA_JAMSIL,
                listOf(5),
                4,
                listOf("tag_friend"),
                listOf("tag_tasty"),
                "g6-byeolmi",
                2,
                "잠실에서 제일 유명한 곱창집. 유명하면 이유가 있음. 내 최애 곱창 맛집임ㅠㅠ 근데 사람이 좀 많은 편. 곱창은 맛있긴 하지만 서비스가 좀 아쉽긴함.",
            ),
            Ut2Review(
                "geumdon",
                PERSONA_JAMSIL,
                listOf(5),
                4,
                listOf("tag_family"),
                listOf("tag_tasty", "tag_kind"),
                "g6-geumdon",
                3,
                "맛있는거 먹고싶을 때 맨날 가는 맛집. 생갈비랑 양념갈비가 맛있음. 전담 매니저가 직접 구워줘서 편하게 먹을 수 있음. 근데 가격이 좀 사악하긴 함…. 그리고 대기가 길어서 갈거면 미리 테이블링 해야 갈 수 있음.",
                pros = "전담 매니저가 구워줘서 편해요",
                cons = "가격이 높고 대기가 길어요",
            ),
            Ut2Review(
                "thebitnam",
                PERSONA_OFFICE,
                listOf(5),
                5,
                listOf("tag_friend"),
                listOf("tag_kind", "tag_tasty"),
                "g6-thebitnam",
                2,
                "진짜 너무 친절하셔서 또 가고싶음… 첨 갔을 때 사람들 앞에 있길래 무슨 웨이팅을 하나 싶었는데 양도 많고 너무 맛있었음… 너무 웨이팅이 심하긴 하지만… 윤남노랑 풍자가 극찬한 이유를 알겠음. 처음에 쌀국수 별로 안좋아했는데 이 매장 알고나서부터 쌀국수 찾아먹는 사람 됨. 고수도 추가할 수 있고 도가니가 들어간 쌀국수 강추! 몰랐는데 캐치테이블 예약도 가능한거 같음. 우리집 경기도인데 이거 먹으러 잠실 놀러 쌉가능",
            ),
            Ut2Review(
                "hikiniku",
                PERSONA_JAMSIL,
                listOf(6),
                5,
                listOf("tag_colleague"),
                listOf("tag_tasty"),
                "g7-hikiniku",
                3,
                "회사 동료에게 추천을 받아 방문한 전설의 후쿠오카 함바그 맛집. 진짜 일본이 따로 없는 맛 ,,, 매일매일 다른 쌀로 ... 짓는 솥밥과 .. 첫번째 함바그입니다 ~ 하면서 주는 고기의 조화가 미친 ..집 .. 꿀팁은 꼭꼭 처음부터 웨이팅있으니 캐치테이블 예약하시고 맥주는 꼭꼭 드세요 (positive) 저 원래 맥주 못먹는데 흡입함",
                pros = "솥밥과 함바그 조합이 훌륭해요",
                cons = "웨이팅이 있어 예약이 필요해요",
            ),
            Ut2Review(
                "mur",
                PERSONA_EXPLORER,
                listOf(6),
                4,
                listOf("tag_friend"),
                listOf("tag_value", "tag_mood"),
                "g7-mur",
                3,
                "역삼역 근처에서 분위기 좋고 가성비 좋은 이자카야를 찾는다면 추천드려요:) 안주 종류가 다양하고 가격도 합리적이라 역삼에서 약속이 있을 때마다 자주 방문하는 곳이에요. 다만 안주가 저렴한 대신 소주는 판매하지 않고 매장이 크지 않아 인기 많은 시간대에는 자리가 없을 수도 있으니 참고하세요!",
            ),
            Ut2Review(
                "younghyang",
                PERSONA_OFFICE,
                listOf(2),
                4,
                listOf("tag_colleague"),
                listOf("tag_tasty"),
                "g3-younghyang",
                1,
                "점심때 회사 사람들이랑 파스타 먹고싶으면 가끔 가는곳. 구디에는 파스타집이 몇개 없어서 너무 귀한 곳이에요. 제발 없어지지마… 금액은 한 12000원대에서 15000원 사이였던 것 같고 피자같은 메뉴가 없긴함. 그래도 명량크림파스타랑, 명란오일파스타? 존맛! 근데 메뉴가 좀 늦게 나오는것 같음(내 체감인가…?) 그리고 메뉴 그릇이 큰데 테이블이 작아서 여러개 메뉴 시키면 좀 불편하긴 해요ㅠㅠ 그리고 거리도 좀 있음. 그치만 점심에 가면 아이스아메리카노 공짜로 먹을 수 있어요ㅎㅎ",
                pros = "구디에 몇 없는 파스타집이고 점심엔 커피가 무료예요",
                cons = "메뉴가 늦게 나오고 테이블이 좁아요",
            ),
            Ut2Review(
                "sotnaeum",
                PERSONA_OFFICE,
                listOf(7),
                5,
                listOf("tag_colleague"),
                listOf("tag_tasty"),
                "g8-sotnaeum",
                2,
                "지금은 이직했는데 이직하기 전 마지막 점심으로 갔던 곳… 이제 못가게 되서 너무 아쉬워요. 이직해서 제일 아쉬운건 여기 가끔 생각나는데 못가는것 하나… 솥밥 메뉴도 종류별로 있고 마지막에 누룽지까지 만들어 먹을 수 있어서 좋아요! 스테이크 솥밥, 문어 솥밥 추천합니당. 평소에 웨이팅이 살짝 있는 편이라서 방문할거면 점심때 살짝 일찍 나오세요! 매장도 협소해서 4명이상 방문하면 불편할 수도 있음.",
                pros = "솥밥 종류가 다양하고 누룽지까지 즐길 수 있어요",
                cons = "웨이팅이 있고 매장이 협소해요",
            ),
            Ut2Review(
                "menshokatsu",
                PERSONA_EXPLORER,
                listOf(7),
                5,
                listOf("tag_alone"),
                listOf("tag_tasty", "tag_kind"),
                "g8-menshokatsu",
                2,
                "대존맛;; 동네 주민이라 여기 근처에 안 먹어본 가게가 없는데 오늘 첨 밥먹고 너무 당황했어요. 너무 맛있어서ㅋㅋㅋ! 먹는 법도 친절하게 설명해주셔서 좋았고 돈카츠 염지도 호감… 파김치도 호감… 돈카츠랑 우동 각각 어울리는 국 따로 나온 디테일 미쳤음… 등심카츠는 비계 부위가 다소 있는 편이라, 담백한 걸 선호하면 안심카츠 쪽이 더 나을 수 있음. 점심시간때 방문하면 약간 늦게 나올수도 있어요!",
                pros = "돈카츠 염지와 곁들임 구성이 훌륭하고 응대가 친절해요",
                cons = "점심시간에는 음식이 늦게 나올 수 있어요",
            ),
            Ut2Review(
                "sokcho",
                PERSONA_OFFICE,
                listOf(7),
                4,
                listOf("tag_family"),
                listOf("tag_value"),
                "g8-sokcho",
                1,
                "가족이 먹어보고 맛집이라 추천해주었고, 점심특선 시래기고등어조림, 삼치구이정식 시켰고 잘먹는 성인 2명인데 양이 아주 넉넉해서 배 터지는줄요! 근데 매장 외관이 좀 낡고 오래되서 좀 더려움. 주방 위생도 좋아보이지 않아서 위생 중요하게 생각하는 사람한테는 비추. 가끔 집에서 생선 먹기 부담스러우면 방문하는거 나쁘지 않음!",
            ),
        )

    private val GROUP_CREATED_AT = Instant.parse("2026-08-18T03:00:00Z")
    private val REVIEWED_AT = Instant.parse("2026-08-19T10:00:00Z")

    fun apply(
        placeStore: InMemoryStore<MockPlace>,
        saveStore: InMemoryStore<MockSave>,
        assetStore: InMemoryStore<MockAsset>,
        groupStore: InMemoryStore<MockGroup>,
        membershipStore: MockMembershipStore,
        shareStore: MockReviewShareStore,
        aiSummaryStore: MockAiSummaryStore,
        reviewIdGenerator: MockReviewIdGenerator,
    ) {
        val placeIdByKey =
            PLACES.associate { p ->
                val created =
                    placeStore.create { id ->
                        MockPlace(id, p.name, p.roadAddress, p.regionName, p.categoryName, p.latitude, p.longitude)
                    }
                p.key to created.placeId
            } +
                (
                    "hanpan" to
                        requireNotNull(placeStore.findAll().find { it.name == "한판승부" }) { "기존 시드의 한판승부가 없다" }.placeId
                )

        val groups =
            GROUPS.mapIndexed { index, g ->
                val createdAt = GROUP_CREATED_AT.plusSeconds(index * 3600L)
                val group =
                    groupStore.create { id ->
                        MockGroup(
                            groupId = id,
                            name = g.name,
                            oneLineDescription = g.oneLine,
                            description = null,
                            imageAssetId = null,
                            foodCategoryId = g.foodCategoryId,
                            regionTagIds = listOf(g.regionTagId),
                            ownerId = g.ownerId,
                            createdAt = createdAt,
                        )
                    }
                // 생성자 자동 가입 + persona 전원이 멤버 (그룹 상세 멤버 목록이 1명이면 어색하다)
                PERSONAS.forEach { membershipStore.join(group.groupId, it, createdAt.plusSeconds(60)) }
                group
            }

        UT_USER_JOINS.forEach { (userId, indexes) ->
            indexes.forEach { index ->
                membershipStore.join(groups[index].groupId, userId, GROUP_CREATED_AT.plusSeconds(86400L + index * 60L))
            }
        }

        REVIEWS.forEachIndexed { index, review ->
            val assetIds =
                (1..review.photoCount).map { order ->
                    val asset =
                        assetStore.create { id ->
                            MockAsset(
                                assetId = id,
                                ownerId = review.authorId,
                                contentType = "image/jpeg",
                                attached = true,
                            )
                        }
                    MockSeedMediaUrls.register(asset.assetId, "$PHOTO_BASE/${review.photoDir}/0$order.jpg")
                    asset.assetId
                }
            val reviewId = reviewIdGenerator.next()
            val at = REVIEWED_AT.plusSeconds(index * 7200L)
            saveStore.create { id ->
                MockSave(
                    saveId = id,
                    ownerId = review.authorId,
                    placeId = placeIdByKey.getValue(review.placeKey),
                    photoAssetIds = assetIds,
                    companionTagIds = review.companionTagIds,
                    positivePointTagIds = review.positivePointTagIds,
                    rating = review.rating,
                    content = review.content,
                    reviewId = reviewId,
                    createdAt = at,
                    updatedAt = at,
                )
            }
            if (review.pros != null || review.cons != null) {
                aiSummaryStore.put(reviewId, pros = review.pros, cons = review.cons)
            }
            review.groupIndexes.forEach { shareStore.add(groups[it].groupId, review.authorId, reviewId) }
        }
    }
}
