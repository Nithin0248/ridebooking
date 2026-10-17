package com.nithin.ridebooking.dto;

import com.nithin.ridebooking.entity.Role;

public record AuthResponse(String token, Long userId, String name, String email, Role role) {}