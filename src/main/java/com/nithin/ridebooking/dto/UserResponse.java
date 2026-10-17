package com.nithin.ridebooking.dto;

import com.nithin.ridebooking.entity.Role;

public record UserResponse(Long id, String name, String email, String phone, Role role) {}