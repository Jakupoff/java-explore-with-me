package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.practicum.dto.NewUserRequest;
import ru.practicum.dto.UserDto;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.UserMapper;
import ru.practicum.model.User;
import ru.practicum.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;


    public List<UserDto> getUsers(List<Long> ids, int from, int size) {
        int page = from / size;
        Pageable pageable = PageRequest.of(page, size);
        List<User> users;
        if (ids == null || ids.isEmpty()) {
            users = userRepository.findAll(pageable).getContent();
        }
        else {
            users = userRepository.findAllByIdIn(ids, pageable);
        }
        return userMapper.toDtoList(users);
    }

    public UserDto createUser(NewUserRequest request) {
        // конвертировать DTO → Entity
        // сохранить
        // вернуть UserDto
        User user = userMapper.toEntity(request);
        User saveUser = userRepository.save(user);
        return userMapper.toDto(saveUser);
    }

    // DELETE /admin/users/{userId}
    public void deleteUser(Long userId) {
        // проверить что пользователь существует
        // удалить
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        userRepository.deleteById(userId);
    }
}
