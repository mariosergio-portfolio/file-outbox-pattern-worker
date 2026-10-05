package com.planet.joboutbox.data.repository;

import com.planet.joboutbox.domain.ExportState;
import com.planet.joboutbox.data.entity.OutboxExport;
import com.planet.joboutbox.domain.OutBoxState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxExportRepository extends JpaRepository<OutboxExport, Long> {

    @Query("""
            SELECT o FROM OutboxExport o
            WHERE o.state = :state
              AND o.createdAt <= :staleBefore
            ORDER BY o.createdAt ASC
            """)
    List<OutboxExport> findStaleByState(
            @Param("state") OutBoxState state,
            @Param("staleBefore") LocalDateTime staleBefore);
}
