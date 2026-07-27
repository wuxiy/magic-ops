package top.cywu.magicops.examples.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由转发控制器。
 * 将 Vue Router 的客户端路由转发到对应的 index.html，
 * 使 /console/* 和 /diagnosis/* 的前端路由正常工作。
 * 排除含文件扩展名（点号）的路径，避免拦截静态资源请求。
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/console", "/console/",
            "/console/{path:^(?!api|assets)[^.]+$}/**"
    })
    public String consoleForward() {
        return "forward:/console/index.html";
    }

    @GetMapping(value = {
            "/diagnosis", "/diagnosis/",
            "/diagnosis/{path:^(?!api|assets)[^.]+$}/**"
    })
    public String diagnosisForward() {
        return "forward:/diagnosis/index.html";
    }
}
