package com.university.awards.export.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.university.awards.common.BizException;
import com.university.awards.common.PageResult;
import com.university.awards.classdict.entity.DictClass;
import com.university.awards.classdict.mapper.DictClassMapper;
import com.university.awards.dept.entity.SysDept;
import com.university.awards.dept.mapper.SysDeptMapper;
import com.university.awards.dict.entity.DictAwardLevel;
import com.university.awards.dict.entity.DictAwardScope;
import com.university.awards.dict.entity.DictCompetition;
import com.university.awards.dict.entity.DictCompetitionCategory;
import com.university.awards.dict.mapper.DictAwardLevelMapper;
import com.university.awards.dict.mapper.DictAwardScopeMapper;
import com.university.awards.dict.mapper.DictCompetitionCategoryMapper;
import com.university.awards.dict.mapper.DictCompetitionMapper;
import com.university.awards.export.entity.BizExportTask;
import com.university.awards.export.mapper.BizExportTaskMapper;
import com.university.awards.export.service.ExportService;
import com.university.awards.export.vo.ExportReportOption;
import com.university.awards.rbac.entity.SysUser;
import com.university.awards.rbac.mapper.SysUserMapper;
import com.university.awards.rbac.service.AuthzService;
import com.university.awards.rbac.service.RbacService;
import com.university.awards.record.entity.BizAwardRecord;
import com.university.awards.record.mapper.BizAwardRecordMapper;
import com.university.awards.team.entity.BizTeamMember;
import com.university.awards.team.entity.BizTeamTeacher;
import com.university.awards.team.mapper.BizTeamMemberMapper;
import com.university.awards.team.mapper.BizTeamTeacherMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {
    private static final int DISCIPLINE_TEMPLATE_DATA_ROW_INDEX = 2; // Excel 第3行（0-based）
    private static final int DISCIPLINE_COL_COUNT = 17;
    private static final DateTimeFormatter EXPORT_FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private static final String REPORT_AWARD_GRADE_MATRIX = "AWARD_GRADE_MATRIX";
    private static final String REPORT_DISCIPLINE_STATS = "DISCIPLINE_STATS";

    private final BizExportTaskMapper taskMapper;
    private final BizAwardRecordMapper recordMapper;
    private final BizTeamMemberMapper teamMemberMapper;
    private final BizTeamTeacherMapper teamTeacherMapper;
    private final SysUserMapper userMapper;
    private final DictClassMapper classMapper;
    private final DictCompetitionMapper competitionMapper;
    private final DictCompetitionCategoryMapper categoryMapper;
    private final DictAwardScopeMapper awardScopeMapper;
    private final DictAwardLevelMapper awardLevelMapper;
    private final SysDeptMapper deptMapper;
    private final AuthzService authz;
    private final RbacService rbacService;

    @Value("${export.out-dir:./data/exports}")
    private String outDir;

    @Value("${export.template.discipline-stats:./templates/discipline_stats_template.xlsx}")
    private String disciplineStatsTemplate;

    @Override
    public Long createExport(String reportCode, String filterJson) {
        String normalized = normalizeReportCode(reportCode);
        return createAndRun(normalized, filterJson);
    }

    @Override
    public List<ExportReportOption> listReports() {
        return List.of(
                new ExportReportOption(REPORT_AWARD_GRADE_MATRIX, "竞赛获奖等级统计矩阵表", "按图示结构导出空模板（暂不填数）"),
                new ExportReportOption(REPORT_DISCIPLINE_STATS, "学科竞赛获奖信息统计表", "按参赛团队成员展开的统计导出")
        );
    }

    private Long createAndRun(String type, String filterJson) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();

        BizExportTask task = new BizExportTask();
        task.setExportType(type);
        task.setFilterJson(filterJson);
        task.setTaskStatus("RUNNING");
        task.setCreatorUserId(uid);
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        try {
            Path out = generate(type, task.getId());
            task.setFileName(buildDownloadFileName(type));
            task.setFilePath(out.toAbsolutePath().normalize().toString());
            task.setTaskStatus("SUCCESS");
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (Exception e) {
            task.setTaskStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            throw new BizException(500, "导出失败：" + e.getMessage());
        }

        return task.getId();
    }

    private List<BizAwardRecord> listApprovedRecordsByDataScope() {
        LambdaQueryWrapper<BizAwardRecord> qw = new LambdaQueryWrapper<BizAwardRecord>()
                .eq(BizAwardRecord::getDeleted, 0)
                .eq(BizAwardRecord::getStatus, "APPROVED");

        // Data scope:
        // - SCHOOL_ADMIN / SYS_ADMIN: all approved
        // - DEPT_ADMIN: only records in own dept (owner_dept_id)
        if (authz.hasRole("DEPT_ADMIN") && !authz.hasAnyRole("SCHOOL_ADMIN", "SYS_ADMIN")) {
            Long uid = authz.currentUserId();
            SysUser user = rbacService.mustGetUser(uid);
            if (user.getDeptId() == null) throw new BizException(400, "当前用户未绑定院系");
            qw.eq(BizAwardRecord::getOwnerDeptId, user.getDeptId());
        }
        return recordMapper.selectList(qw.orderByAsc(BizAwardRecord::getId).last("limit 5000"));
    }

    private Path generate(String type, Long taskId) throws Exception {
        Files.createDirectories(Path.of(outDir));
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path out = Path.of(outDir, type.toLowerCase() + "_" + taskId + "_" + ts + ".xlsx");

        if (REPORT_AWARD_GRADE_MATRIX.equals(type)) {
            writeAwardGradeMatrixTemplate(out, buildAwardGradeMatrixRows(listApprovedRecordsByDataScope()));
            return out;
        }

        String templatePath = templatePathByReport(type);
        File template = new File(templatePath);
        if (!template.exists()) throw new BizException(400, "缺少导出模板文件：" + template.getPath());
        writeDisciplineStatsByPoi(template.toPath(), out, buildDisciplineStatsGroups(listApprovedRecordsByDataScope()));
        return out;
    }

    private List<DisciplineStatsGroup> buildDisciplineStatsGroups(List<BizAwardRecord> records) {
        if (records.isEmpty()) return List.of();

        Set<Long> teamIds = records.stream().map(BizAwardRecord::getTeamId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> competitionIds = records.stream().map(BizAwardRecord::getCompetitionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> categoryIds = records.stream().map(BizAwardRecord::getCompetitionCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> scopeIds = records.stream().map(BizAwardRecord::getAwardScopeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> levelIds = records.stream().map(BizAwardRecord::getAwardLevelId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> deptIds = records.stream().map(BizAwardRecord::getOwnerDeptId).filter(Objects::nonNull).collect(Collectors.toSet());

        List<BizTeamMember> teamMembers = teamIds.isEmpty() ? List.of() : teamMemberMapper.selectList(
                new LambdaQueryWrapper<BizTeamMember>()
                        .in(BizTeamMember::getTeamId, teamIds)
                        .eq(BizTeamMember::getJoinStatus, "ACCEPTED")
        );
        List<BizTeamTeacher> teamTeachers = teamIds.isEmpty() ? List.of() : teamTeacherMapper.selectList(
                new LambdaQueryWrapper<BizTeamTeacher>()
                        .in(BizTeamTeacher::getTeamId, teamIds)
                        .eq(BizTeamTeacher::getJoinStatus, "ACCEPTED")
        );

        Set<Long> userIds = new LinkedHashSet<>();
        teamMembers.forEach(m -> userIds.add(m.getUserId()));
        teamTeachers.forEach(t -> userIds.add(t.getTeacherUserId()));

        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
        userMap.values().stream().map(SysUser::getDeptId).filter(Objects::nonNull).forEach(deptIds::add);
        Set<Long> classIds = userMap.values().stream().map(SysUser::getClassId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, DictClass> classMap = classIds.isEmpty() ? Map.of() : classMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(DictClass::getId, c -> c));

        Map<Long, DictCompetition> competitionMap = competitionIds.isEmpty() ? Map.of() : competitionMapper.selectBatchIds(competitionIds).stream()
                .collect(Collectors.toMap(DictCompetition::getId, c -> c));
        Map<Long, DictCompetitionCategory> categoryMap = categoryIds.isEmpty() ? Map.of() : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(DictCompetitionCategory::getId, c -> c));
        Map<Long, DictAwardScope> scopeMap = scopeIds.isEmpty() ? Map.of() : awardScopeMapper.selectBatchIds(scopeIds).stream()
                .collect(Collectors.toMap(DictAwardScope::getId, s -> s));
        Map<Long, DictAwardLevel> levelMap = levelIds.isEmpty() ? Map.of() : awardLevelMapper.selectBatchIds(levelIds).stream()
                .collect(Collectors.toMap(DictAwardLevel::getId, l -> l));
        Map<Long, SysDept> deptMap = deptIds.isEmpty() ? Map.of() : deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d));

        Map<Long, List<BizTeamMember>> membersByTeam = teamMembers.stream()
                .collect(Collectors.groupingBy(BizTeamMember::getTeamId));
        membersByTeam.values().forEach(list ->
                list.sort(Comparator.comparing(BizTeamMember::getMemberOrderNo, Comparator.nullsLast(Integer::compareTo))));

        Map<Long, List<BizTeamTeacher>> teachersByTeam = teamTeachers.stream()
                .collect(Collectors.groupingBy(BizTeamTeacher::getTeamId));
        teachersByTeam.values().forEach(list ->
                list.sort(Comparator.comparing(BizTeamTeacher::getTeacherOrderNo, Comparator.nullsLast(Integer::compareTo))));

        List<DisciplineStatsGroup> groups = new java.util.ArrayList<>();
        for (BizAwardRecord record : records) {
            List<BizTeamMember> members = membersByTeam.getOrDefault(record.getTeamId(), Collections.emptyList());

            DictCompetition competition = competitionMap.get(record.getCompetitionId());
            DictCompetitionCategory category = categoryMap.get(record.getCompetitionCategoryId());
            DictAwardScope scope = scopeMap.get(record.getAwardScopeId());
            DictAwardLevel level = levelMap.get(record.getAwardLevelId());
            SysDept dept = deptMap.get(record.getOwnerDeptId());

            List<String> teacherNames = teachersByTeam.getOrDefault(record.getTeamId(), Collections.emptyList()).stream()
                    .map(t -> userMap.get(t.getTeacherUserId()))
                    .filter(Objects::nonNull)
                    .map(SysUser::getRealName)
                    .filter(Objects::nonNull)
                    .toList();
            String teacherText = String.join("，", teacherNames);

            DisciplineStatsGroup group = new DisciplineStatsGroup();
            group.categoryName = category == null ? "" : nullToEmpty(category.getCategoryName());
            group.semester = nullToEmpty(record.getSemester());
            group.awardDate = record.getAwardDate() == null ? "" : record.getAwardDate().toString();
            group.competitionName = competition == null ? "" : nullToEmpty(competition.getCompetitionName());
            group.awardScopeName = scope == null ? "" : nullToEmpty(scope.getScopeName());
            group.awardLevelName = level == null ? "" : nullToEmpty(level.getLevelName());
            group.projectName = nullToEmpty(record.getProjectName());
            group.teamCount = record.getTeamAwardCount() == null ? 1 : record.getTeamAwardCount();
            group.teacherNames = teacherText;
            group.deptName = dept == null ? "" : nullToEmpty(dept.getDeptName());
            group.remark = nullToEmpty(record.getRemark());
            group.students = new java.util.ArrayList<>();

            if (members.isEmpty()) {
                group.students.add(new DisciplineStatsStudent("", "", "", "", ""));
            } else {
                for (BizTeamMember member : members) {
                    SysUser student = userMap.get(member.getUserId());
                    DictClass clazz = (student == null || student.getClassId() == null) ? null : classMap.get(student.getClassId());
                    String studentDept = (student == null || student.getDeptId() == null || deptMap.get(student.getDeptId()) == null)
                            ? ""
                            : nullToEmpty(deptMap.get(student.getDeptId()).getDeptName());
                    group.students.add(new DisciplineStatsStudent(
                            student == null ? "" : nullToEmpty(student.getRealName()),
                            clazz == null ? "" : nullToEmpty(clazz.getClassName()),
                            studentDept,
                            student == null ? "" : nullToEmpty(student.getStudentNo()),
                            Objects.toString(member.getMemberOrderNo(), "")
                    ));
                }
            }
            groups.add(group);
        }
        return groups;
    }

    private void writeDisciplineStatsByPoi(Path templatePath, Path outPath, List<DisciplineStatsGroup> groups) throws Exception {
        try (InputStream in = Files.newInputStream(templatePath);
             Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row templateRow = sheet.getRow(DISCIPLINE_TEMPLATE_DATA_ROW_INDEX);
            if (templateRow == null) {
                throw new BizException(500, "统计模板缺少数据模板行（第3行）");
            }

            CellStyle[] styles = new CellStyle[DISCIPLINE_COL_COUNT];
            for (int i = 0; i < DISCIPLINE_COL_COUNT; i++) {
                Cell c = templateRow.getCell(i);
                styles[i] = c == null ? null : c.getCellStyle();
            }
            short templateRowHeight = templateRow.getHeight();

            // 清理模板中数据区已有的合并（避免旧合并影响新写入）
            clearDataRegionMergedCells(sheet, DISCIPLINE_TEMPLATE_DATA_ROW_INDEX);
            // 清理模板中数据区已有行（包含占位符行），避免“占位+数据”混合覆盖
            clearDataRegionRows(sheet, DISCIPLINE_TEMPLATE_DATA_ROW_INDEX);

            int rowIndex = DISCIPLINE_TEMPLATE_DATA_ROW_INDEX;
            for (DisciplineStatsGroup group : groups) {
                int startRow = rowIndex;
                int rowSpan = Math.max(1, group.students.size());
                for (int i = 0; i < rowSpan; i++) {
                    Row row = sheet.createRow(rowIndex);
                    row.setHeight(templateRowHeight);
                    ensureRowStyles(row, styles);
                    resetRowValues(row);

                    DisciplineStatsStudent student = group.students.get(i);
                    writeCell(row, 8, student.studentName);
                    writeCell(row, 9, student.studentClass);
                    writeCell(row, 10, student.studentDeptName);
                    writeCell(row, 11, student.studentNo);
                    writeCell(row, 13, student.memberOrderNo);
                    rowIndex++;
                }
                int endRow = rowIndex - 1;

                Row firstRow = sheet.getRow(startRow);
                writeCell(firstRow, 0, group.categoryName);
                writeCell(firstRow, 1, group.semester);
                writeCell(firstRow, 2, group.awardDate);
                writeCell(firstRow, 3, group.competitionName);
                writeCell(firstRow, 4, group.awardScopeName);
                writeCell(firstRow, 5, group.awardLevelName);
                writeCell(firstRow, 6, group.projectName);
                writeCell(firstRow, 7, group.teamCount);
                writeCell(firstRow, 12, group.students.size());
                writeCell(firstRow, 14, group.teacherNames);
                writeCell(firstRow, 15, group.deptName);
                writeCell(firstRow, 16, group.remark);

                mergeIfNeeded(sheet, startRow, endRow, 0);
                mergeIfNeeded(sheet, startRow, endRow, 1);
                mergeIfNeeded(sheet, startRow, endRow, 2);
                mergeIfNeeded(sheet, startRow, endRow, 3);
                mergeIfNeeded(sheet, startRow, endRow, 4);
                mergeIfNeeded(sheet, startRow, endRow, 5);
                mergeIfNeeded(sheet, startRow, endRow, 6);
                mergeIfNeeded(sheet, startRow, endRow, 7);
                mergeIfNeeded(sheet, startRow, endRow, 12);
                mergeIfNeeded(sheet, startRow, endRow, 14);
                mergeIfNeeded(sheet, startRow, endRow, 15);
                mergeIfNeeded(sheet, startRow, endRow, 16);
            }

            // 兜底清理模板残留占位符，避免 {records.xxx} 出现在导出结果中
            clearResidualRecordPlaceholders(sheet);

            try (OutputStream out = Files.newOutputStream(outPath)) {
                workbook.write(out);
            }
        }
    }

    private void ensureRowStyles(Row row, CellStyle[] styles) {
        for (int i = 0; i < DISCIPLINE_COL_COUNT; i++) {
            Cell cell = row.getCell(i);
            if (cell == null) cell = row.createCell(i);
            if (styles[i] != null) {
                cell.setCellStyle(styles[i]);
            }
        }
    }

    private void writeCell(Row row, int col, String val) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(val == null ? "" : val);
    }

    private void writeCell(Row row, int col, int val) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(val);
    }

    private void mergeIfNeeded(Sheet sheet, int startRow, int endRow, int col) {
        if (endRow <= startRow) return;
        sheet.addMergedRegion(new CellRangeAddress(startRow, endRow, col, col));
    }

    private void resetRowValues(Row row) {
        for (int i = 0; i < DISCIPLINE_COL_COUNT; i++) {
            Cell cell = row.getCell(i);
            if (cell == null) cell = row.createCell(i);
            cell.setCellValue("");
        }
    }

    private void clearDataRegionMergedCells(Sheet sheet, int startDataRow) {
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() >= startDataRow || region.getLastRow() >= startDataRow) {
                sheet.removeMergedRegion(i);
            }
        }
    }

    private void clearDataRegionRows(Sheet sheet, int startDataRow) {
        int last = sheet.getLastRowNum();
        for (int r = last; r >= startDataRow; r--) {
            Row row = sheet.getRow(r);
            if (row != null) {
                sheet.removeRow(row);
            }
        }
    }

    private void clearResidualRecordPlaceholders(Sheet sheet) {
        int first = sheet.getFirstRowNum();
        int last = sheet.getLastRowNum();
        for (int r = first; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            short firstCol = row.getFirstCellNum();
            short lastCol = row.getLastCellNum();
            if (firstCol < 0 || lastCol < 0) continue;
            for (int c = firstCol; c < lastCol; c++) {
                Cell cell = row.getCell(c);
                if (cell == null || cell.getCellType() != CellType.STRING) continue;
                String val = cell.getStringCellValue();
                if (val != null && val.contains("{records.")) {
                    cell.setCellValue("");
                }
            }
        }
    }

    private void writeAwardGradeMatrixTemplate(Path outPath, List<AwardGradeMatrixRow> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("竞赛获奖等级统计");
            int[] widths = {
                    12, 8, 44, 13, 13,
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 10
            };
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setWrapText(true);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);
            headerStyle.setFont(headerFont);

            CellStyle bodyStyle = workbook.createCellStyle();
            bodyStyle.setAlignment(HorizontalAlignment.CENTER);
            bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            bodyStyle.setBorderTop(BorderStyle.THIN);
            bodyStyle.setBorderBottom(BorderStyle.THIN);
            bodyStyle.setBorderLeft(BorderStyle.THIN);
            bodyStyle.setBorderRight(BorderStyle.THIN);
            bodyStyle.setWrapText(true);
            Font bodyFont = workbook.createFont();
            bodyFont.setFontHeightInPoints((short) 9);
            bodyStyle.setFont(bodyFont);

            Row row0 = sheet.createRow(0);
            Row row1 = sheet.createRow(1);
            row0.setHeightInPoints(24);
            row1.setHeightInPoints(22);
            for (int c = 0; c <= 24; c++) {
                Cell c0 = row0.createCell(c);
                c0.setCellStyle(headerStyle);
                c0.setCellValue("");
                Cell c1 = row1.createCell(c);
                c1.setCellStyle(headerStyle);
                c1.setCellValue("");
            }

            setHeader(row0, 0, "竞赛类别", headerStyle);
            setHeader(row0, 1, "序号", headerStyle);
            setHeader(row0, 2, "比赛名称", headerStyle);
            setHeader(row0, 3, "获奖学期", headerStyle);
            setHeader(row0, 4, "获奖时间", headerStyle);
            mergeCell(sheet, 0, 1, 0, 0);
            mergeCell(sheet, 0, 1, 1, 1);
            mergeCell(sheet, 0, 1, 2, 2);
            mergeCell(sheet, 0, 1, 3, 3);
            mergeCell(sheet, 0, 1, 4, 4);

            String[] levelHeaders = {
                    "国家级特等奖", "国家级一等奖", "国家级二等奖", "国家级三等奖", "国家级优秀奖",
                    "省级特等奖", "省级一等奖", "省级二等奖", "省级三等奖", "省级优秀奖"
            };
            for (int i = 0; i < levelHeaders.length; i++) {
                int colStart = 5 + (i * 2);
                int colEnd = colStart + 1;
                setHeader(row0, colStart, levelHeaders[i], headerStyle);
                mergeCell(sheet, 0, 0, colStart, colEnd);
                setHeader(row1, colStart, "获奖数量", headerStyle);
                setHeader(row1, colEnd, "获奖人次", headerStyle);
            }

            int dataStart = 2;
            List<AwardGradeMatrixRow> safeRows = rows == null ? List.of() : rows;
            int dataRows = Math.max(safeRows.size(), 1);
            for (int r = dataStart; r < dataStart + dataRows; r++) {
                Row row = sheet.createRow(r);
                row.setHeightInPoints(20);
                for (int c = 0; c <= 24; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellStyle(bodyStyle);
                    cell.setCellValue("");
                }
            }

            if (!safeRows.isEmpty()) {
                for (int i = 0; i < safeRows.size(); i++) {
                    AwardGradeMatrixRow item = safeRows.get(i);
                    Row row = sheet.getRow(dataStart + i);
                    writeCell(row, 0, item.categoryName);
                    writeCell(row, 1, item.seqNo);
                    writeCell(row, 2, item.competitionName);
                    writeCell(row, 3, item.semester);
                    writeCell(row, 4, item.awardDateText);
                    for (int b = 0; b < MATRIX_BIN_ORDER.length; b++) {
                        int colStart = 5 + (b * 2);
                        String binKey = MATRIX_BIN_ORDER[b];
                        AwardGradeAgg agg = item.metrics.getOrDefault(binKey, AwardGradeAgg.ZERO);
                        writeCell(row, colStart, agg.awardCount);
                        writeCell(row, colStart + 1, agg.personTimes);
                    }
                }
                mergeCategoryColumn(sheet, safeRows, dataStart);
            }

            try (OutputStream out = Files.newOutputStream(outPath)) {
                workbook.write(out);
            }
        }
    }

    private List<AwardGradeMatrixRow> buildAwardGradeMatrixRows(List<BizAwardRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Set<Long> teamIds = records.stream()
                .map(BizAwardRecord::getTeamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> competitionIds = records.stream()
                .map(BizAwardRecord::getCompetitionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> categoryIds = records.stream()
                .map(BizAwardRecord::getCompetitionCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> scopeIds = records.stream()
                .map(BizAwardRecord::getAwardScopeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> levelIds = records.stream()
                .map(BizAwardRecord::getAwardLevelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, DictCompetition> competitionMap = competitionIds.isEmpty() ? Map.of() : competitionMapper.selectBatchIds(competitionIds)
                .stream().collect(Collectors.toMap(DictCompetition::getId, c -> c));
        Map<Long, DictCompetitionCategory> categoryMap = categoryIds.isEmpty() ? Map.of() : categoryMapper.selectBatchIds(categoryIds)
                .stream().collect(Collectors.toMap(DictCompetitionCategory::getId, c -> c));
        Map<Long, DictAwardScope> scopeMap = scopeIds.isEmpty() ? Map.of() : awardScopeMapper.selectBatchIds(scopeIds)
                .stream().collect(Collectors.toMap(DictAwardScope::getId, s -> s));
        Map<Long, DictAwardLevel> levelMap = levelIds.isEmpty() ? Map.of() : awardLevelMapper.selectBatchIds(levelIds)
                .stream().collect(Collectors.toMap(DictAwardLevel::getId, l -> l));
        Map<Long, Set<Long>> teamStudentIdsMap = loadAcceptedStudentIdsByTeam(teamIds);

        Map<String, AwardGradeMatrixRow> grouped = new LinkedHashMap<>();
        for (BizAwardRecord record : records) {
            Long competitionId = record.getCompetitionId();
            Long categoryId = record.getCompetitionCategoryId();
            String rowKey = String.valueOf(categoryId) + "|" + String.valueOf(competitionId);
            AwardGradeMatrixRow row = grouped.get(rowKey);
            if (row == null) {
                DictCompetitionCategory category = categoryMap.get(categoryId);
                DictCompetition competition = competitionMap.get(competitionId);
                row = new AwardGradeMatrixRow();
                row.categoryName = category == null ? "" : nullToEmpty(category.getCategoryName());
                row.competitionName = competition == null ? "" : nullToEmpty(competition.getCompetitionName());
                row.metrics = new LinkedHashMap<>(); // 最后再由累加器转成展示值
                row.semesterSet = new LinkedHashSet<>();
                row.awardMonthSet = new LinkedHashSet<>();
                grouped.put(rowKey, row);
            }
            if (record.getSemester() != null && !record.getSemester().isBlank()) {
                row.semesterSet.add(record.getSemester());
            }
            if (record.getAwardDate() != null) {
                row.awardMonthSet.add(YearMonth.from(record.getAwardDate()));
            }

            DictAwardScope scope = levelScope(record, scopeMap);
            DictAwardLevel level = levelMap.get(record.getAwardLevelId());
            String binKey = resolveMatrixBin(scope == null ? "" : nullToEmpty(scope.getScopeName()),
                    level == null ? "" : nullToEmpty(level.getLevelName()));
            if (binKey == null) {
                continue;
            }
            AwardGradeAggBuilder agg = row.metricBuilders.computeIfAbsent(binKey, k -> new AwardGradeAggBuilder());
            agg.awardCount++;
            if (record.getTeamId() != null) {
                Set<Long> studentIds = teamStudentIdsMap.getOrDefault(record.getTeamId(), Set.of());
                agg.studentIds.addAll(studentIds);
            }
        }

        List<AwardGradeMatrixRow> rows = new ArrayList<>(grouped.values());
        for (AwardGradeMatrixRow row : rows) {
            for (String key : MATRIX_BIN_ORDER) {
                AwardGradeAggBuilder b = row.metricBuilders.get(key);
                if (b == null) continue;
                AwardGradeAgg out = new AwardGradeAgg();
                out.awardCount = b.awardCount;          // 获奖数量：该奖项下获奖记录数量（不去重）
                out.personTimes = b.studentIds.size();  // 获奖人次：这些团队中的学生人数
                row.metrics.put(key, out);
            }
            row.semester = summarizeSemester(row.semesterSet);
            row.awardDateText = summarizeAwardMonth(row.awardMonthSet);
        }
        rows.sort(Comparator
                .comparing((AwardGradeMatrixRow x) -> nullToEmpty(x.categoryName))
                .thenComparing(x -> nullToEmpty(x.competitionName)));

        String currentCategory = null;
        int seq = 0;
        for (AwardGradeMatrixRow row : rows) {
            if (!Objects.equals(currentCategory, row.categoryName)) {
                currentCategory = row.categoryName;
                seq = 1;
            } else {
                seq++;
            }
            row.seqNo = seq;
        }
        return rows;
    }

    private Map<Long, Set<Long>> loadAcceptedStudentIdsByTeam(Set<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        List<BizTeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<BizTeamMember>()
                        .in(BizTeamMember::getTeamId, teamIds)
                        .eq(BizTeamMember::getJoinStatus, "ACCEPTED")
        );
        Map<Long, Set<Long>> out = new LinkedHashMap<>();
        for (BizTeamMember m : members) {
            if (m.getTeamId() == null || m.getUserId() == null) continue;
            out.computeIfAbsent(m.getTeamId(), k -> new LinkedHashSet<>()).add(m.getUserId());
        }
        return out;
    }

    private String summarizeSemester(Set<String> semesters) {
        if (semesters == null || semesters.isEmpty()) return "";
        if (semesters.size() == 1) return semesters.iterator().next();
        return "多学期";
    }

    private String summarizeAwardMonth(Set<YearMonth> months) {
        if (months == null || months.isEmpty()) return "";
        if (months.size() == 1) return months.iterator().next().toString();
        return months.stream()
                .sorted()
                .map(YearMonth::toString)
                .collect(Collectors.joining("、"));
    }

    private DictAwardScope levelScope(BizAwardRecord record, Map<Long, DictAwardScope> scopeMap) {
        DictAwardScope byRecord = scopeMap.get(record.getAwardScopeId());
        if (byRecord != null) {
            return byRecord;
        }
        return null;
    }

    private String resolveMatrixBin(String scopeName, String levelName) {
        String scope = scopeName == null ? "" : scopeName;
        String level = levelName == null ? "" : levelName;
        boolean national = scope.contains("国家");
        boolean provincial = scope.contains("省") || scope.contains("部");
        if (!national && !provincial) return null;

        String levelCode = resolveLevelCode(level);
        if (levelCode == null) return null;
        return (national ? "N_" : "P_") + levelCode;
    }

    private String resolveLevelCode(String levelName) {
        if (levelName == null || levelName.isBlank()) return null;
        if (levelName.contains("特")) return "SPECIAL";
        if (levelName.contains("一") || levelName.contains("1") || levelName.contains("Ⅰ")) return "FIRST";
        if (levelName.contains("二") || levelName.contains("2") || levelName.contains("Ⅱ")) return "SECOND";
        if (levelName.contains("三") || levelName.contains("3") || levelName.contains("Ⅲ")) return "THIRD";
        if (levelName.contains("优")) return "EXCELLENT";
        return null;
    }

    private void mergeCategoryColumn(Sheet sheet, List<AwardGradeMatrixRow> rows, int dataStartRow) {
        if (rows.isEmpty()) return;
        int start = 0;
        while (start < rows.size()) {
            int end = start;
            String category = rows.get(start).categoryName;
            while (end + 1 < rows.size() && Objects.equals(category, rows.get(end + 1).categoryName)) {
                end++;
            }
            int startRow = dataStartRow + start;
            int endRow = dataStartRow + end;
            if (endRow > startRow) {
                mergeCell(sheet, startRow, endRow, 0, 0);
            }
            start = end + 1;
        }
    }

    private static final String[] MATRIX_BIN_ORDER = {
            "N_SPECIAL", "N_FIRST", "N_SECOND", "N_THIRD", "N_EXCELLENT",
            "P_SPECIAL", "P_FIRST", "P_SECOND", "P_THIRD", "P_EXCELLENT"
    };

    private static class AwardGradeMatrixRow {
        private String categoryName;
        private int seqNo;
        private String competitionName;
        private String semester;
        private String awardDateText;
        private Map<String, AwardGradeAgg> metrics;
        private Map<String, AwardGradeAggBuilder> metricBuilders = new LinkedHashMap<>();
        private Set<String> semesterSet;
        private Set<YearMonth> awardMonthSet;
    }

    private static class AwardGradeAggBuilder {
        private int awardCount;
        private final Set<Long> studentIds = new LinkedHashSet<>();
    }

    private static class AwardGradeAgg {
        private static final AwardGradeAgg ZERO = new AwardGradeAgg();
        private int awardCount;
        private int personTimes;
    }

    private void setHeader(Row row, int col, String text, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        cell.setCellValue(text);
    }

    private void mergeCell(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstCol, lastCol));
    }

    private static class DisciplineStatsGroup {
        private String categoryName;
        private String semester;
        private String awardDate;
        private String competitionName;
        private String awardScopeName;
        private String awardLevelName;
        private String projectName;
        private int teamCount;
        private String teacherNames;
        private String deptName;
        private String remark;
        private List<DisciplineStatsStudent> students;
    }

    private record DisciplineStatsStudent(
            String studentName,
            String studentClass,
            String studentDeptName,
            String studentNo,
            String memberOrderNo
    ) {}

    private String templatePathByReport(String reportCode) {
        return switch (reportCode) {
            case REPORT_DISCIPLINE_STATS -> disciplineStatsTemplate;
            default -> throw new BizException(400, "不支持的导出类型：" + reportCode);
        };
    }

    private String normalizeReportCode(String reportCode) {
        if (reportCode == null || reportCode.isBlank()) throw new BizException(400, "reportCode 不能为空");
        String value = reportCode.trim().toUpperCase();
        if (REPORT_AWARD_GRADE_MATRIX.equals(value) || REPORT_DISCIPLINE_STATS.equals(value)) {
            return value;
        }
        throw new BizException(400, "不支持的导出类型：" + reportCode);
    }

    private String buildDownloadFileName(String reportCode) {
        if (REPORT_AWARD_GRADE_MATRIX.equals(reportCode)) {
            return "竞赛获奖等级统计矩阵表_" + LocalDate.now().format(EXPORT_FILE_DATE_FMT) + ".xlsx";
        }
        return "学科竞赛获奖信息统计表_" + LocalDate.now().format(EXPORT_FILE_DATE_FMT) + ".xlsx";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public PageResult<BizExportTask> myTasks(long pageNo, long pageSize) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        Page<BizExportTask> page = taskMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<BizExportTask>()
                        .eq(BizExportTask::getCreatorUserId, uid)
                        .orderByDesc(BizExportTask::getId)
        );
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Override
    public BizExportTask getTask(Long id) {
        StpUtil.checkLogin();
        Long uid = StpUtil.getLoginIdAsLong();
        BizExportTask task = taskMapper.selectById(id);
        if (task == null) throw new BizException(404, "导出任务不存在");
        if (!authz.hasAnyRole("SCHOOL_ADMIN", "SYS_ADMIN") && (task.getCreatorUserId() == null || !uid.equals(task.getCreatorUserId()))) {
            throw new BizException(403, "无权限");
        }
        return task;
    }
}

