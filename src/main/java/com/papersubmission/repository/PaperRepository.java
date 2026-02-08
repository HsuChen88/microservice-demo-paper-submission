package com.papersubmission.repository;

import com.papersubmission.model.Paper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaperRepository extends JpaRepository<Paper, UUID> {
}
