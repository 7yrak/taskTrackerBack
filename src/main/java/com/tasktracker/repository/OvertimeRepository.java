package com.tasktracker.repository;
import com.tasktracker.model.OvertimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OvertimeRepository extends JpaRepository<OvertimeEntry,Long> {
 List<OvertimeEntry> findAllByOrderByDateDescIdDesc();
}
