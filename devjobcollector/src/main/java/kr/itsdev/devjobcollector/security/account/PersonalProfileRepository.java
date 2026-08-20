package kr.itsdev.devjobcollector.security.account;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalProfileRepository extends JpaRepository<PersonalProfile, Long> {
    boolean existsByUser(UserAccount user);
}
