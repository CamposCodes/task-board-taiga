package com.r2da.taskboard.items;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByProjectIdAndRef(Long projectId, int ref);

    List<Item> findByParentIdOrderByListOrderAscIdAsc(Long parentId);
}
