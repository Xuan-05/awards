package com.university.awards.importer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import com.university.awards.dict.entity.*;
import com.university.awards.dict.service.*;
import com.university.awards.rbac.service.AuthzService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管理员导入接口（一次性工具型）。
 *
 * <p>
 * 用途：将“竞赛项目奖项分析表”中的竞赛及其关联关系导入字典：
 * </p>
 * <ul>
 * <li>竞赛类别（dict_competition_category）</li>
 * <li>主办方（dict_organizer）：按整格文本合并写入（一个竞赛绑定一个 organizerId）</li>
 * <li>竞赛（dict_competition）：按竞赛名称去重 upsert</li>
 * <li>奖项范围/等级（dict_award_scope / dict_award_level）：从“主要奖项设置”抽取全局并集去重写入</li>
 * </ul>
 *
 * <p>
 * 权限：仅 SCHOOL_ADMIN / SYS_ADMIN。
 * </p>
 */
@RestController
@RequestMapping("/api/admin/import")
@RequiredArgsConstructor
public class AdminImportController {

    private final DictCompetitionCategoryService categoryService;
    private final DictOrganizerService organizerService;
    private final DictCompetitionService competitionService;
    private final DictAwardScopeService awardScopeService;
    private final DictAwardLevelService awardLevelService;
    private final AuthzService authz;

    /**
     * 导入竞赛字典（文本形式）。
     *
     * <p>
     * 输入格式：
     * </p>
     * <ul>
     * <li>推荐：从 Excel 直接复制粘贴（TSV/表格文本，包含表头）。</li>
     * <li>也支持 OCR 文本（列之间用多个空格分隔）。</li>
     * </ul>
     *
     * @param req 导入请求
     * @return 导入统计
     */
    @PostMapping("/competitions-text")
    public ApiResponse<ImportResult> importCompetitionsText(@RequestBody @Valid ImportTextReq req) {
        authz.requireAnyRole("SCHOOL_ADMIN", "SYS_ADMIN");
        ImportResult result = new ImportResult();

        List<Row> rows = parseRows(req.getText());
        if (rows.isEmpty()) {
            throw new BizException(400, "未解析到有效数据行，请确认粘贴内容包含表头与数据");
        }

        // category cache by display name (e.g. "A类" / "未分类")
        Map<String, DictCompetitionCategory> categoryByName = new HashMap<>();
        // organizer cache by name (full cell)
        Map<String, DictOrganizer> organizerByName = new HashMap<>();
        // competition cache by name
        Map<String, DictCompetition> competitionByName = new HashMap<>();
        // award scope cache by name
        Map<String, DictAwardScope> scopeByName = new HashMap<>();

        // preload existing dictionaries (basic)
        preloadCategories(categoryByName);
        preloadOrganizers(organizerByName);
        preloadCompetitions(competitionByName);
        preloadScopes(scopeByName);

        // parse awards to global union sets
        Set<AwardScopeLevel> union = new LinkedHashSet<>();

        for (Row r : rows) {
            // 1) category upsert
            DictCompetitionCategory cat = ensureCategory(r.typeCodeTrim(), categoryByName, result);
            // 2) organizer upsert (join-as-one per decision)
            DictOrganizer org = ensureOrganizer(r.organizerTrim(), organizerByName, result);
            // 3) competition upsert by name
            ensureCompetition(r.competitionNameTrim(), cat.getId(), org == null ? null : org.getId(), competitionByName,
                    result);
            // 4) award scope/level extract union
            union.addAll(extractAwards(r.awardsTextTrim()));
        }

        // upsert award scope/levels
        upsertAwardUnion(union, scopeByName, result);

        result.setParsedRows(rows.size());
        result.setUnionAwardPairs(union.size());
        return ApiResponse.ok(result);
    }

    // ---------------- request/response ----------------

    @Data
    public static class ImportTextReq {
        /**
         * Excel 复制粘贴的表格文本（建议包含表头）。
         */
        @NotBlank
        private String text;
    }

    @Data
    public static class ImportResult {
        private int parsedRows;
        private int createdCategories;
        private int createdOrganizers;
        private int createdCompetitions;
        private int updatedCompetitions;
        private int createdAwardScopes;
        private int createdAwardLevels;
        private int unionAwardPairs;
        private List<String> warnings = new ArrayList<>();

        void warn(String msg) {
            if (warnings.size() < 200)
                warnings.add(msg);
        }
    }

    // ---------------- parsing ----------------

    /**
     * 行结构（从文本解析后使用）。
     *
     * @param typeCode        竞赛类型（A/B/C/D...）
     * @param competitionName 竞赛项目名称
     * @param organizer       主办方（部门）整格文本
     * @param awardsText      主要奖项设置文本
     */
    private record Row(String typeCode, String competitionName, String organizer, String awardsText) {
        String typeCodeTrim() {
            return typeCode == null ? "" : typeCode.trim();
        }

        String competitionNameTrim() {
            return competitionName == null ? "" : competitionName.trim();
        }

        String organizerTrim() {
            return organizer == null ? "" : organizer.trim();
        }

        String awardsTextTrim() {
            return awardsText == null ? "" : awardsText.trim();
        }
    }

    private static final Pattern TSV_HEADER = Pattern.compile("竞赛类型\\s+竞赛项目名称");

    private List<Row> parseRows(String text) {
        if (text == null)
            return List.of();
        String raw = text.replace("\r\n", "\n").replace("\r", "\n");
        List<String> lines = raw.lines().map(String::trim).filter(s -> !s.isBlank()).toList();
        if (lines.isEmpty())
            return List.of();

        // Try TSV (tab-separated) first.
        boolean hasTab = raw.contains("\t");
        if (hasTab) {
            return parseTsv(lines);
        }

        // Fallback: OCR text with multiple spaces between columns.
        // Expected columns: type | competitionName | ... | organizer | awards
        return parseOcrSpaceSeparated(lines);
    }

    private List<Row> parseTsv(List<String> lines) {
        int startIdx = 0;
        Map<String, Integer> header = new HashMap<>();
        // detect header line (first line that contains required columns)
        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            String ln = lines.get(i);
            if (TSV_HEADER.matcher(ln).find() || ln.contains("\t竞赛项目名称")) {
                String[] hs = ln.split("\t");
                for (int j = 0; j < hs.length; j++) {
                    String h = hs[j] == null ? "" : hs[j].trim();
                    if (!h.isBlank())
                        header.put(h, j);
                }
                startIdx = i + 1;
                break;
            }
        }
        List<Row> out = new ArrayList<>();
        for (int i = startIdx; i < lines.size(); i++) {
            String[] cols = lines.get(i).split("\t");
            if (cols.length < 2)
                continue;
            // skip header if not detected
            if ("竞赛类型".equals(cols[0].trim()))
                continue;

            int idxType = header.getOrDefault("竞赛类型", 0);
            int idxName = header.getOrDefault("竞赛项目名称", 1);
            // 主办方（部门）是我们决策要绑定的字段；若不存在则退化取第 3 列
            int idxOrg = header.getOrDefault("主办方（部门）", Math.min(3, cols.length - 1));
            int idxAwards = header.getOrDefault("主要奖项设置", cols.length - 1);

            String type = idxType >= 0 && idxType < cols.length ? cols[idxType] : "";
            String name = idxName >= 0 && idxName < cols.length ? cols[idxName] : "";
            String organizer = idxOrg >= 0 && idxOrg < cols.length ? cols[idxOrg] : "";
            String awards = idxAwards >= 0 && idxAwards < cols.length ? cols[idxAwards] : "";
            if (!StringUtils.hasText(type) || !StringUtils.hasText(name))
                continue;
            out.add(new Row(type, name, organizer, awards));
        }
        return out;
    }

    private List<Row> parseOcrSpaceSeparated(List<String> lines) {
        int startIdx = 0;
        for (int i = 0; i < Math.min(10, lines.size()); i++) {
            if (lines.get(i).contains("竞赛类型") && lines.get(i).contains("竞赛项目名称")) {
                startIdx = i + 1;
                break;
            }
        }

        List<Row> out = new ArrayList<>();
        for (int i = startIdx; i < lines.size(); i++) {
            String line = lines.get(i);
            // Typical OCR begins with single-letter type.
            // Split by 2+ spaces to approximate columns.
            String[] parts = line.split("\\s{2,}");
            if (parts.length < 3)
                continue;
            String type = parts[0];
            if (type.length() > 2)
                continue;
            String name = parts[1];
            // Heuristic: organizer is usually 3rd or 4th column (主管方/主办方)
            String organizer = parts.length >= 4 ? parts[3] : parts[2];
            String awards = parts[parts.length - 1];
            if (!StringUtils.hasText(type) || !StringUtils.hasText(name))
                continue;
            out.add(new Row(type, name, organizer, awards));
        }
        return out;
    }

    // ---------------- upsert helpers ----------------

    private void preloadCategories(Map<String, DictCompetitionCategory> categoryByName) {
        categoryService.list(new LambdaQueryWrapper<DictCompetitionCategory>()).forEach(c -> {
            if (c.getCategoryName() != null)
                categoryByName.put(c.getCategoryName(), c);
        });
    }

    private void preloadOrganizers(Map<String, DictOrganizer> organizerByName) {
        organizerService.list(new LambdaQueryWrapper<DictOrganizer>()).forEach(o -> {
            if (o.getOrganizerName() != null)
                organizerByName.put(o.getOrganizerName(), o);
        });
    }

    private void preloadCompetitions(Map<String, DictCompetition> competitionByName) {
        competitionService.list(new LambdaQueryWrapper<DictCompetition>()).forEach(c -> {
            if (c.getCompetitionName() != null)
                competitionByName.put(c.getCompetitionName(), c);
        });
    }

    private void preloadScopes(Map<String, DictAwardScope> scopeByName) {
        awardScopeService.list(new LambdaQueryWrapper<DictAwardScope>()).forEach(s -> {
            if (s.getScopeName() != null)
                scopeByName.put(s.getScopeName(), s);
        });
    }

    private DictCompetitionCategory ensureCategory(String typeRaw, Map<String, DictCompetitionCategory> cache,
            ImportResult result) {
        String type = (typeRaw == null ? "" : typeRaw.trim());
        final String categoryName;
        if (type.isBlank()) {
            result.warn("存在空的竞赛类型，已归入「未分类」");
            categoryName = "未分类";
        } else {
            categoryName = type + "类";
        }
        DictCompetitionCategory existing = cache.get(categoryName);
        if (existing != null)
            return existing;

        DictCompetitionCategory ins = new DictCompetitionCategory();
        ins.setCategoryName(categoryName);
        ins.setEnabled(1);
        ins.setSortNo(0);
        categoryService.save(ins);
        cache.put(categoryName, ins);
        result.createdCategories++;
        return ins;
    }

    private DictOrganizer ensureOrganizer(String nameRaw, Map<String, DictOrganizer> cache, ImportResult result) {
        String name = (nameRaw == null ? "" : nameRaw.trim());
        if (name.isBlank()) {
            result.warn("竞赛主办方为空，将不绑定 organizerId");
            return null;
        }
        DictOrganizer existing = cache.get(name);
        if (existing != null)
            return existing;

        DictOrganizer ins = new DictOrganizer();
        ins.setOrganizerName(name);
        ins.setEnabled(1);
        ins.setSortNo(0);
        organizerService.save(ins);
        cache.put(name, ins);
        result.createdOrganizers++;
        return ins;
    }

    private void ensureCompetition(String compNameRaw, Long categoryId, Long organizerId,
            Map<String, DictCompetition> cache, ImportResult result) {
        String compName = (compNameRaw == null ? "" : compNameRaw.trim());
        if (compName.isBlank())
            return;

        DictCompetition existing = cache.get(compName);
        if (existing == null) {
            DictCompetition ins = new DictCompetition();
            ins.setCompetitionName(compName);
            ins.setCompetitionShortName(null);
            ins.setCategoryId(categoryId);
            ins.setEnabled(1);
            ins.setSortNo(0);
            competitionService.save(ins);
            cache.put(compName, ins);
            result.createdCompetitions++;
            return;
        }

        boolean needUpdate = false;
        DictCompetition upd = new DictCompetition();
        upd.setId(existing.getId());

        if (categoryId != null && (existing.getCategoryId() == null || !categoryId.equals(existing.getCategoryId()))) {
            upd.setCategoryId(categoryId);
            needUpdate = true;
        }
        if (needUpdate) {
            competitionService.updateById(upd);
            result.updatedCompetitions++;
        }
    }

    // ---------------- award extraction & upsert ----------------

    private record AwardScopeLevel(String scopeName, String levelName) {
    }

    private Set<AwardScopeLevel> extractAwards(String awardsText) {
        if (awardsText == null)
            return Set.of();
        String t = awardsText.trim();
        if (t.isBlank())
            return Set.of();

        // Normalize separators
        String norm = t.replace("，", ",")
                .replace("；", ";")
                .replace("、", ",")
                .replace("：", ":")
                .replace("（", "(")
                .replace("）", ")")
                .replace("。", ";");

        // Detect scopes
        List<String> scopes = new ArrayList<>();
        if (norm.contains("国家"))
            scopes.add("国家级");
        if (norm.contains("省"))
            scopes.add("省级");
        if (norm.contains("校"))
            scopes.add("校级");
        if (norm.contains("国际"))
            scopes.add("国际级");
        if (scopes.isEmpty()) {
            // fallback: unknown
            scopes.add("其他");
        }

        // Extract levels (common patterns)
        Set<String> levels = new LinkedHashSet<>();
        levels.addAll(extractByKeyword(norm, "特等奖"));
        levels.addAll(extractByKeyword(norm, "一等奖"));
        levels.addAll(extractByKeyword(norm, "二等奖"));
        levels.addAll(extractByKeyword(norm, "三等奖"));
        levels.addAll(extractByKeyword(norm, "优秀奖"));
        levels.addAll(extractByKeyword(norm, "金奖"));
        levels.addAll(extractByKeyword(norm, "银奖"));
        levels.addAll(extractByKeyword(norm, "铜奖"));

        if (levels.isEmpty()) {
            // last resort: keep whole text as one level under each scope (still useful for
            // dictionary union)
            levels.add("通用奖项");
        }

        Set<AwardScopeLevel> out = new LinkedHashSet<>();
        for (String s : scopes) {
            for (String l : levels) {
                out.add(new AwardScopeLevel(s, l));
            }
        }
        return out;
    }

    private Set<String> extractByKeyword(String text, String kw) {
        return text.contains(kw) ? Set.of(kw) : Set.of();
    }

    private void upsertAwardUnion(Set<AwardScopeLevel> union, Map<String, DictAwardScope> scopeByName,
            ImportResult result) {
        // ensure scopes first
        for (AwardScopeLevel pair : union) {
            String scopeName = pair.scopeName();
            if (scopeName == null || scopeName.isBlank())
                continue;
            if (!scopeByName.containsKey(scopeName)) {
                DictAwardScope ins = new DictAwardScope();
                ins.setScopeName(scopeName);
                ins.setEnabled(1);
                ins.setSortNo(0);
                awardScopeService.save(ins);
                scopeByName.put(scopeName, ins);
                result.createdAwardScopes++;
            }
        }

        // then levels under scopes (unique: award_scope_id + award_level_name)
        for (AwardScopeLevel pair : union) {
            DictAwardScope scope = scopeByName.get(pair.scopeName());
            if (scope == null)
                continue;
            String levelName = pair.levelName();
            if (levelName == null || levelName.isBlank())
                continue;

            long count = awardLevelService.count(new LambdaQueryWrapper<DictAwardLevel>()
                    .eq(DictAwardLevel::getAwardScopeId, scope.getId())
                    .eq(DictAwardLevel::getLevelName, levelName));
            if (count > 0)
                continue;

            DictAwardLevel ins = new DictAwardLevel();
            ins.setAwardScopeId(scope.getId());
            ins.setLevelName(levelName);
            ins.setEnabled(1);
            ins.setSortNo(0);
            awardLevelService.save(ins);
            result.createdAwardLevels++;
        }
    }

}
