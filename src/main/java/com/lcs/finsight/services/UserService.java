package com.lcs.finsight.services;

import com.lcs.finsight.dtos.request.UserRequestDto;
import com.lcs.finsight.dtos.response.UserResponseDto;
import com.lcs.finsight.exceptions.UserExceptions;
import com.lcs.finsight.models.User;
import com.lcs.finsight.repositories.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserExceptions.UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserExceptions.UsernameNotFoundException(email));
    }

    @Transactional
    public User create(UserRequestDto dto) {
        Optional<User> existingUser = userRepository.findByEmail(dto.getEmail());
        if (existingUser.isPresent()) {
            throw new UserExceptions.EmailAlreadyUsedException(dto.getEmail());
        }

        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        String encryptedPassword = encoder.encode(dto.getPassword());
        user.setPassword(encryptedPassword);

        return userRepository.save(user);
    }

    @Transactional
    public User update(User user, UserRequestDto dto) {
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        String encryptedPassword = encoder.encode(dto.getPassword());
        user.setPassword(encryptedPassword);

        return userRepository.save(user);
    }

    public UserResponseDto mapToResponseDTO(User user) {
        return new UserResponseDto(user);
    }

}
