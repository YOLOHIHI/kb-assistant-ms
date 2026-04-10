package com.codec.kb.index;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KbJpaRepository extends JpaRepository<KbEntity, String> {
}
