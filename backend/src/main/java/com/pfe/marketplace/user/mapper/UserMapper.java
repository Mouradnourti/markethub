package com.pfe.marketplace.user.mapper;

import com.pfe.marketplace.user.dto.UserDto;
import com.pfe.marketplace.user.entity.User;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    @org.mapstruct.Mapping(target = "twoFactorEnabled", source = "totpEnabled")
    UserDto toDto(User user);
}
