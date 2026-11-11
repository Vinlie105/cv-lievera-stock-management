package com.cvlievera.stockmanagement.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    Page<UserDTO.Response> getAllUsers(Pageable pageable);

    UserDTO.Response getUserById(Long id);

    UserDTO.Response getCurrentUser(String username);

    UserDTO.Response createUser(UserDTO.CreateRequest request);

    UserDTO.Response updateUser(Long id, UserDTO.UpdateRequest request);

    void deactivateUser(Long id);

    void activateUser(Long id);

    void unlockUser(Long id);

    void resetPassword(Long id, String temporaryPassword);

    void resetTwoFactor(Long id);
}
