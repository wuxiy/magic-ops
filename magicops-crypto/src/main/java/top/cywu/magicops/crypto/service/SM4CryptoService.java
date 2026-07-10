package top.cywu.magicops.crypto.service;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;

/**
 * SM4 国密算法加解密服务。使用 BouncyCastle 提供者。
 *
 * <p>SM4 是中国国家标准的分组密码算法，块大小 128 位，密钥长度 128 位。
 * <p>用于 HTTP 接口适配场景中的请求/响应加解密。
 */
public class SM4CryptoService {

    private static final String ALGORITHM = "SM4";
    private static final String TRANSFORMATION = "SM4/CBC/PKCS7Padding";
    private static final int IV_LENGTH = 16;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * SM4-CBC 加密。
     *
     * @param key  16 字节密钥
     * @param iv   16 字节初始化向量
     * @param data 明文
     * @return Base64 编码的密文
     */
    public String encrypt(byte[] key, byte[] iv, String data) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * SM4-CBC 解密。
     *
     * @param key        16 字节密钥
     * @param iv         16 字节初始化向量
     * @param ciphertext Base64 编码的密文
     * @return 明文
     */
    public String decrypt(byte[] key, byte[] iv, String ciphertext) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 解密失败: " + e.getMessage(), e);
        }
    }
}
