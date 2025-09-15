package com.example.cloudfour.authservice.client;

import com.example.cloudfour.authservice.domain.auth.dto.UserRequestDTO;
import com.example.cloudfour.authservice.domain.auth.dto.UserResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name="user-service", url="http://user-service.app.svc.cluster.local:80/internal/users")
public interface UserFeignClient {
    @GetMapping("/exists")
    UserResponseDTO.ExistsByEmailResponseDTO existsByEmail(@RequestParam("email") String email);

    @PostMapping
    UserResponseDTO.UserBriefResponseDTO create(@RequestBody UserRequestDTO.CreateUserRequestDTO req);

    @GetMapping("/by-email")
    UserResponseDTO.UserBriefResponseDTO byEmail(@RequestParam("email") String email);

    @GetMapping("/{id}")
    UserResponseDTO.UserBriefResponseDTO byId(@PathVariable("id") UUID id);

    @PostMapping("/{id}/verify-password")
    UserResponseDTO.PasswordVerifyResponseDTO verifyPassword(
            @PathVariable("id") UUID id,
            @RequestBody UserRequestDTO.PasswordVerifyRequestDTO body
    );

    @PostMapping("/{id}/email-verified")
    void markEmailVerified(@PathVariable("id") UUID id);

    @PostMapping("/{id}/change-password")
    void changePassword(
            @PathVariable("id") UUID id,
            @RequestBody UserRequestDTO.ChangePasswordRequestDTO body
    );

    @PostMapping("/{id}/email-change/start")
    void startEmailChange(
            @PathVariable("id") UUID id,
            @RequestBody UserRequestDTO.EmailChangeStartRequestDTO body
    );

    @PostMapping("/{id}/email-change/confirm")
    void confirmEmailChange(
            @PathVariable("id") UUID id,
            @RequestBody UserRequestDTO.EmailChangeConfirmRequestDTO body
    );
}
