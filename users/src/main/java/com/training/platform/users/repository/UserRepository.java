package com.training.platform.users.repository;

import com.training.platform.users.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Override
    @EntityGraph(attributePaths = "profile")
    Page<User> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "profile")
    @Query(value = """
            select u from User u left join u.profile p
            where lower(u.username) like lower(concat('%', :query, '%'))
               or lower(u.email) like lower(concat('%', :query, '%'))
               or lower(p.firstName) like lower(concat('%', :query, '%'))
               or lower(p.middleName) like lower(concat('%', :query, '%'))
               or lower(p.lastName) like lower(concat('%', :query, '%'))
            """,
            countQuery = """
            select count(u) from User u left join u.profile p
            where lower(u.username) like lower(concat('%', :query, '%'))
               or lower(u.email) like lower(concat('%', :query, '%'))
               or lower(p.firstName) like lower(concat('%', :query, '%'))
               or lower(p.middleName) like lower(concat('%', :query, '%'))
               or lower(p.lastName) like lower(concat('%', :query, '%'))
            """)
    Page<User> search(@Param("query") String query, Pageable pageable);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRoleIgnoreCase(String role);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Optional<User> findByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = "profile")
    Optional<User> findWithProfileById(Long id);
}
