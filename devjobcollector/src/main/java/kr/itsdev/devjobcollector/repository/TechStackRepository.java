package kr.itsdev.devjobcollector.repository;

import kr.itsdev.devjobcollector.domain.TechStack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TechStackRepository extends JpaRepository<TechStack, Integer> {
    
    Optional<TechStack> findByStackName(String StackName);
    
    boolean existsByStackName(String StackName);
}