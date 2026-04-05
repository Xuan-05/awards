package com.university.awards.rbac.service;

import cn.dev33.satoken.stp.StpUtil;
import com.university.awards.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 授权辅助组件（基于当前登录用户）。
 *
 * <p>该类封装：</p>
 * <ul>
 *   <li>当前用户 ID 的获取（会强制校验登录）。</li>
 *   <li>角色校验（requireAnyRole）与角色判断（hasRole/hasAnyRole）。</li>
 * </ul>
 *
 * <p>注意：</p>
 * <ul>
 *   <li>本项目的接口层通常在 Controller/Service 入口处调用此类进行权限控制。</li>
 *   <li>数据范围（例如“只能看自己提交的数据”）属于业务校验，不在此类中统一实现。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AuthzService {

    private final RbacService rbacService;

    /**
     * 获取当前登录用户 ID。
     *
     * @return userId（Long）
     * @throws cn.dev33.satoken.exception.NotLoginException 未登录时抛出
     */
    public Long currentUserId() {
        StpUtil.checkLogin();
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 要求当前用户拥有任意一个角色，否则抛 403。
     *
     * @param roleCodes 允许的角色编码列表
     * @throws BizException 403 无权限
     */
    public void requireAnyRole(String... roleCodes) {
        Long uid = currentUserId();
        Set<String> roles = Set.copyOf(rbacService.getRoleCodes(uid));
        for (String r : roleCodes) {
            if (roles.contains(r)) return;
        }
        throw new BizException(403, "无权限");
    }

    /**
     * 判断当前用户是否拥有某个角色。
     *
     * @param roleCode 角色编码
     */
    public boolean hasRole(String roleCode) {
        Long uid = currentUserId();
        return rbacService.hasRole(uid, roleCode);
    }

    /**
     * 判断当前用户是否拥有任意一个角色。
     */
    public boolean hasAnyRole(String... roleCodes) {
        Long uid = currentUserId();
        Set<String> roles = Set.copyOf(rbacService.getRoleCodes(uid));
        for (String r : roleCodes) {
            if (roles.contains(r)) return true;
        }
        return false;
    }
}

