package ir.sharif.pvz.model;

/**
 * Raw arguments of a register command, before validation.
 */
public record RegisterRequest(
        String username,
        String password,
        String passwordConfirm,
        String nickname,
        String email,
        String gender) {

    public RegisterRequest withPassword(String newPassword, String newConfirm) {
        return new RegisterRequest(username, newPassword, newConfirm, nickname, email, gender);
    }
}
