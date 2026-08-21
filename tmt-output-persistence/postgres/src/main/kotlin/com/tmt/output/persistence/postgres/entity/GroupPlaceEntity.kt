package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable

@Embeddable
data class GroupPlaceId(
    @Column(nullable = false)
    val groupId: Long,
    @Column(nullable = false)
    val placeId: Long,
) : Serializable

/** 공유 리뷰에서 파생되는 집계다. 0이 되면 행을 지워 place_count가 행 수와 같아진다. */
@Entity
@Table(name = "group_place")
class GroupPlaceEntity(
    @EmbeddedId
    val id: GroupPlaceId,
) {
    @Column(nullable = false)
    var sharedReviewCount: Int = 0
        protected set
}
