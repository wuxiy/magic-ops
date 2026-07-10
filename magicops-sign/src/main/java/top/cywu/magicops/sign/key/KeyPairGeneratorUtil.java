package top.cywu.magicops.sign.key;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * 密钥对生成工具。用于 E2E 测试生成共享 RSA 密钥对。
 *
 * <p>输出 Base64 编码的 PKCS8 私钥和 X509 公钥，可设置为环境变量：
 * <pre>
 *   export MAGICOPS_PRIVATE_KEY="..."
 *   export MAGICOPS_PUBLIC_KEY="..."
 *   export MAGICOPS_KEY_ID="e2e-test-key"
 * </pre>
 */
public class KeyPairGeneratorUtil {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        String privateKeyB64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKeyB64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        System.out.println("export MAGICOPS_PRIVATE_KEY=\"" + privateKeyB64 + "\"");
        System.out.println("export MAGICOPS_PUBLIC_KEY=\"" + publicKeyB64 + "\"");
        System.out.println("export MAGICOPS_KEY_ID=\"e2e-test-key\"");
    }
}
