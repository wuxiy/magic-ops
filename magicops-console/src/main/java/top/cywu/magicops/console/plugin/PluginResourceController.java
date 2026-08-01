package top.cywu.magicops.console.plugin;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.io.InputStream;

/**
 * magic-editor 插件静态资源控制器。
 *
 * <p>magic-api 的资源处理器将 {@code /magic/web/**} 映射到 {@code classpath:/magic-editor/}
 * （位于 magic-api jar，且为单 classpath 解析）。为避免插件资源与之冲突（在 console 自建
 * {@code /magic-editor/} 目录会遮蔽编辑器自身的 index.html/assets），插件 IIFE bundle 放在
 * 本模块自有目录 {@code classpath:/magicops-editor-plugins/}，由本控制器以正确的 JS MIME 类型提供。
 *
 * <p>控制器映射优先于静态资源处理器，因此 {@code /magic/web/plugins/{file}} 由本控制器处理，
 * 而 {@code /magic/web/plugins}（无文件名，插件列表 JSON）仍由 magic-api 的 Workbench 控制器处理。
 */
@Controller
public class PluginResourceController {

    @GetMapping("/magic/web/plugins/{filename:.+}")
    public ResponseEntity<byte[]> servePlugin(@PathVariable String filename) throws IOException {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        ClassPathResource resource = new ClassPathResource("magicops-editor-plugins/" + filename);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        try (InputStream in = resource.getInputStream()) {
            byte[] bytes = StreamUtils.copyToByteArray(in);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/javascript;charset=UTF-8")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(bytes);
        }
    }
}
