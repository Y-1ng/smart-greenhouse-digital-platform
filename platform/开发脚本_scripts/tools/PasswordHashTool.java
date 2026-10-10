import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 用于生成演示账号 BCrypt 哈希的小工具。
 */
public final class PasswordHashTool {

    private PasswordHashTool() {
    }

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        for (String password : args) {
            System.out.println(password + "=" + encoder.encode(password));
        }
    }
}
