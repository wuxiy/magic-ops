package top.cywu.magicops.sign.canonical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Canonical JSON 工具。签名输入必须形成稳定字节序列。
 *
 * <p>规范化规则（来自 {@code docs/architecture/release-and-runtime.md}）：
 * <ul>
 *   <li>JSON 使用 UTF-8 编码</li>
 *   <li>JSON 字段按字典序排序</li>
 *   <li>JSON 不包含无意义空白</li>
 * </ul>
 */
public final class CanonicalJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.INDENT_OUTPUT, false);

    private CanonicalJson() {
    }

    /**
     * 将 JSON 对象转为 canonical 字节（UTF-8、字段字典序、无空白）。
     */
    public static byte[] toCanonicalBytes(Map<String, Object> json) {
        try {
            String compact = MAPPER.writeValueAsString(sortMap(json));
            return compact.getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法序列化 JSON", e);
        }
    }

    /**
     * 将 JSON 字符串解析后转为 canonical 字节。
     */
    public static byte[] toCanonicalBytes(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            String compact = MAPPER.writeValueAsString(sortNode(node));
            return compact.getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法解析 JSON: " + json, e);
        }
    }

    /**
     * 规范化脚本内容：统一 LF 换行。
     */
    public static byte[] normalizeScript(String content) {
        if (content == null) {
            return new byte[0];
        }
        return content.replace("\r\n", "\n").replace("\r", "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 递归排序 Map 的 key。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> sortMap(Map<String, Object> map) {
        var sorted = new java.util.TreeMap<String, Object>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> innerMap) {
                sorted.put(entry.getKey(), sortMap((Map<String, Object>) innerMap));
            } else if (value instanceof List<?> list) {
                sorted.put(entry.getKey(), sortList(list));
            } else {
                sorted.put(entry.getKey(), value);
            }
        }
        return sorted;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> sortList(List<?> list) {
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> innerMap) {
                result.add(sortMap((Map<String, Object>) innerMap));
            } else if (item instanceof List<?> innerList) {
                result.add(sortList(innerList));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private static JsonNode sortNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = MAPPER.createObjectNode();
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);
            keys.sort(String::compareTo);
            for (String key : keys) {
                sorted.set(key, sortNode(node.get(key)));
            }
            return sorted;
        }
        return node;
    }
}
