package com.university.awards.common;

import cn.dev33.satoken.stp.StpUtil;
import com.university.awards.common.ApiResponse;
import com.university.awards.common.BizException;
import com.university.awards.log.entity.SysOperateLog;
import com.university.awards.log.service.SysOperateLogService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;

/**
 * 控制器操作日志切面（写操作审计）。
 *
 * <p>设计取舍（MVP）：</p>
 * <ul>
 *   <li>只持久化“写操作”（POST/PUT/DELETE）。GET/HEAD/OPTIONS 通常为查询请求，量大且价值相对低。</li>
 *   <li>登录接口 {@code /api/auth/login} 不在这里记录：它有独立的 {@code sys_login_log}。</li>
 *   <li>日志写入失败不能影响业务请求：saveLog 内部吞掉异常。</li>
 * </ul>
 *
 * <p>字段说明（sys_operate_log）：</p>
 * <ul>
 *   <li>bizType：根据 URI 粗略归类（AWARD_RECORD/TEAM/DICT/...），便于筛选。</li>
 *   <li>action：httpMethod 粗略映射（POST/PUT/DELETE）。</li>
 *   <li>responseCode：优先取 {@link ApiResponse#getCode()}；异常时取 {@link BizException#getCode()} 或 500。</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class OperateLogAspect {

    private final SysOperateLogService operateLogService;

    public OperateLogAspect(SysOperateLogService operateLogService) {
        this.operateLogService = operateLogService;
    }

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object aroundController(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String method = pjp.getSignature().toShortString();
        String uid = StpUtil.isLogin() ? String.valueOf(StpUtil.getLoginId()) : "anonymous";
        HttpServletRequest req = currentRequest();
        String httpMethod = req == null ? null : req.getMethod();
        String uri = req == null ? null : req.getRequestURI();
        try {
            Object result = pjp.proceed();
            long cost = System.currentTimeMillis() - start;
            log.info("api_ok user={} method={} costMs={}", uid, method, cost);

            // only persist mutating requests
            if (shouldPersist(httpMethod, uri)) {
                Integer respCode = extractBizCode(result);
                saveLog(httpMethod, uri, true, respCode, null, cost, req);
            }
            return result;
        } catch (Throwable t) {
            long cost = System.currentTimeMillis() - start;
            log.warn("api_err user={} method={} costMs={} err={}", uid, method, cost, t.toString());
            if (shouldPersist(httpMethod, uri)) {
                Integer respCode = t instanceof BizException be ? be.getCode() : 500;
                saveLog(httpMethod, uri, false, respCode, truncate(t.getMessage()), cost, req);
            }
            throw t;
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) return sra.getRequest();
        return null;
    }

    private boolean shouldPersist(String httpMethod, String uri) {
        if (httpMethod == null || uri == null) return false;
        // 查询类请求不记库（减少噪声与存储压力）
        if (HttpMethod.GET.matches(httpMethod) || HttpMethod.HEAD.matches(httpMethod) || HttpMethod.OPTIONS.matches(httpMethod)) {
            return false;
        }
        // avoid logging login/logout too verbosely; login has dedicated login log
        if (uri.startsWith("/api/auth/login")) return false;
        // 只记录本系统 API 路径（避免误记静态资源等）
        return uri.startsWith("/api/");
    }

    private Integer extractBizCode(Object result) {
        if (result instanceof ApiResponse<?> ar) return ar.getCode();
        return 0;
    }

    private void saveLog(String httpMethod, String uri, boolean ok, Integer respCode, String err, long cost, HttpServletRequest req) {
        try {
            SysOperateLog l = new SysOperateLog();
            if (StpUtil.isLogin()) {
                l.setUserId(StpUtil.getLoginIdAsLong());
            }
            // username is not always available without extra query; keep null for MVP
            l.setUsername(null);
            l.setHttpMethod(httpMethod);
            l.setRequestUri(uri);
            l.setBizType(guessBizType(uri));
            l.setAction(guessAction(httpMethod, uri));
            // MVP：不记录 requestBody（可能包含敏感信息；且读取 body 需要额外包装 request）
            l.setRequestBody(null);
            l.setResponseCode(respCode);
            l.setSuccessFlag(ok ? 1 : 0);
            l.setErrorMessage(err);
            l.setIp(req == null ? null : truncate(req.getRemoteAddr()));
            l.setUserAgent(req == null ? null : truncate(req.getHeader("User-Agent")));
            l.setCostMs(cost);
            l.setCreatedAt(LocalDateTime.now());
            operateLogService.save(l);
        } catch (Throwable ignore) {
            // never break business request due to logging
        }
    }

    private String guessBizType(String uri) {
        if (uri == null) return null;
        if (uri.startsWith("/api/award-records")) return "AWARD_RECORD";
        if (uri.startsWith("/api/teams") || uri.startsWith("/api/team-invitations")) return "TEAM";
        if (uri.startsWith("/api/audit")) return "AUDIT";
        if (uri.startsWith("/api/exports")) return "EXPORT";
        if (uri.startsWith("/api/dicts")) return "DICT";
        if (uri.startsWith("/api/admin")) return "ADMIN";
        if (uri.startsWith("/api/system")) return "SYSTEM";
        if (uri.startsWith("/api/messages")) return "MESSAGE";
        if (uri.startsWith("/api/files")) return "FILE";
        return "API";
    }

    private String guessAction(String httpMethod, String uri) {
        if (httpMethod == null) return null;
        if (HttpMethod.POST.matches(httpMethod)) return "POST";
        if (HttpMethod.PUT.matches(httpMethod)) return "PUT";
        if (HttpMethod.DELETE.matches(httpMethod)) return "DELETE";
        return httpMethod;
    }

    private String truncate(String s) {
        if (s == null) return null;
        if (s.length() <= 900) return s;
        return s.substring(0, 900);
    }
}

