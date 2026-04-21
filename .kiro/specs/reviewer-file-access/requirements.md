# Requirements Document

## Introduction

This feature addresses a critical permission issue where Level 1 (L1) and Level 2 (L2) reviewers cannot access file attachments for award records they are assigned to review. Currently, the file preview/download endpoints only allow access to:
1. System administrators (SCHOOL_ADMIN, SYS_ADMIN)
2. The original file uploader

This prevents reviewers from performing their duties, as they cannot view supporting documentation (certificates, proof documents, etc.) attached to award records under review.

## Glossary

- **File_Controller**: The REST controller handling file upload, download, and preview operations
- **L1_Reviewer**: A user with role COMP_REVIEWER_L1 who performs first-level review of award records
- **L2_Reviewer**: A user with role COMP_REVIEWER_L2 who performs second-level review of award records
- **Award_Record**: A record in biz_award_record representing a competition award submission
- **File_Attachment**: A file linked to an award record through biz_award_record_file
- **Reviewer_Scope**: A record in biz_reviewer_comp_scope defining which competitions a reviewer can access
- **Review_Assignment**: The relationship between a reviewer and an award record (via l1_auditor_user_id or l2_reviewer_user_id)

## Requirements

### Requirement 1: L1 Reviewer File Access

**User Story:** As an L1 reviewer, I want to view file attachments for award records assigned to me, so that I can properly evaluate the submission.

#### Acceptance Criteria

1. WHEN an L1 reviewer requests preview or download of a file, AND the file is attached to an award record where the reviewer is the l1_auditor_user_id, THEN the File_Controller SHALL grant access
2. WHEN an L1 reviewer requests preview or download of a file, AND the file is attached to an award record in a competition within their reviewer scope, AND the record status is PENDING_SCHOOL or later, THEN the File_Controller SHALL grant access
3. WHEN an L1 reviewer requests preview or download of a file, AND the file is NOT attached to any award record they can review, THEN the File_Controller SHALL deny access with 403 error

### Requirement 2: L2 Reviewer File Access

**User Story:** As an L2 reviewer, I want to view file attachments for award records assigned to me, so that I can perform second-level review.

#### Acceptance Criteria

1. WHEN an L2 reviewer requests preview or download of a file, AND the file is attached to an award record where the reviewer is the l2_reviewer_user_id, THEN the File_Controller SHALL grant access
2. WHEN an L2 reviewer requests preview or download of a file, AND the file is attached to an award record in a competition within their reviewer scope, AND the record has l2_review_flag = 1, THEN the File_Controller SHALL grant access
3. WHEN an L2 reviewer requests preview or download of a file, AND the file is NOT attached to any award record they can review, THEN the File_Controller SHALL deny access with 403 error

### Requirement 3: Maintain Existing Access Controls

**User Story:** As a system administrator, I want existing file access controls to remain intact, so that security is not compromised.

#### Acceptance Criteria

1. WHEN a SCHOOL_ADMIN or SYS_ADMIN requests any file, THEN the File_Controller SHALL grant access (existing behavior)
2. WHEN a file uploader requests their own uploaded file, THEN the File_Controller SHALL grant access (existing behavior)
3. WHEN a user without any valid permission requests a file, THEN the File_Controller SHALL deny access with 403 error

### Requirement 4: Reviewer Scope Validation

**User Story:** As a system administrator, I want reviewers to only access files for competitions within their assigned scope, so that access control boundaries are respected.

#### Acceptance Criteria

1. WHEN checking reviewer access to a file, THE File_Controller SHALL verify the reviewer has an enabled scope record (biz_reviewer_comp_scope.enabled = 1) for the competition
2. WHEN a reviewer scope has validFrom and validTo dates, THE File_Controller SHALL verify the current date falls within the valid period
3. WHEN a reviewer has no enabled scope for a competition, THEN the File_Controller SHALL deny access to files from award records in that competition

### Requirement 5: Performance Optimization

**User Story:** As a developer, I want file permission checks to be efficient, so that file preview/download operations remain fast.

#### Acceptance Criteria

1. WHEN checking file permissions, THE File_Controller SHALL minimize database queries by using efficient joins or batch queries
2. WHEN a file is not attached to any award record, THE File_Controller SHALL short-circuit reviewer permission checks
3. THE File_Controller SHALL complete permission checks within 100ms for typical cases
