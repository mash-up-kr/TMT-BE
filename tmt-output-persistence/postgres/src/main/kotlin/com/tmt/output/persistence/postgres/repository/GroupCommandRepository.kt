package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GroupCommandRepository : JpaRepository<GroupEntity, Long>
