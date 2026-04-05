package com.university.awards.controller;

import com.university.awards.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PingController {

    /**
     * 健康检查/连通性测试接口。
     *
     * <p>用途：</p>
     * <ul>
     *   <li>用于部署后快速判断服务是否启动、路由是否可达。</li>
     * </ul>
     *
     * <p>权限：无需登录。</p>
     */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }
}

