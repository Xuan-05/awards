# Implementation Plan: Reviewer File Access

## Overview

This implementation plan breaks down the reviewer file access feature into discrete coding tasks. The approach is incremental: first create the permission service interface and implementation, then integrate it into the file controller, and finally add comprehensive tests.

## Tasks

- [ ] 1. Create FilePermissionService interface and implementation
  - Create FilePermissionService interface with canAccessFile and canReviewerAccessRecord methods
  - Create FilePermissionServiceImpl with core permission checking logic
  - Implement admin and uploader permission checks (existing logic)
  - Add dependency injection for required mappers (BizAwardRecordFileMapper, BizAwardRecordMapper, BizReviewerCompScopeMapper, RbacService)
  - _Requirements: 3.1, 3.2_

- [ ]* 1.1 Write unit tests for admin and uploader access
  - Test SCHOOL_ADMIN can access any file
  - Test SYS_ADMIN can access any file
  - Test uploader can access their own files
  - Test non-uploader cannot access others' files
  - _Requirements: 3.1, 3.2_

- [ ] 2. Implement reviewer scope validation logic
  - [ ] 2.1 Implement hasValidScope method in FilePermissionServiceImpl
    - Query biz_reviewer_comp_scope for enabled scopes
    - Validate validFrom and validTo date ranges
    - _Requirements: 4.1, 4.2_

  - [ ]* 2.2 Write property test for scope validation
    - **Property 5: Scope-based access requires enabled scope**
    - **Validates: Requirements 4.1**

  - [ ]* 2.3 Write property test for date-bounded scopes
    - **Property 6: Date-bounded scopes are enforced**
    - **Validates: Requirements 4.2**

  - [ ]* 2.4 Write unit tests for scope edge cases
    - Test disabled scope denies access
    - Test expired scope denies access
    - Test future scope denies access
    - Test null date fields allow access
    - _Requirements: 4.1, 4.2_

- [ ] 3. Implement L1 reviewer access logic
  - [ ] 3.1 Implement canReviewerAccessRecord for L1 reviewers
    - Check direct assignment via l1AuditorUserId
    - Check scope-based access for PENDING_SCHOOL or later statuses
    - _Requirements: 1.1, 1.2_

  - [ ]* 3.2 Write property test for L1 direct assignment
    - **Property 3: L1 reviewer direct assignment access**
    - **Validates: Requirements 1.1**

  - [ ]* 3.3 Write property test for L1 scope-based access
    - **Property 8: L1 scope access requires appropriate status**
    - **Validates: Requirements 1.2**

  - [ ]* 3.4 Write unit tests for L1 reviewer scenarios
    - Test L1 reviewer with direct assignment can access files
    - Test L1 reviewer with scope can access PENDING_SCHOOL records
    - Test L1 reviewer cannot access DRAFT records via scope
    - Test L1 reviewer without scope cannot access files
    - _Requirements: 1.1, 1.2, 1.3_

- [ ] 4. Implement L2 reviewer access logic
  - [ ] 4.1 Implement canReviewerAccessRecord for L2 reviewers
    - Check direct assignment via l2ReviewerUserId
    - Check scope-based access for records with l2ReviewFlag = 1
    - _Requirements: 2.1, 2.2_

  - [ ]* 4.2 Write property test for L2 direct assignment
    - **Property 4: L2 reviewer direct assignment access**
    - **Validates: Requirements 2.1**

  - [ ]* 4.3 Write property test for L2 scope-based access
    - **Property 9: L2 scope access requires review flag**
    - **Validates: Requirements 2.2**

  - [ ]* 4.4 Write unit tests for L2 reviewer scenarios
    - Test L2 reviewer with direct assignment can access files
    - Test L2 reviewer with scope can access flagged records
    - Test L2 reviewer cannot access non-flagged records via scope
    - _Requirements: 2.1, 2.2, 2.3_

- [ ] 5. Implement canReviewerAccessFile method
  - [ ] 5.1 Query biz_award_record_file to find records linked to file
    - Handle case where file has no award record associations
    - Iterate through record associations and check reviewer access
    - _Requirements: 1.1, 1.2, 2.1, 2.2_

  - [ ]* 5.2 Write unit tests for file-record associations
    - Test file with no award record associations denies access
    - Test file with multiple record associations grants access if any match
    - Test deleted award records are ignored
    - _Requirements: 1.3, 2.3_

- [ ] 6. Integrate FilePermissionService into FileController
  - [ ] 6.1 Add FilePermissionService dependency to FileController
    - Inject FilePermissionService via constructor
    - _Requirements: 3.1, 3.2, 3.3_

  - [ ] 6.2 Update preview() method to use FilePermissionService
    - Replace existing permission check with filePermissionService.canAccessFile()
    - Maintain existing error handling and response codes
    - Add security logging for denied access
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3_

  - [ ] 6.3 Update download() method to use FilePermissionService
    - Replace existing permission check with filePermissionService.canAccessFile()
    - Maintain existing error handling and response codes
    - Add security logging for denied access
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3_

  - [ ]* 6.4 Write integration tests for preview endpoint
    - Test L1 reviewer can preview files for assigned records
    - Test L2 reviewer can preview files for assigned records
    - Test unauthorized user receives 403 error
    - Test token-based authentication works
    - _Requirements: 1.1, 2.1, 3.3_

  - [ ]* 6.5 Write integration tests for download endpoint
    - Test L1 reviewer can download files for assigned records
    - Test L2 reviewer can download files for assigned records
    - Test unauthorized user receives 403 error
    - _Requirements: 1.1, 2.1, 3.3_

- [ ] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ]* 8. Performance validation
  - [ ]* 8.1 Measure permission check latency
    - Test typical scenarios complete within 100ms
    - Identify slow queries if any
    - _Requirements: 5.3_

  - [ ]* 8.2 Optimize queries if needed
    - Add database indexes if permission checks are slow
    - Consider caching reviewer scopes if appropriate
    - _Requirements: 5.1, 5.2, 5.3_

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- The checkpoint ensures incremental validation
- Property tests validate universal correctness properties with minimum 100 iterations
- Unit tests validate specific examples and edge cases
- Integration tests verify end-to-end flows through the HTTP endpoints
