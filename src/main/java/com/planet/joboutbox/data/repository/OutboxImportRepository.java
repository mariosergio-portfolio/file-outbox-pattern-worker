package com.planet.joboutbox.data.repository;

import com.planet.joboutbox.domain.ImportState;
import com.planet.joboutbox.data.entity.OutboxImport;
import com.planet.joboutbox.domain.OutBoxState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxImportRepository extends JpaRepository<OutboxImport, Long> {

    @Query("""
            SELECT o FROM OutboxImport o
            WHERE o.state = :state
              AND o.createdAt <= :staleBefore
            ORDER BY o.createdAt ASC
            """)
    List<OutboxImport> findStaleByState(
            @Param("state") OutBoxState state,
            @Param("staleBefore") LocalDateTime staleBefore);
}
