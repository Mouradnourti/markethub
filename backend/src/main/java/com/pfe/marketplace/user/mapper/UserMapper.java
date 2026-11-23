package com.pfe.marketplace.user.mapper;

import com.pfe.marketplace.user.dto.UserDto;
import com.pfe.marketplace.user.entity.User;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    UserDto toDto(User user);
}
