package top.cywu.magicops.console.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由转发控制器。将 Vue Router 的客户端路由转发到对应的 index.html。
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/console", "/console/",
            "/console/{path:^(?!api|assets).*}/**"
    })
    public String consoleForward() {
        return "forward:/console/index.html";
    }

    @GetMapping(value = {
            "/diagnosis", "/diagnosis/",
            "/diagnosis/{path:^(?!api|assets).*}/**"
    })
    public String diagnosisForward() {
        return "forward:/diagnosis/index.html";
    }
}
