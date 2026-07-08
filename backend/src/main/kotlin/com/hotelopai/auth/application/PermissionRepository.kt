package com.hotelopai.auth.application

import com.hotelopai.auth.domain.Permission
import java.util.UUID

interface PermissionRepository {
    fun save(permission: Permission): Permission

    fun findById(id: UUID): Permission?

    fun findByCode(code: String): Permission?

    fun findAll(): List<Permission>
}
