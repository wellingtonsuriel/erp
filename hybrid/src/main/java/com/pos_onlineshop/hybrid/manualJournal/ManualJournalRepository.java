package com.pos_onlineshop.hybrid.manualJournal;

import com.pos_onlineshop.hybrid.enums.ManualJournalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ManualJournalRepository extends JpaRepository<ManualJournal, Long> {

    List<ManualJournal> findByStatus(ManualJournalStatus status);

    List<ManualJournal> findAllByOrderByIdDesc();
}
