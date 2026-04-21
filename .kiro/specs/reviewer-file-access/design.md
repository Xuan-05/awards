# Design Document: Reviewer File Access

## Overview

This design extends the file access control logic in FileController to allow L1 and L2 reviewers to access file attachments for award records they are authorized to review. The solution introduces a new permission checking service that validates reviewer access based on their role, assigned records, and competition scope.

## Architecture

The solution follows a layered architecture:

1. **Controller Layer** (FileController): Handles HTTP requests and delegates permission checks
2. **Service Layer** (FilePermissionService): Encapsulates file access permission logic
3. **Data Layer** (Mappers): Queries reviewer scopes and award record relationships

The design maintains backward compatibility with existing permission checks while adding reviewer-specific logic.

## Components and Interfaces

### FilePermissionService

A new service that centralizes file permission checking logic.

```java
public interface FilePermissionService {
    /**
     * Check if a user can access a file
     * @param userId The user requesting access
     * @param fileId The file being accessed
     * @return true if access is granted, false otherwise
     */
    boolean canAccessFile(Long userId, Long fileId);
    
    /**
     * Check if a reviewer can access files for a specific award record
     * @param reviewerUserId The reviewer user ID
     * @param recordId The award record ID
     * @return true if the reviewer can access the record's files
     */
    boolean canReviewerAccessRecord(Long reviewerUserId, Long recordId);
}
```

### FilePermissionServiceImpl

Implementation with the following logic flow:

```
Pseudocode:

function canAccessFile(userId, fileId):
    // 1. Check admin roles (existing logic)
    if user has role SCHOOL_ADMIN or SYS_ADMIN:
        return true
    
    // 2. Get file metadata
    file = getFileById(fileId)
    if file is null or deleted:
        return false
    
    // 3. Check if user is uploader (existing logic)
    if file.uploaderUserId == userId:
        return true
    
    // 4. Check reviewer access (NEW)
    if user has role COMP_REVIEWER_L1 or COMP_REVIEWER_L2:
        return canReviewerAccessFile(userId, fileId)
    
    return false

function canReviewerAccessFile(reviewerUserId, fileId):
    // Find award records linked to this file
    recordFiles = query biz_award_record_file where fileId = fileId and deleted = 0
    
    if recordFiles is empty:
        return false
    
    for each recordFile in recordFiles:
        if canReviewerAccessRecord(reviewerUserId, recordFile.recordId):
            return true
    
    return false

function canReviewerAccessRecord(reviewerUserId, recordId):
    // Get award record
    record = query biz_award_record where id = recordId
    if record is null or deleted:
        return false
    
    // Get user roles
    roles = getRoleCodes(reviewerUserId)
    
    // Check L1 reviewer access
    if roles contains COMP_REVIEWER_L1:
        // Direct assignment
        if record.l1AuditorUserId == reviewerUserId:
            return true
        
        // Scope-based access (for records in PENDING_SCHOOL or later)
        if record.status in [PENDING_SCHOOL, SCHOOL_REJECTED, APPROVED, ARCHIVED]:
            if hasValidScope(reviewerUserId, record.competitionId):
                return true
    
    // Check L2 reviewer access
    if roles contains COMP_REVIEWER_L2:
        // Direct assignment
        if record.l2ReviewerUserId == reviewerUserId:
            return true
        
        // Scope-based access (for records flagged for L2 review)
        if record.l2ReviewFlag == 1:
            if hasValidScope(reviewerUserId, record.competitionId):
                return true
    
    return false

function hasValidScope(reviewerUserId, competitionId):
    scope = query biz_reviewer_comp_scope where:
        reviewerUserId = reviewerUserId
        and competitionId = competitionId
        and enabled = 1
    
    if scope is null:
        return false
    
    // Check date validity if dates are set
    now = current date/time
    if scope.validFrom is not null and now < scope.validFrom:
        return false
    if scope.validTo is not null and now > scope.validTo:
        return false
    
    return true
```

### Modified FileController

Update the `preview()` and `download()` methods to use FilePermissionService:

```java
@GetMapping("/{fileId}/preview")
public ResponseEntity<FileSystemResource> preview(
        @PathVariable Long fileId,
        @RequestParam(value = "Authorization", required = false) String token) {
    Long uid = getCurrentUserId(token);
    SysFile meta = sysFileService.getById(fileId);
    
    if (meta == null || meta.getDeleted() != null && meta.getDeleted() == 1)
        throw new BizException(404, "文件不存在");
    
    // Use new permission service
    if (!filePermissionService.canAccessFile(uid, fileId))
        throw new BizException(403, "无权限");
    
    if ("download_only".equals(meta.getPreviewType()))
        throw new BizException(400, "仅支持下载");
    
    // ... rest of method
}
```

## Data Models

### Existing Tables Used

**biz_award_record_file**: Links files to award records
- fileId: Foreign key to sys_file
- recordId: Foreign key to biz_award_record
- deleted: Soft delete flag

**biz_award_record**: Award submission records
- id: Primary key
- competitionId: Foreign key to dict_competition
- l1AuditorUserId: L1 reviewer assignment
- l2ReviewerUserId: L2 reviewer assignment
- l2ReviewFlag: Flag indicating L2 review required
- status: Record status (DRAFT, PENDING_SCHOOL, etc.)
- deleted: Soft delete flag

**biz_reviewer_comp_scope**: Reviewer competition scopes
- reviewerUserId: Foreign key to sys_user
- competitionId: Foreign key to dict_competition
- enabled: Whether scope is active
- validFrom: Optional start date
- validTo: Optional end date

**sys_user_role**: User role assignments
- userId: Foreign key to sys_user
- roleCode: Role identifier (COMP_REVIEWER_L1, COMP_REVIEWER_L2, etc.)

## Correctness Properties

A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.

### Property 1: Admin access is unrestricted
*For any* file and any user with role SCHOOL_ADMIN or SYS_ADMIN, the permission check should return true
**Validates: Requirements 3.1**

### Property 2: Uploader access is preserved
*For any* file, the user who uploaded it (matching uploaderUserId) should have access granted
**Validates: Requirements 3.2**

### Property 3: L1 reviewer direct assignment access
*For any* award record with l1AuditorUserId set, the assigned L1 reviewer should have access to all files attached to that record
**Validates: Requirements 1.1**

### Property 4: L2 reviewer direct assignment access
*For any* award record with l2ReviewerUserId set, the assigned L2 reviewer should have access to all files attached to that record
**Validates: Requirements 2.1**

### Property 5: Scope-based access requires enabled scope
*For any* reviewer attempting scope-based access, if they do not have an enabled scope record for the competition, access should be denied
**Validates: Requirements 4.1**

### Property 6: Date-bounded scopes are enforced
*For any* reviewer scope with validFrom or validTo dates, access should only be granted when the current date falls within the valid period
**Validates: Requirements 4.2**

### Property 7: Unauthorized users are denied
*For any* user without admin role, uploader status, or valid reviewer assignment, access to a file should be denied
**Validates: Requirements 1.3, 2.3, 3.3**

### Property 8: L1 scope access requires appropriate status
*For any* L1 reviewer with valid scope, they should only access files for records with status PENDING_SCHOOL or later
**Validates: Requirements 1.2**

### Property 9: L2 scope access requires review flag
*For any* L2 reviewer with valid scope, they should only access files for records with l2ReviewFlag = 1
**Validates: Requirements 2.2**

## Error Handling

### Error Scenarios

1. **File Not Found (404)**
   - File ID does not exist
   - File is soft-deleted
   - Response: `{"code": 404, "message": "文件不存在"}`

2. **Access Denied (403)**
   - User is not authenticated
   - User lacks required permissions
   - Reviewer scope is invalid or expired
   - Response: `{"code": 403, "message": "无权限"}`

3. **Preview Not Supported (400)**
   - File previewType is "download_only"
   - Response: `{"code": 400, "message": "仅支持下载"}`

### Logging

Log permission denials for security auditing:
```java
if (!filePermissionService.canAccessFile(uid, fileId)) {
    log.warn("File access denied: userId={}, fileId={}, reason=insufficient_permissions", 
             uid, fileId);
    throw new BizException(403, "无权限");
}
```

## Testing Strategy

### Unit Tests

Unit tests will verify specific examples and edge cases:

1. **Admin Access Tests**
   - Test SCHOOL_ADMIN can access any file
   - Test SYS_ADMIN can access any file

2. **Uploader Access Tests**
   - Test uploader can access their own files
   - Test non-uploader cannot access others' files

3. **L1 Reviewer Tests**
   - Test L1 reviewer with direct assignment can access files
   - Test L1 reviewer with scope can access PENDING_SCHOOL records
   - Test L1 reviewer cannot access DRAFT records via scope
   - Test L1 reviewer without scope cannot access files

4. **L2 Reviewer Tests**
   - Test L2 reviewer with direct assignment can access files
   - Test L2 reviewer with scope can access flagged records
   - Test L2 reviewer cannot access non-flagged records via scope

5. **Scope Validation Tests**
   - Test disabled scope denies access
   - Test expired scope (past validTo) denies access
   - Test future scope (before validFrom) denies access
   - Test valid scope within date range grants access

6. **Edge Cases**
   - Test file with no award record associations
   - Test deleted award records
   - Test reviewer with multiple roles
   - Test null date fields in scope

### Property-Based Tests

Property tests will verify universal correctness across randomized inputs. Each test should run a minimum of 100 iterations.

Tests will use JUnit 5 with jqwik for property-based testing in Java.

**Test Configuration:**
- Framework: jqwik (Java property-based testing library)
- Minimum iterations: 100 per property
- Tag format: `@Tag("Feature: reviewer-file-access, Property N: [property text]")`

**Property Test Examples:**

1. **Property 1: Admin access is unrestricted**
   - Generate: random fileId, random userId with SCHOOL_ADMIN or SYS_ADMIN role
   - Assert: canAccessFile returns true

2. **Property 3: L1 reviewer direct assignment access**
   - Generate: random award record with l1AuditorUserId, random file attached to record
   - Assert: canAccessFile returns true for the assigned reviewer

3. **Property 5: Scope-based access requires enabled scope**
   - Generate: random reviewer, random competition, scope with enabled=0
   - Assert: canReviewerAccessRecord returns false

### Integration Tests

Integration tests will verify end-to-end flows:

1. Test complete preview flow for L1 reviewer
2. Test complete download flow for L2 reviewer
3. Test permission denial returns proper HTTP status codes
4. Test token-based authentication in URL parameters

### Performance Tests

Verify requirement 5.3 (permission checks within 100ms):

1. Measure permission check latency for typical scenarios
2. Test with multiple file-record associations
3. Test with multiple reviewer scopes
4. Identify and optimize slow queries if needed
