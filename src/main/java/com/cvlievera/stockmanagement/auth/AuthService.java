package com.cvlievera.stockmanagement.auth;

import com.cvlievera.stockmanagement.user.User;

public interface AuthService {

    AuthDTO.AuthResult login(AuthDTO.LoginRequest request, String ip);

    AuthDTO.AuthResult verifyMfa(AuthDTO.MfaVerifyRequest request, String ip);

    AuthDTO.AuthResult refresh(String rawRefreshToken, String ip);

    void logout(String rawRefreshToken);

    AuthDTO.SessionResponse currentSession(User user);

    AuthDTO.AuthResult changePassword(User user, AuthDTO.ChangePasswordRequest request, String ip);

    AuthDTO.TwoFaSetupResponse beginTwoFactorSetup(User user);

    AuthDTO.EnableResult enableTwoFactor(User user, String code, String ip);
}
