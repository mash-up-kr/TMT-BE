package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class UserEntity(
    @Column(nullable = false, unique = true)
    val kakaoId: Long,
    /** 2~20자, 중복 허용 (U3). 폭·CHECK의 정본은 V6 마이그레이션이다 */
    @Column(length = 20, nullable = false)
    var nickname: String,
    /** 카카오 값 자리 — 신규 쓰기는 하지 않는다 (TMT-370). 조회는 [profileImageAssetId]를 우선한다 */
    @Column(columnDefinition = "text")
    var profileImageUrl: String? = null,
    /** 프로필 사진의 정본. 업로드 경로는 그룹 대표 이미지와 같다 (M7) */
    var profileImageAssetId: Long? = null,
    /** 가입 화면을 끝낸 시각. null이면 미완료다 */
    var profileCompletedAt: Instant? = null,
    /** 이 시각 전에 발급된 refresh는 무효 — 로그아웃이 찍는다 (TMT-353). null이면 로그아웃한 적이 없다 */
    var tokensInvalidBefore: Instant? = null,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
