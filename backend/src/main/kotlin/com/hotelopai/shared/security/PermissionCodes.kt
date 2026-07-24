package com.hotelopai.shared.security

object PermissionCodes {
    const val AUTH_LOGIN = "AUTH_LOGIN"
    const val AUTH_MANAGE = "AUTH_MANAGE"
    const val AUTH_VIEW = "AUTH_VIEW"

    const val TASK_READ = "TASK_READ"
    const val TASK_CREATE = "TASK_CREATE"
    const val TASK_ASSIGN = "TASK_ASSIGN"
    const val TASK_START = "TASK_START"
    const val TASK_PAUSE = "TASK_PAUSE"
    const val TASK_RESUME = "TASK_RESUME"
    const val TASK_COMPLETE = "TASK_COMPLETE"
    const val TASK_CANCEL = "TASK_CANCEL"
    const val TASK_MARK_OVERDUE = "TASK_MARK_OVERDUE"
    const val TASK_ATTACHMENT_READ = "TASK_ATTACHMENT_READ"

    const val ASSISTANT_USE = "ASSISTANT_USE"
    const val ASSISTANT_CONFIRM_TASK = "ASSISTANT_CONFIRM_TASK"
    const val ASSISTANT_ATTACHMENT_REGISTER = "ASSISTANT_ATTACHMENT_REGISTER"
    const val ASSISTANT_VISION_IMPORT = "ASSISTANT_VISION_IMPORT"

    const val NOTIFICATION_READ = "NOTIFICATION_READ"
    const val NOTIFICATION_MARK_READ = "NOTIFICATION_MARK_READ"

    const val DASHBOARD_READ = "DASHBOARD_READ"
    const val REPORT_READ = "REPORT_READ"

    const val DEV_PMS_ACCESS = "DEV_PMS_ACCESS"
    const val PMS_OPERATIONS_ACCESS = "PMS_OPERATIONS_ACCESS"
    const val RESERVATION_SYNC_OPERATIONS = "RESERVATION_SYNC_OPERATIONS"
}

object PermissionExpressions {
    const val AUTH_VIEW = "@permissionGuard.hasAnyPermission('${PermissionCodes.AUTH_VIEW}')"

    const val TASK_READ = "@permissionGuard.hasAnyPermission('${PermissionCodes.TASK_READ}')"
    const val TASK_CREATE = "@permissionGuard.hasAnyPermission('${PermissionCodes.TASK_CREATE}')"
    const val TASK_ASSIGN = "@permissionGuard.hasAnyPermission('${PermissionCodes.TASK_ASSIGN}')"
    const val TASK_START = "@permissionGuard.hasAnyPermission('${PermissionCodes.TASK_START}')"
    const val TASK_PAUSE = "@permissionGuard.hasAnyPermission('${PermissionCodes.TASK_PAUSE}')"
    const val TASK_RESUME = "@permissionGuard.hasAnyPermission('${PermissionCodes.TASK_RESUME}')"
    const val TASK_COMPLETE = "@permissionGuard.hasAnyPermission('${PermissionCodes.TASK_COMPLETE}')"
    const val TASK_CANCEL = "@permissionGuard.hasAnyPermission('${PermissionCodes.TASK_CANCEL}')"
    const val TASK_MARK_OVERDUE = "@permissionGuard.hasAnyPermission('${PermissionCodes.TASK_MARK_OVERDUE}')"
    const val TASK_ATTACHMENT_READ = "@permissionGuard.hasAnyPermission('${PermissionCodes.TASK_ATTACHMENT_READ}')"

    const val ASSISTANT_USE = "@permissionGuard.hasAnyPermission('${PermissionCodes.ASSISTANT_USE}')"
    const val ASSISTANT_CONFIRM_TASK = "@permissionGuard.hasAnyPermission('${PermissionCodes.ASSISTANT_CONFIRM_TASK}')"
    const val ASSISTANT_ATTACHMENT_REGISTER = "@permissionGuard.hasAnyPermission('${PermissionCodes.ASSISTANT_ATTACHMENT_REGISTER}')"
    const val ASSISTANT_VISION_IMPORT = "@permissionGuard.hasAnyPermission('${PermissionCodes.ASSISTANT_VISION_IMPORT}')"

    const val NOTIFICATION_READ = "@permissionGuard.hasAnyPermission('${PermissionCodes.NOTIFICATION_READ}')"
    const val NOTIFICATION_MARK_READ = "@permissionGuard.hasAnyPermission('${PermissionCodes.NOTIFICATION_MARK_READ}')"

    const val DASHBOARD_READ = "@permissionGuard.hasAnyPermission('${PermissionCodes.DASHBOARD_READ}')"
    const val REPORT_READ = "@permissionGuard.hasAnyPermission('${PermissionCodes.REPORT_READ}')"

    const val DEV_PMS_ACCESS = "@permissionGuard.hasAnyPermission('${PermissionCodes.DEV_PMS_ACCESS}')"
    const val PMS_OPERATIONS_ACCESS = "@permissionGuard.hasAnyPermission('${PermissionCodes.PMS_OPERATIONS_ACCESS}')"
    const val RESERVATION_SYNC_OPERATIONS = "@permissionGuard.hasAnyPermission('${PermissionCodes.RESERVATION_SYNC_OPERATIONS}')"
}
