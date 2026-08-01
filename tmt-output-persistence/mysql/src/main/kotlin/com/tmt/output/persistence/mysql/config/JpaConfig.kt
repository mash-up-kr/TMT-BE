package com.tmt.output.persistence.mysql.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = ["com.tmt.output.persistence.mysql.repository"])
@EntityScan(basePackages = ["com.tmt.output.persistence.mysql.entity"])
class JpaConfig
