package com.tmt.application.domain.place

/** 직접 등록 매장의 서버 제약 (F §4-1·§7). 값의 정본은 `place` 테이블의 컬럼 폭이다. */
object PlaceRules {
    const val NAME_MAX_LENGTH = 100

    /** 시군구명 + " " + 읍면동명. 조립 결과가 넘으면 INSERT가 깨지므로 미리 끊는다 (F §7). */
    const val REGION_NAME_MAX_LENGTH = 50

    const val ROAD_ADDRESS_MAX_LENGTH = 200
}
