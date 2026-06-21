package com.conrad.shortlink.controller;

import com.conrad.shortlink.service.ShortLinkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;

/**
 * 短链接重定向 Controller
 *
 * 教学点：
 * 1. 单独 Controller 而非在 ShortLinkController：关注点分离
 * 2. 302 vs 301 重定向：
 *    - 301（永久）：浏览器缓存，下次直接走缓存，统计不到
 *    - 302（临时）：每次都问服务器，能统计点击数 ← 我们用这个
 * 3. 用 ResponseEntity.status(302).location(URI.create(url)) 实现重定向
 * 4. 异步思想：点击数自增可以异步，这里保持同步简单实现
 *
 * 对应学习模块：notes/java/08-spring
 */
@RestController
public class RedirectController {

    private final ShortLinkService service;

    public RedirectController(ShortLinkService service) {
        this.service = service;
    }

    /**
     * 短链访问入口
     * GET /{shortCode} → 302 重定向到长链接
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode,
                                          HttpServletRequest request) {
        // 排除 API 路径（避免和 /api/v1/shortlinks/{code} 冲突）
        // 这里因为 RedirectController 和 ShortLinkController 都有 /{shortCode}，
        // Spring 会按更具体的路径优先匹配，所以 API 路径会先命中 ShortLinkController
        // 但 demo01/demo02/demo03 这种只有一层路径的还是到这里
        if (shortCode.startsWith("api")) {
            return ResponseEntity.notFound().build();
        }

        String longUrl = service.resolve(shortCode);

        // 异步记录点击（这里简化为同步）
        try {
            service.recordAccess(shortCode);
        } catch (Exception ignored) {
            // 统计失败不影响重定向
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(longUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);  // 302
    }
}
