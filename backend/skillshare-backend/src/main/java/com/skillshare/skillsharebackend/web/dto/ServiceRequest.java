package com.skillshare.skillsharebackend.web.dto;

/** Body for {@code POST/PUT /api/admin/services} - create and update
 *  share the same shape since both write the same three editable
 *  columns (01_schema_v3.sql's `services` table has no status/active
 *  flag - see {@code AdminService}'s javadoc on why delete is a hard
 *  delete guarded by a reference check instead). */
public record ServiceRequest(String serviceName, String category, String description) {}
