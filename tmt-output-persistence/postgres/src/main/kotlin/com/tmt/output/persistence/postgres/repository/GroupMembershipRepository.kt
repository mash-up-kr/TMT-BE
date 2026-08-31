package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupMembershipEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GroupMembershipRepository : JpaRepository<GroupMembershipEntity, Long>
